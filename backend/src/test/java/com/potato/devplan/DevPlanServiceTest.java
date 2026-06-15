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
        UpdateNodeRequest in = new UpdateNodeRequest("blocked", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateNode("r1", "node_1", in, "human"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked_reason");
    }

    @Test
    void done_without_artifacts_or_unchecked_criteria_warns() {
        reqWithPlan();
        UpdateNodeRequest in = new UpdateNodeRequest("done", null, "完成了", null, null, null, null, null);
        UpdateResult r = service.updateNode("r1", "node_1", in, "ai");
        assertThat(r.warnings()).anyMatch(w -> w.contains("commit") || w.contains("产物"));
        assertThat(r.warnings()).anyMatch(w -> w.contains("验收"));
    }

    @Test
    void status_change_appends_log_with_from_to_and_actor() {
        reqWithPlan();
        UpdateNodeRequest in = new UpdateNodeRequest("in_progress", null, "开工", "用antd", null, null, null, null);
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
        UpdateNodeRequest in = new UpdateNodeRequest("done", null, "完成", null, null, commit, null, null);
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
        UpdateNodeRequest in = new UpdateNodeRequest(null, null, null, null, null, null, List.of(item), null);
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
                new UpdateNodeRequest("done", null, null, null, null, null, null, null), "ai");
        UpdateResult r = service.updateNode("r1", "node_1",
                new UpdateNodeRequest("in_progress", null, null, null, null, null, null, null), "ai");
        assertThat(r.node().getStatus()).isEqualTo("done"); // 未回退
        assertThat(r.warnings()).anyMatch(w -> w.contains("未回退"));
    }

    @Test
    void verification_appended_with_at_and_verify_log() {
        reqWithPlan();
        DevPlan.Verification v = new DevPlan.Verification();
        v.setKind("test");
        v.setResult("pass");
        v.setSummary("10 tests, 0 failed");
        UpdateNodeRequest in = new UpdateNodeRequest(null, null, null, null, null, null, null, List.of(v));
        UpdateResult r = service.updateNode("r1", "node_1", in, "ai");
        assertThat(r.node().getVerifications()).hasSize(1);
        assertThat(r.node().getVerifications().get(0).getAt()).isNotNull();
        DevPlan.LogEntry last = r.node().getLog().get(r.node().getLog().size() - 1);
        assertThat(last.getAction()).isEqualTo("verify");
        assertThat(last.getSummary()).contains("test=pass");
    }

    @Test
    void done_with_passing_verification_clears_verify_warning() {
        reqWithPlan();
        DevPlan.Commit commit = new DevPlan.Commit();
        commit.setSha("abc");
        DevPlan.AcceptanceItem item = new DevPlan.AcceptanceItem();
        item.setText("必填校验");
        item.setChecked(true);
        DevPlan.Verification v = new DevPlan.Verification();
        v.setKind("test");
        v.setResult("pass");
        v.setSummary("ok");
        UpdateNodeRequest in = new UpdateNodeRequest("done", null, "完成", null, null, commit, List.of(item), List.of(v));
        UpdateResult r = service.updateNode("r1", "node_1", in, "ai");
        assertThat(r.warnings()).noneMatch(w -> w.contains("验证")); // 有通过验证 → 无验证警告
    }

    @Test
    void illegal_verification_kind_rejected() {
        reqWithPlan();
        DevPlan.Verification v = new DevPlan.Verification();
        v.setKind("magic");
        v.setResult("pass");
        UpdateNodeRequest in = new UpdateNodeRequest(null, null, null, null, null, null, null, List.of(v));
        assertThatThrownBy(() -> service.updateNode("r1", "node_1", in, "ai"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("验证类型");
    }

    @Test
    void aggregate_node_skips_artifact_and_verify_warnings() {
        reqWithPlan();
        service.addNodes("r1", "node_1", List.of(new NodeInput("子任务", null, null, null, null, null)));
        DevPlan.AcceptanceItem item = new DevPlan.AcceptanceItem();
        item.setText("必填校验");
        item.setChecked(true);
        UpdateNodeRequest in = new UpdateNodeRequest("done", null, null, null, null, null, List.of(item), null);
        UpdateResult r = service.updateNode("r1", "node_1", in, "ai");
        // node_1 现在有子节点 → 豁免「无产物/无验证」;但子未完成警告仍在
        assertThat(r.warnings()).noneMatch(w -> w.contains("产物"));
        assertThat(r.warnings()).noneMatch(w -> w.contains("验证"));
        assertThat(r.warnings()).anyMatch(w -> w.contains("子节点"));
    }

    // ---- root 聚合 + 需求状态联动 ----

    @Test
    void root_aggregates_to_done_when_all_leaves_done() {
        Requirement req = reqWithPlan(); // root + node_1(唯一叶)
        service.updateNode("r1", "node_1",
                new UpdateNodeRequest("done", null, null, null, null, null, null, null), "ai");
        assertThat(req.getDevPlan().getRoot().getStatus()).isEqualTo("done");
    }

    @Test
    void root_aggregates_to_in_progress_when_partial() {
        Requirement req = reqWithPlan();
        service.addNodes("r1", "node_1", List.of(
                new NodeInput("A", null, null, null, null, null),
                new NodeInput("B", null, null, null, null, null)));
        // node_1 现在非叶,叶子为 node_2(A)、node_3(B)
        service.updateNode("r1", "node_2",
                new UpdateNodeRequest("done", null, null, null, null, null, null, null), "ai");
        assertThat(req.getDevPlan().getRoot().getStatus()).isEqualTo("in_progress");
    }

    @Test
    void create_transitions_requirement_draft_to_confirmed() {
        Requirement req = new Requirement();
        req.setId("r2");
        req.setStatus("draft");
        when(repo.findById("r2")).thenReturn(Optional.of(req));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.create("r2", "需求二", null, List.of(new NodeInput("n", null, null, null, null, null)));
        assertThat(req.getStatus()).isEqualTo("confirmed");
    }

    @Test
    void requirement_done_when_root_done_and_reverts_on_reopen() {
        Requirement req = new Requirement();
        req.setId("r3");
        req.setStatus("confirmed");
        when(repo.findById("r3")).thenReturn(Optional.of(req));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.create("r3", "需求三", null, List.of(new NodeInput("n", null, null, null, null, null)));
        // 全节点 done → 需求 done
        service.updateNode("r3", "node_1",
                new UpdateNodeRequest("done", null, null, null, null, null, null, null), "ai");
        assertThat(req.getStatus()).isEqualTo("done");
        // 追加一个 todo 节点并触发 recompute → root 回退 → 需求 done 退回 confirmed
        service.addNodes("r3", "node_root", List.of(new NodeInput("追加", null, null, null, null, null)));
        service.updateNode("r3", "node_2",
                new UpdateNodeRequest(null, null, "note", null, null, null, null, null), "ai");
        assertThat(req.getStatus()).isEqualTo("confirmed");
    }
}
