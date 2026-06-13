package com.potato.requirement;

import com.potato.archnode.ArchNodeService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementServiceTest {

    @Mock
    RequirementRepository repo;
    @Mock
    ArchNodeService archNodeService;

    RequirementService service;

    @BeforeEach
    void setUp() {
        service = new RequirementService(repo, archNodeService);
    }

    /** 建一条 projectId=default 的需求,并 stub findById/save。 */
    private Requirement req() {
        Requirement r = new Requirement();
        r.setId("r1");
        r.setProjectId("default");
        when(repo.findById("r1")).thenReturn(Optional.of(r));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        return r;
    }

    @Test
    void relateArch_adds_path_when_linked() {
        req();
        when(archNodeService.relateAndMark(eq("default"), eq("/sys/mod"), eq("done"), eq("r1")))
                .thenReturn(new ArchNodeService.RelateResult(true, List.of()));
        RelateArchResponse resp = service.relateArch("r1", List.of(new ArchLink("/sys/mod", "done")));
        assertThat(resp.relatedArchNodes()).containsExactly("/sys/mod");
        assertThat(resp.warnings()).isEmpty();
    }

    @Test
    void relateArch_skips_path_when_not_linked_and_surfaces_warning() {
        req();
        when(archNodeService.relateAndMark(eq("default"), eq("/nope"), any(), eq("r1")))
                .thenReturn(new ArchNodeService.RelateResult(false, List.of("结构树节点不存在,跳过: /nope")));
        RelateArchResponse resp = service.relateArch("r1", List.of(new ArchLink("/nope", "done")));
        assertThat(resp.relatedArchNodes()).isEmpty();                 // 节点不存在 → 需求侧不加 path
        assertThat(resp.warnings()).anyMatch(w -> w.contains("不存在"));
    }

    @Test
    void relateArch_dedups_existing_path() {
        Requirement r = req();
        r.getRelatedArchNodes().add("/sys/mod");                       // 已关联
        when(archNodeService.relateAndMark(eq("default"), eq("/sys/mod"), any(), eq("r1")))
                .thenReturn(new ArchNodeService.RelateResult(true, List.of()));
        RelateArchResponse resp = service.relateArch("r1", List.of(new ArchLink("/sys/mod", null)));
        assertThat(resp.relatedArchNodes()).containsExactly("/sys/mod"); // 不重复
    }

    // ---- 需求分级 type/tier ----

    @Test
    void create_sets_type_and_tier() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        Requirement r = service.create("T", null, null, "draft", "default", null, "bugfix", "Small", "u");
        assertThat(r.getType()).isEqualTo("bugfix");
        assertThat(r.getTier()).isEqualTo("Small");
    }

    @Test
    void create_defaults_tier_to_medium_and_allows_null_type() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        Requirement r = service.create("T", null, null, "draft", "default", null, null, null, "u");
        assertThat(r.getType()).isNull();
        assertThat(r.getTier()).isEqualTo("Medium");
    }

    @Test
    void create_rejects_invalid_tier() {
        assertThatThrownBy(() -> service.create("T", null, null, "draft", "default", null, null, "Huge", "u"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("tier");
    }
}
