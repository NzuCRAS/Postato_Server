package com.potato.devplan;

import com.potato.devplan.DevPlanDtos.NodeInput;
import com.potato.devplan.DevPlanDtos.UpdateNodeRequest;
import com.potato.devplan.DevPlanDtos.UpdateResult;
import com.potato.requirement.Requirement;
import com.potato.requirement.RequirementRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevPlanServiceTest {

    @Mock
    RequirementRepository repo;

    DevPlanService service;

    @BeforeEach
    void setUp() {
        service = new DevPlanService(repo);
    }

    private Requirement reqWithPlan() {
        Requirement req = new Requirement();
        req.setId("r1");
        req.setTitle("需求一");
        when(repo.findById("r1")).thenReturn(Optional.of(req));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        // 建一棵:root → node_1(带一个验收点)
        NodeInput n1 = new NodeInput("表单", "desc", "表单模块",
                List.of("必填校验"), List.of("/wiki/form"), List.of());
        service.create("r1", "需求一", null, List.of(n1));
        return req;
    }

    @Test
    void blocked_without_reason_is_rejected() {
        reqWithPlan();
        UpdateNodeRequest in = new UpdateNodeRequest("blocked", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateNode("r1", "node_1", in, "human"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked_reason");
    }

    @Test
    void done_without_artifacts_or_unchecked_criteria_warns() {
        reqWithPlan();
        UpdateNodeRequest in = new UpdateNodeRequest("done", null, "完成了", null, null, null, null);
        UpdateResult r = service.updateNode("r1", "node_1", in, "ai");
        assertThat(r.warnings()).anyMatch(w -> w.contains("commit") || w.contains("产物"));
        assertThat(r.warnings()).anyMatch(w -> w.contains("验收"));
    }

    @Test
    void status_change_appends_log_with_from_to_and_actor() {
        reqWithPlan();
        UpdateNodeRequest in = new UpdateNodeRequest("in_progress", null, "开工", "用antd", null, null, null);
        UpdateResult r = service.updateNode("r1", "node_1", in, "ai");
        DevPlan.LogEntry last = r.node().getLog().get(r.node().getLog().size() - 1);
        assertThat(last.getAction()).isEqualTo("status_change");
        assertThat(last.getFrom()).isEqualTo("todo");
        assertThat(last.getTo()).isEqualTo("in_progress");
        assertThat(last.getActor()).isEqualTo("ai");
        assertThat(last.getDetail()).isEqualTo("用antd");
    }

    @Test
    void commit_is_attached_to_log_entry() {
        reqWithPlan();
        DevPlan.Commit commit = new DevPlan.Commit();
        commit.setSha("abc123");
        commit.setMessage("feat: x");
        UpdateNodeRequest in = new UpdateNodeRequest("done", null, "完成", null, null, commit, null);
        UpdateResult r = service.updateNode("r1", "node_1", in, "ai");
        DevPlan.LogEntry last = r.node().getLog().get(r.node().getLog().size() - 1);
        assertThat(last.getCommit()).isNotNull();
        assertThat(last.getCommit().getSha()).isEqualTo("abc123");
        // 有 commit 后,"无产物"警告应消失
        assertThat(r.warnings()).noneMatch(w -> w.contains("产物"));
    }

    @Test
    void add_then_resolve_correction() {
        reqWithPlan();
        DevPlan.Correction c = service.addCorrection("r1", "node_1", "alice", "加手机号校验");
        assertThat(c.isResolved()).isFalse();
        DevPlan.Correction resolved = service.resolveCorrection("r1", "node_1", c.getId());
        assertThat(resolved.isResolved()).isTrue();
    }

    @Test
    void add_nodes_continues_id_numbering_under_parent() {
        reqWithPlan(); // root + node_1
        DevPlan.Node parent = service.addNodes("r1", "node_1", List.of(
                new NodeInput("子任务A", null, null, null, null, null),
                new NodeInput("子任务B", null, null, null, null, null)));
        assertThat(parent.getChildren()).hasSize(2);
        assertThat(parent.getChildren().get(0).getId()).isEqualTo("node_2");
        assertThat(parent.getChildren().get(1).getId()).isEqualTo("node_3");
        assertThat(parent.getChildren().get(0).getStatus()).isEqualTo("todo");
    }

    @Test
    void reset_archives_plan_without_losing_logs() {
        Requirement req = reqWithPlan();
        service.resetPlan("r1", "推倒重来");
        assertThat(req.getDevPlan()).isNull();
        assertThat(req.getArchivedDevPlans()).hasSize(1);
        DevPlan archived = req.getArchivedDevPlans().get(0);
        assertThat(archived.getArchivedAt()).isNotNull();
        assertThat(archived.getArchiveReason()).isEqualTo("推倒重来");
        // 日志仍在(根节点的"初始分解"日志没丢)
        assertThat(archived.getRoot().getLog()).isNotEmpty();
    }

    @Test
    void checking_acceptance_appends_log_entry() {
        reqWithPlan(); // node_1 带验收点「必填校验」(未勾)
        DevPlan.AcceptanceItem item = new DevPlan.AcceptanceItem();
        item.setText("必填校验");
        item.setChecked(true);
        UpdateNodeRequest in = new UpdateNodeRequest(null, null, null, null, null, null, List.of(item));
        UpdateResult r = service.updateNode("r1", "node_1", in, "human");
        DevPlan.LogEntry last = r.node().getLog().get(r.node().getLog().size() - 1);
        assertThat(last.getAction()).isEqualTo("acceptance");
        assertThat(last.getSummary()).contains("勾选").contains("必填校验");
        assertThat(last.getActor()).isEqualTo("human");
    }

    @Test
    void set_repo_updates_plan_repo() {
        reqWithPlan();
        DevPlan.Repo repo = new DevPlan.Repo();
        repo.setUrl("https://github.com/x/y");
        repo.setDefaultBranch("main");
        DevPlan.Repo out = service.setRepo("r1", repo);
        assertThat(out.getUrl()).isEqualTo("https://github.com/x/y");
        assertThat(out.getDefaultBranch()).isEqualTo("main");
    }

    @Test
    void in_progress_does_not_revert_done_node() {
        reqWithPlan();
        service.updateNode("r1", "node_1",
                new UpdateNodeRequest("done", null, null, null, null, null, null), "ai");
        UpdateResult r = service.updateNode("r1", "node_1",
                new UpdateNodeRequest("in_progress", null, null, null, null, null, null), "ai");
        assertThat(r.node().getStatus()).isEqualTo("done"); // 未回退
        assertThat(r.warnings()).anyMatch(w -> w.contains("未回退"));
    }
}
