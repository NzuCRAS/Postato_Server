package com.potato.archnode;

import com.potato.archnode.ArchNodeDtos.CreateNodeRequest;
import com.potato.archnode.ArchNodeDtos.SyncModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchNodeServiceTest {

    @Mock
    ArchNodeRepository repo;

    ArchNodeService service;

    @BeforeEach
    void setUp() {
        service = new ArchNodeService(repo);
    }

    private ArchNode node(String id, String path, String layer, List<String> tags) {
        ArchNode n = new ArchNode();
        n.setId(id);
        n.setProjectId("p");
        n.setPath(path);
        n.setLayer(layer);
        n.setStatus("active");
        n.setTitle(path.substring(path.lastIndexOf('/') + 1));
        if (tags != null) n.setTags(tags);
        return n;
    }

    private ArchNode statusNode(String id, String path, String impl) {
        ArchNode n = node(id, path, "L1", null);
        n.setImplStatus(impl);
        return n;
    }

    @Test
    void create_computes_path_under_parent() {
        when(repo.findById("a")).thenReturn(Optional.of(node("a", "/用户域", "L1", null)));
        when(repo.findByProjectIdAndPath("p", "/用户域/认证上下文")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        CreateNodeRequest in = new CreateNodeRequest("a", "认证上下文", "L2", "context", null, null, null, null, null);
        ArchNode created = service.create("p", in, "manual", null);
        assertThat(created.getPath()).isEqualTo("/用户域/认证上下文");
        assertThat(created.getParentId()).isEqualTo("a");
        assertThat(created.getSource()).isEqualTo("manual");
    }

    @Test
    void create_rejects_duplicate_path() {
        when(repo.findById("a")).thenReturn(Optional.of(node("a", "/用户域", "L1", null)));
        when(repo.findByProjectIdAndPath("p", "/用户域/x")).thenReturn(Optional.of(node("z", "/用户域/x", "L2", null)));
        CreateNodeRequest in = new CreateNodeRequest("a", "x", "L2", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create("p", in, "manual", null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("已存在");
    }

    @Test
    void list_by_tag_includes_ancestors() {
        when(repo.findByProjectIdOrderByPathAsc("p")).thenReturn(List.of(
                node("1", "/用户域", "L1", List.of()),
                node("2", "/用户域/认证上下文", "L2", List.of()),
                node("3", "/用户域/认证上下文/JWT", "L3", List.of("安全")),
                node("4", "/订单域", "L1", List.of())
        ));
        List<ArchNode> r = service.list("p", "安全", null);
        assertThat(r).extracting(ArchNode::getPath)
                .containsExactlyInAnyOrder("/用户域", "/用户域/认证上下文", "/用户域/认证上下文/JWT");
    }

    @Test
    void archive_archives_subtree() {
        ArchNode n = node("2", "/用户域/认证上下文", "L2", null);
        when(repo.findById("2")).thenReturn(Optional.of(n));
        when(repo.findByProjectIdAndPathStartingWith("p", "/用户域/认证上下文/"))
                .thenReturn(List.of(node("3", "/用户域/认证上下文/JWT", "L3", null)));
        when(repo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        int cnt = service.archive("2");
        assertThat(cnt).isEqualTo(2);
        assertThat(n.getStatus()).isEqualTo("archived");
    }

    @Test
    void move_recomputes_subtree_paths() {
        ArchNode moving = node("2", "/用户域/认证上下文", "L2", null);
        ArchNode child = node("3", "/用户域/认证上下文/JWT", "L3", null);
        ArchNode newParent = node("5", "/安全域", "L1", null);
        when(repo.findById("2")).thenReturn(Optional.of(moving));
        when(repo.findById("5")).thenReturn(Optional.of(newParent));
        when(repo.findByProjectIdAndPath("p", "/安全域/认证上下文")).thenReturn(Optional.empty());
        when(repo.findByProjectIdAndPathStartingWith("p", "/用户域/认证上下文/")).thenReturn(List.of(child));
        when(repo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        ArchNode r = service.move("2", "5");
        assertThat(r.getPath()).isEqualTo("/安全域/认证上下文");
        assertThat(child.getPath()).isEqualTo("/安全域/认证上下文/JWT");
    }

    @Test
    void sync_upserts_under_parent_and_archives_disappeared() {
        ArchNode parent = node("ctx", "/用户域/认证上下文", "L2", null);
        ArchNode stale = node("old", "/用户域/认证上下文/Old", "L3", null);
        stale.setSource("sync");
        stale.setRepoId("repo_1");
        stale.setCreatedAt(Instant.now());
        when(repo.findByProjectIdAndRepoId("p", "repo_1")).thenReturn(List.of(stale));
        when(repo.findByProjectIdAndPath("p", "/用户域/认证上下文")).thenReturn(Optional.of(parent));
        when(repo.findByProjectIdAndPath("p", "/用户域/认证上下文/JWT")).thenReturn(Optional.empty());
        when(repo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        SyncModule m = new SyncModule("/用户域/认证上下文", null, "JWT", "component",
                List.of("安全"), null, List.of("security-lib/**"));
        var res = service.sync("p", "repo_1", List.of(m));
        assertThat(res.get("upserted")).isEqualTo(1);
        assertThat(res.get("archived")).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo("archived");
    }

    @Test
    void upsertTree_creates_nested_tree_with_auto_layer() {
        List<ArchNode> saved = new ArrayList<>();
        when(repo.findByProjectIdAndPath(any(), any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> {
            ArchNode a = i.getArgument(0);
            if (a.getId() == null) a.setId("gen" + a.getPath());
            saved.add(a);
            return a;
        });
        ArchNodeDtos.TreeNode child = new ArchNodeDtos.TreeNode("用户域", null, "domain", null, null, null, null, null, null);
        ArchNodeDtos.TreeNode root = new ArchNodeDtos.TreeNode("Potato 平台", null, "system", null, null, null, null, null, List.of(child));
        var res = service.upsertTree("p", null, List.of(root));
        assertThat(res.get("created")).isEqualTo(2);
        assertThat(res.get("updated")).isEqualTo(0);
        // 根自动 L0、子自动 L1(父+1);子的 parentId = 父保存后的 id(逐节点即时保存)
        assertThat(saved).extracting(ArchNode::getPath, ArchNode::getLayer)
                .containsExactly(tuple("/Potato 平台", "L0"), tuple("/Potato 平台/用户域", "L1"));
        assertThat(saved.get(1).getParentId()).isEqualTo("gen/Potato 平台");
    }

    @Test
    void upsertTree_idempotent_updates_existing_and_keeps_source() {
        ArchNode existingRoot = node("r", "/Potato 平台", "L0", null);
        existingRoot.setSource("manual");
        existingRoot.setCreatedAt(Instant.now());
        when(repo.findByProjectIdAndPath("p", "/Potato 平台")).thenReturn(Optional.of(existingRoot));
        when(repo.findByProjectIdAndPath("p", "/Potato 平台/需求域")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> {
            ArchNode a = i.getArgument(0);
            if (a.getId() == null) a.setId("gen" + a.getPath());
            return a;
        });
        ArchNodeDtos.TreeNode child = new ArchNodeDtos.TreeNode("需求域", null, "domain", null, null, null, null, null, null);
        ArchNodeDtos.TreeNode root = new ArchNodeDtos.TreeNode("Potato 平台", null, "system", null, null, null, null, null, List.of(child));
        var res = service.upsertTree("p", null, List.of(root));
        assertThat(res.get("created")).isEqualTo(1); // 仅子节点新建
        assertThat(res.get("updated")).isEqualTo(1); // 根更新
        assertThat(existingRoot.getStatus()).isEqualTo("active");
        assertThat(existingRoot.getSource()).isEqualTo("manual"); // 保留原 source
    }

    @Test
    void upsertTree_aggregates_parent_from_children() {
        ArchNode sys = node("sys", "/sys", "L0", null);
        sys.setImplStatus("planned");
        sys.setCreatedAt(Instant.now());
        when(repo.findByProjectIdAndPath("p", "/sys")).thenReturn(Optional.of(sys));
        when(repo.findByProjectIdAndPath("p", "/sys/A")).thenReturn(Optional.empty());
        when(repo.findByProjectIdAndPath("p", "/sys/B")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> {
            ArchNode a = i.getArgument(0);
            if (a.getId() == null) a.setId("gen" + a.getPath());
            return a;
        });
        when(repo.findByProjectIdAndParentId("p", "sys"))
                .thenReturn(List.of(statusNode("a", "/sys/A", "done"), statusNode("b", "/sys/B", "planned")));

        ArchNodeDtos.TreeNode a = new ArchNodeDtos.TreeNode("A", "L1", "domain", null, null, null, null, "done", null);
        ArchNodeDtos.TreeNode b = new ArchNodeDtos.TreeNode("B", "L1", "domain", null, null, null, null, "planned", null);
        service.upsertTree("p", "/sys", List.of(a, b));

        assertThat(sys.getImplStatus()).isEqualTo("in_progress"); // done + planned 混合 → in_progress
    }

    @Test
    void impl_status_propagates_chained_to_root() {
        ArchNode x = node("x", "/x", "L0", null);
        x.setImplStatus("planned");
        x.setCreatedAt(Instant.now());
        ArchNode y = node("y", "/x/y", "L1", null);
        y.setImplStatus("planned");
        y.setParentId("x");
        y.setCreatedAt(Instant.now());
        when(repo.findByProjectIdAndPath("p", "/x/y")).thenReturn(Optional.of(y));
        when(repo.findByProjectIdAndPath("p", "/x/y/z")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> {
            ArchNode n = i.getArgument(0);
            if (n.getId() == null) n.setId("gen" + n.getPath());
            return n;
        });
        when(repo.findById("x")).thenReturn(Optional.of(x));
        when(repo.findByProjectIdAndParentId("p", "y")).thenReturn(List.of(statusNode("z", "/x/y/z", "done")));
        when(repo.findByProjectIdAndParentId("p", "x")).thenReturn(List.of(y));

        ArchNodeDtos.TreeNode z = new ArchNodeDtos.TreeNode("z", "L2", "context", null, null, null, null, "done", null);
        service.upsertTree("p", "/x/y", List.of(z));

        assertThat(y.getImplStatus()).isEqualTo("done");   // 子 z done → y done
        assertThat(x.getImplStatus()).isEqualTo("done");   // 链式:y done → x done
    }

    @Test
    void relateAndMark_leaf_sets_impl_and_aggregates_parent() {
        ArchNode parent = node("p1", "/ctx", "L1", null);
        parent.setImplStatus("planned");                 // parentId null → 顶层
        ArchNode leaf = node("leaf", "/ctx/mod", "L2", null);
        leaf.setImplStatus("planned");
        leaf.setParentId("p1");
        when(repo.findByProjectIdAndPath("p", "/ctx/mod")).thenReturn(Optional.of(leaf));
        when(repo.findByProjectIdAndParentId("p", "leaf")).thenReturn(List.of());     // 叶子无子
        when(repo.findById("p1")).thenReturn(Optional.of(parent));
        when(repo.findByProjectIdAndParentId("p", "p1")).thenReturn(List.of(leaf));   // 父的子=leaf
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ArchNodeService.RelateResult r = service.relateAndMark("p", "/ctx/mod", "done", "req1");
        assertThat(r.linked()).isTrue();
        assertThat(r.warnings()).isEmpty();
        assertThat(leaf.getImplStatus()).isEqualTo("done");
        assertThat(leaf.getRelatedRequirements()).containsExactly("req1");
        assertThat(parent.getImplStatus()).isEqualTo("done");   // 叶子 done → 父聚合 done
    }

    @Test
    void relateAndMark_nonleaf_warns_and_keeps_status() {
        ArchNode n = node("n", "/ctx", "L1", null);
        n.setImplStatus("planned");
        when(repo.findByProjectIdAndPath("p", "/ctx")).thenReturn(Optional.of(n));
        when(repo.findByProjectIdAndParentId("p", "n")).thenReturn(List.of(node("c", "/ctx/sub", "L2", null)));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ArchNodeService.RelateResult r = service.relateAndMark("p", "/ctx", "done", "req1");
        assertThat(r.linked()).isTrue();
        assertThat(r.warnings()).anyMatch(w -> w.contains("子聚合"));
        assertThat(n.getImplStatus()).isEqualTo("planned");        // 非叶子未被直接设
        assertThat(n.getRelatedRequirements()).contains("req1");   // 但关联已建立
    }

    @Test
    void relateAndMark_dedups_requirement_link() {
        ArchNode n = node("n", "/ctx", "L1", null);
        n.setRelatedRequirements(new ArrayList<>(List.of("req1")));
        when(repo.findByProjectIdAndPath("p", "/ctx")).thenReturn(Optional.of(n));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ArchNodeService.RelateResult r = service.relateAndMark("p", "/ctx", null, "req1"); // 仅关联,已存在
        assertThat(r.linked()).isTrue();
        assertThat(n.getRelatedRequirements()).containsExactly("req1");   // 去重不重复
    }

    @Test
    void relateAndMark_missing_node_warns_not_linked() {
        when(repo.findByProjectIdAndPath("p", "/nope")).thenReturn(Optional.empty());
        ArchNodeService.RelateResult r = service.relateAndMark("p", "/nope", "done", "req1");
        assertThat(r.linked()).isFalse();
        assertThat(r.warnings()).anyMatch(w -> w.contains("不存在"));
    }

    @Test
    void relateAndMark_illegal_impl_status_rejected() {
        assertThatThrownBy(() -> service.relateAndMark("p", "/ctx", "magic", "req1"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("impl_status");
    }
}
