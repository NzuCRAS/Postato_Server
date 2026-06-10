package com.potato.devplan;

import com.potato.devplan.DevPlanDtos.NodeInput;
import com.potato.devplan.DevPlanDtos.UpdateNodeRequest;
import com.potato.devplan.DevPlanDtos.UpdateResult;
import com.potato.requirement.Requirement;
import com.potato.requirement.RequirementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DevPlanService {

    private static final Set<String> VALID_STATUS = Set.of("todo", "in_progress", "done", "blocked");

    private final RequirementRepository requirementRepository;

    public DevPlanService(RequirementRepository requirementRepository) {
        this.requirementRepository = requirementRepository;
    }

    /** 创建开发计划树(自动分配 id、置 todo)。已存在则报 409。 */
    public DevPlan create(String reqId, String rootTitle, DevPlan.Repo repo, List<NodeInput> nodes) {
        Requirement req = getReq(reqId);
        if (req.getDevPlan() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "开发计划已存在,请用更新节点接口");
        }
        Counter counter = new Counter();
        Instant now = Instant.now();

        DevPlan.Node root = new DevPlan.Node();
        root.setId("node_root");
        root.setTitle(rootTitle != null && !rootTitle.isBlank() ? rootTitle : req.getTitle());
        root.setStatus("todo");
        root.setChildren(buildNodes(nodes, counter));
        root.getLog().add(log("human", "created", "初始分解", null, null, null, null));

        DevPlan plan = new DevPlan();
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        plan.setRepo(repo);
        plan.setRoot(root);

        req.setDevPlan(plan);
        req.setUpdatedAt(now);
        requirementRepository.save(req);
        return plan;
    }

    /**
     * 更新某节点。actor 由调用方(Controller)按认证渠道传入(ai/human)。
     * 返回更新后的节点 + 软警告列表。
     */
    public UpdateResult updateNode(String reqId, String nodeId, UpdateNodeRequest in, String actor) {
        Requirement req = getReq(reqId);
        DevPlan plan = req.getDevPlan();
        if (plan == null || plan.getRoot() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "开发计划不存在");
        }
        DevPlan.Node node = findNode(plan.getRoot(), nodeId);
        if (node == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "节点不存在: " + nodeId);
        }

        boolean appendedLog = false;
        List<String> warnings = new ArrayList<>();

        // 状态变更
        if (in.status() != null && !in.status().isBlank()) {
            if (!VALID_STATUS.contains(in.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法节点状态: " + in.status());
            }
            // 硬规则:blocked 必须给原因
            if ("blocked".equals(in.status())
                    && (in.blockedReason() == null || in.blockedReason().isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标记 blocked 必须填写 blocked_reason");
            }
            // 小保护:in_progress 仅当节点当前为 todo 时生效,避免把 done/blocked 回退
            if ("in_progress".equals(in.status()) && !"todo".equals(node.getStatus())) {
                warnings.add("节点当前为 " + node.getStatus() + ",未回退到 in_progress");
            } else {
                String from = node.getStatus();
                node.setStatus(in.status());
                node.setBlockedReason("blocked".equals(in.status()) ? in.blockedReason() : null);
                node.getLog().add(log(actor, "status_change",
                        in.logMessage() != null ? in.logMessage() : "状态变更",
                        in.logDetail(), from, in.status(), in.commit()));
                appendedLog = true;
            }
        }

        // 产物(逐字段覆盖非空值)
        if (in.artifacts() != null) {
            mergeArtifacts(node.getArtifacts(), in.artifacts());
        }

        // 验收点(整列表替换)+ 变更留痕:比对前后,把勾选/取消了哪些点写进工作日志
        if (in.acceptanceCriteria() != null) {
            Map<String, Boolean> oldState = new HashMap<>();
            for (DevPlan.AcceptanceItem a : node.getAcceptanceCriteria()) {
                oldState.put(a.getText(), a.isChecked());
            }
            node.setAcceptanceCriteria(new ArrayList<>(in.acceptanceCriteria()));

            List<String> nowChecked = new ArrayList<>();
            List<String> nowUnchecked = new ArrayList<>();
            for (DevPlan.AcceptanceItem a : node.getAcceptanceCriteria()) {
                Boolean was = oldState.get(a.getText());
                if (a.isChecked() && !Boolean.TRUE.equals(was)) nowChecked.add(a.getText());
                if (!a.isChecked() && Boolean.TRUE.equals(was)) nowUnchecked.add(a.getText());
            }
            if (!nowChecked.isEmpty() || !nowUnchecked.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                if (!nowChecked.isEmpty()) sb.append("勾选验收点:").append(String.join("、", nowChecked));
                if (!nowUnchecked.isEmpty()) {
                    if (sb.length() > 0) sb.append(";");
                    sb.append("取消验收点:").append(String.join("、", nowUnchecked));
                }
                node.getLog().add(log(actor, "acceptance", sb.toString(), null, null, null, null));
            }
        }

        // 仅补日志/commit(没有状态变更时)
        if (!appendedLog && (in.commit() != null || in.logMessage() != null || in.logDetail() != null)) {
            node.getLog().add(log(actor, "note",
                    in.logMessage() != null ? in.logMessage() : "更新",
                    in.logDetail(), null, null, in.commit()));
        }

        Instant now = Instant.now();
        plan.setUpdatedAt(now);
        req.setUpdatedAt(now);
        requirementRepository.save(req);

        warnings.addAll(computeWarnings(node));
        return new UpdateResult(node, warnings);
    }

    /** 新增纠偏(任何可查看者) */
    public DevPlan.Correction addCorrection(String reqId, String nodeId, String by, String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "纠偏内容不能为空");
        }
        Requirement req = getReq(reqId);
        DevPlan.Node node = requireNode(req, nodeId);
        DevPlan.Correction c = new DevPlan.Correction();
        c.setId("c_" + UUID.randomUUID().toString().substring(0, 8));
        c.setTimestamp(Instant.now());
        c.setBy(by);
        c.setMessage(message);
        c.setResolved(false);
        node.getCorrections().add(c);
        touch(req);
        return c;
    }

    /** 标记纠偏已解决(development) */
    public DevPlan.Correction resolveCorrection(String reqId, String nodeId, String correctionId) {
        Requirement req = getReq(reqId);
        DevPlan.Node node = requireNode(req, nodeId);
        DevPlan.Correction c = node.getCorrections().stream()
                .filter(x -> correctionId.equals(x.getId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "纠偏不存在: " + correctionId));
        c.setResolved(true);
        touch(req);
        return c;
    }

    /** 在父节点下追加子节点(自动续号、置 todo)。用于把一个节点拆成更细的子任务。 */
    public DevPlan.Node addNodes(String reqId, String parentId, List<NodeInput> nodes) {
        Requirement req = getReq(reqId);
        DevPlan.Node parent = requireNode(req, parentId);
        Counter counter = new Counter(maxNodeNumber(req.getDevPlan().getRoot()));
        List<DevPlan.Node> added = buildNodes(nodes, counter);
        parent.getChildren().addAll(added);
        touch(req);
        return parent;
    }

    /** 重置开发计划:当前树标记 archived 并入档(不删除、保留全部日志),清空 devPlan 以便重新建树。 */
    public void resetPlan(String reqId, String reason) {
        Requirement req = getReq(reqId);
        DevPlan plan = req.getDevPlan();
        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可重置的开发计划");
        }
        plan.setArchivedAt(Instant.now());
        plan.setArchiveReason(reason);
        req.getArchivedDevPlans().add(plan);
        req.setDevPlan(null);
        req.setUpdatedAt(Instant.now());
        requirementRepository.save(req);
    }

    /** 设置/更新进度树关联的代码仓库(建树后也能改)。 */
    public DevPlan.Repo setRepo(String reqId, DevPlan.Repo repo) {
        if (repo == null || repo.getUrl() == null || repo.getUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repo.url 不能为空");
        }
        Requirement req = getReq(reqId);
        DevPlan plan = req.getDevPlan();
        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "开发计划不存在");
        }
        plan.setRepo(repo);
        touch(req);
        return plan.getRepo();
    }

    /** 扫描树中形如 node_N 的最大编号(node_root 等非数字后缀计 0)。 */
    private int maxNodeNumber(DevPlan.Node node) {
        if (node == null) return 0;
        int max = 0;
        if (node.getId() != null && node.getId().startsWith("node_")) {
            try {
                max = Integer.parseInt(node.getId().substring("node_".length()));
            } catch (NumberFormatException ignored) {
                // node_root 之类,忽略
            }
        }
        for (DevPlan.Node c : node.getChildren()) {
            max = Math.max(max, maxNodeNumber(c));
        }
        return max;
    }

    // ---- 软警告 ----

    /** done 时计算软警告(不阻止操作) */
    List<String> computeWarnings(DevPlan.Node node) {
        List<String> warnings = new ArrayList<>();
        if (!"done".equals(node.getStatus())) {
            return warnings;
        }
        if (!hasAnyArtifact(node)) {
            warnings.add("节点标记为完成,但没有任何 commit 或产物(PR)。");
        }
        boolean anyUnchecked = node.getAcceptanceCriteria().stream().anyMatch(a -> !a.isChecked());
        if (anyUnchecked) {
            warnings.add("节点标记为完成,但仍有未勾选的验收标准。");
        }
        boolean childUndone = node.getChildren().stream().anyMatch(c -> !"done".equals(c.getStatus()));
        if (childUndone) {
            warnings.add("节点标记为完成,但存在未完成的子节点。");
        }
        return warnings;
    }

    private boolean hasAnyArtifact(DevPlan.Node node) {
        boolean commitInLog = node.getLog().stream().anyMatch(e -> e.getCommit() != null);
        DevPlan.Artifacts a = node.getArtifacts();
        boolean prPresent = a != null && (a.getPrNumber() != null
                || (a.getPrUrl() != null && !a.getPrUrl().isBlank()));
        return commitInLog || prPresent;
    }

    // ---- helpers ----

    private void mergeArtifacts(DevPlan.Artifacts target, DevPlan.Artifacts in) {
        if (in.getBranch() != null) target.setBranch(in.getBranch());
        if (in.getPrNumber() != null) target.setPrNumber(in.getPrNumber());
        if (in.getPrUrl() != null) target.setPrUrl(in.getPrUrl());
        if (in.getTestsAdded() != null) target.setTestsAdded(in.getTestsAdded());
        if (in.getTechProposalId() != null) target.setTechProposalId(in.getTechProposalId());
    }

    private Requirement getReq(String reqId) {
        return requirementRepository.findById(reqId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在"));
    }

    private DevPlan.Node requireNode(Requirement req, String nodeId) {
        DevPlan plan = req.getDevPlan();
        if (plan == null || plan.getRoot() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "开发计划不存在");
        }
        DevPlan.Node node = findNode(plan.getRoot(), nodeId);
        if (node == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "节点不存在: " + nodeId);
        }
        return node;
    }

    private void touch(Requirement req) {
        Instant now = Instant.now();
        req.getDevPlan().setUpdatedAt(now);
        req.setUpdatedAt(now);
        requirementRepository.save(req);
    }

    private List<DevPlan.Node> buildNodes(List<NodeInput> inputs, Counter counter) {
        List<DevPlan.Node> result = new ArrayList<>();
        if (inputs == null) return result;
        for (NodeInput in : inputs) {
            DevPlan.Node node = new DevPlan.Node();
            node.setId("node_" + counter.next());
            node.setTitle(in.title());
            node.setDescription(in.description());
            node.setStatus("todo");
            node.setModuleRef(in.moduleRef());
            if (in.acceptanceCriteria() != null) {
                for (String text : in.acceptanceCriteria()) {
                    DevPlan.AcceptanceItem item = new DevPlan.AcceptanceItem();
                    item.setText(text);
                    item.setChecked(false);
                    node.getAcceptanceCriteria().add(item);
                }
            }
            if (in.relatedDocs() != null) {
                node.setRelatedDocs(new ArrayList<>(in.relatedDocs()));
            }
            node.setChildren(buildNodes(in.children(), counter));
            node.getLog().add(log("human", "created", "初始分解", null, null, null, null));
            result.add(node);
        }
        return result;
    }

    private DevPlan.Node findNode(DevPlan.Node node, String id) {
        if (node == null) return null;
        if (id.equals(node.getId())) return node;
        for (DevPlan.Node child : node.getChildren()) {
            DevPlan.Node found = findNode(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private DevPlan.LogEntry log(String actor, String action, String summary, String detail,
                                 String from, String to, DevPlan.Commit commit) {
        DevPlan.LogEntry e = new DevPlan.LogEntry();
        e.setTimestamp(Instant.now());
        e.setActor(actor != null ? actor : "human");
        e.setAction(action);
        e.setSummary(summary);
        e.setDetail(detail);
        e.setFrom(from);
        e.setTo(to);
        e.setCommit(commit);
        return e;
    }

    private static class Counter {
        private int n;
        Counter() { this.n = 0; }
        Counter(int start) { this.n = start; }
        int next() { return ++n; }
    }
}
