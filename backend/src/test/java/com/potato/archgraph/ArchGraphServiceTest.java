package com.potato.archgraph;

import com.potato.archnode.ArchNode;
import com.potato.archnode.ArchNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchGraphServiceTest {

    @Mock
    ArchModuleRepository moduleRepo;
    @Mock
    ArchEdgeRepository edgeRepo;
    @Mock
    ArchNodeRepository legacyRepo;

    ArchGraphService service;

    @BeforeEach
    void setUp() {
        service = new ArchGraphService(moduleRepo, edgeRepo, legacyRepo);
    }

    private ArchModule module(String key) {
        ArchModule m = new ArchModule();
        m.setId("id-" + key);
        m.setProjectId("default");
        m.setKey(key);
        return m;
    }

    @Test
    void upsertModule_creates_new() {
        when(moduleRepo.findByProjectIdAndKey("default", "permission")).thenReturn(Optional.empty());
        when(moduleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArchModule m = service.upsertModule("default", "permission", "权限", "desc", "用户与权限", "done", List.of("backend/permission/**"), 1);
        assertThat(m.getKey()).isEqualTo("permission");
        assertThat(m.getImplStatus()).isEqualTo("done");
        assertThat(m.getGroup()).isEqualTo("用户与权限");
    }

    @Test
    void upsertModule_rejects_blank_key() {
        assertThatThrownBy(() -> service.upsertModule("default", "", "t", null, null, null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("key 必填");
    }

    @Test
    void upsertModule_rejects_bad_impl_status() {
        assertThatThrownBy(() -> service.upsertModule("default", "x", "t", null, null, "bogus", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("impl_status");
    }

    @Test
    void upsertEdge_rejects_when_endpoint_module_missing() {
        when(moduleRepo.existsByProjectIdAndKey("default", "a")).thenReturn(false);
        assertThatThrownBy(() -> service.upsertEdge("default", "a", "b", "calls", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("源模块未注册");
    }

    @Test
    void upsertEdge_creates_when_both_exist() {
        when(moduleRepo.existsByProjectIdAndKey("default", "a")).thenReturn(true);
        when(moduleRepo.existsByProjectIdAndKey("default", "b")).thenReturn(true);
        when(edgeRepo.findByProjectIdAndFromAndToAndKind("default", "a", "b", "calls")).thenReturn(Optional.empty());
        when(edgeRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArchEdge e = service.upsertEdge("default", "a", "b", "calls", "RPC");
        assertThat(e.getFrom()).isEqualTo("a");
        assertThat(e.getTo()).isEqualTo("b");
        assertThat(e.getKind()).isEqualTo("calls");
        assertThat(e.getLabel()).isEqualTo("RPC");
    }

    @Test
    void deleteModule_also_clears_related_edges() {
        when(moduleRepo.findByProjectIdAndKey("default", "a")).thenReturn(Optional.of(module("a")));
        ArchEdge out = new ArchEdge();
        out.setFrom("a");
        out.setTo("b");
        ArchEdge in = new ArchEdge();
        in.setFrom("c");
        in.setTo("a");
        when(edgeRepo.findByProjectIdAndFrom("default", "a")).thenReturn(List.of(out));
        when(edgeRepo.findByProjectIdAndTo("default", "a")).thenReturn(List.of(in));
        service.deleteModule("default", "a");
        verify(edgeRepo).deleteAll(any());
        verify(moduleRepo).delete(any());
    }

    @Test
    void relateDoc_attaches_to_every_module_in_scope_with_scope_tag() {
        ArchModule a = module("gateway");
        ArchModule b = module("order");
        when(moduleRepo.findByProjectIdAndKey("default", "gateway")).thenReturn(Optional.of(a));
        when(moduleRepo.findByProjectIdAndKey("default", "order")).thenReturn(Optional.of(b));
        when(moduleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.relateDoc("default", "requirement", "req-123", "网关→订单鉴权", List.of("gateway", "order"));
        // 跨模块文档:两端都挂,且各自记录 scope=[gateway, order]
        assertThat(a.getDocs()).hasSize(1);
        assertThat(a.getDocs().get(0).getRef()).isEqualTo("req-123");
        assertThat(a.getDocs().get(0).getScope()).containsExactly("gateway", "order");
        assertThat(b.getDocs()).hasSize(1);
        assertThat(b.getDocs().get(0).getScope()).containsExactly("gateway", "order");
    }

    @Test
    void relateDoc_dedupes_by_type_and_ref() {
        ArchModule a = module("gateway");
        when(moduleRepo.findByProjectIdAndKey("default", "gateway")).thenReturn(Optional.of(a));
        when(moduleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.relateDoc("default", "requirement", "req-1", "t1", List.of("gateway"));
        service.relateDoc("default", "requirement", "req-1", "t2", List.of("gateway")); // 同 type+ref → 更新不新增
        assertThat(a.getDocs()).hasSize(1);
        assertThat(a.getDocs().get(0).getTitle()).isEqualTo("t2");
    }

    @Test
    void relateDoc_rejects_bad_type_and_empty_scope() {
        assertThatThrownBy(() -> service.relateDoc("default", "bogus", "r", null, List.of("a")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("文档类型");
        assertThatThrownBy(() -> service.relateDoc("default", "requirement", "r", null, List.of()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("scope");
    }

    @Test
    void archiveLegacyTree_marks_active_nodes_archived() {
        ArchNode n1 = new ArchNode();
        n1.setStatus("active");
        ArchNode n2 = new ArchNode();
        n2.setStatus("archived"); // 已归档不重复计
        when(legacyRepo.findByProjectIdOrderByPathAsc("default")).thenReturn(List.of(n1, n2));
        when(legacyRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        int n = service.archiveLegacyTree("default");
        assertThat(n).isEqualTo(1);
        assertThat(n1.getStatus()).isEqualTo("archived");
    }
}
