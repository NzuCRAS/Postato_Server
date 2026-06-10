# 做深 Herness 进度树 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Herness 进度树做成「信息完备、可验收、可追溯、可干预」的开发驾驭树,并打通"逻辑在平台 / 代码在 GitHub"的 commit 闭环。

**Architecture:** 后端 `DevPlan` 实体由扁平结构重构为富节点(验收/关联文档/结构化产物/N条带commit的工作日志/纠偏);`DevPlanService` 增加软警告与自动日志、actor 由认证渠道推断;新增 corrections 端点;MCP 三个工具增强透传新字段;前端进度树重构为「总览条 + 树 + 节点详情抽屉」(布局 A)。范围 = A 做透 + C 做出体验 + B 轻量,D 暂缓。

**Tech Stack:** Java 17 / Spring Boot 3 / Spring Data MongoDB / Lombok / JUnit5+Mockito(后端);TypeScript / React 18 / Vite / Ant Design(前端);TypeScript / @modelcontextprotocol/sdk(MCP)。

**对应 spec:** `docs/superpowers/specs/2026-06-09-herness-dev-plan-deepening-design.md`

---

## 验证方式说明(本项目特例,务必先读)

- **当前目录不是 git 仓库**(`git rev-parse` 失败)。因此本计划用 **"重启/前端验证"检查点** 代替 `git commit`。一旦将来初始化 git,可在每个检查点处提交。
- **后端纯逻辑**(软警告、blocked 校验、自动日志、actor 推断)写 **JUnit 单测**(`spring-boot-starter-test` 已在 pom 中)。这是本计划唯一的"先写测试"环节。
- **其余层(Controller / MCP / 前端)** 遵循项目既有约定(见开发说明书 §12):写完 → `docker compose restart backend`(后端约 1~3 分钟编译)→ **在前端界面验证**。MCP 改完 `docker compose restart mcp-server` 看日志起来即可,真实验证留到里程碑 7 联调。
- 后端单测在容器内跑:`docker compose exec backend mvn -q -Dtest=DevPlanServiceTest test`(若本机有 JDK17+Maven,也可在 `backend/` 直接 `mvn -Dtest=DevPlanServiceTest test`)。

---

## 文件结构总览

**后端(`backend/src/main/java/com/potato/`)**
- `devplan/DevPlan.java` — 重构:DevPlan + Repo + Node + AcceptanceItem + Artifacts + LogEntry + Commit + Correction(改)
- `devplan/DevPlanService.java` — 建树写新字段、updateNode(软警告/自动日志/actor)、addCorrection/resolveCorrection(改)
- `devplan/DevPlanController.java` — 入参/返回扩展、corrections 端点、actor 推断(改)
- `devplan/DevPlanDtos.java` — 新建:NodeInput/NodeUpdate/UpdateResult/请求响应 record(新)
- `auth/AuthTokenFilter.java` — 给 principal 加认证渠道 authority(改)
- `config/DataSeeder.java` — 补 corrections 权限 seed(改)
- `devplan/DevPlanServiceTest.java`(test 目录)— 单测(新)

**MCP(`mcp-server/src/`)**
- `tools.ts` — 三个工具增强(改)

**前端(`frontend/src/`)**
- `types/index.ts` — 新增类型 + 扩展节点类型(改)
- `api/devplan.ts` — 接口扩展 + corrections(改)
- `features/useDevPlan.ts` — 新操作(改)
- `components/DevPlanTree.tsx` — 重构为总览条 + 树(选中回调)(改)
- `components/NodeDetailDrawer.tsx` — 节点详情抽屉(新)
- `pages/RequirementDetailPage.tsx` — 接线选中态 + 抽屉 + repo 输入(改)

---

# 阶段一:后端数据模型

## Task 1:重构 `DevPlan.java` 为富节点模型

**Files:**
- Modify(整体替换): `backend/src/main/java/com/potato/devplan/DevPlan.java`

- [ ] **Step 1: 用以下完整内容替换 `DevPlan.java`**

```java
package com.potato.devplan;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Herness 开发进度树 —— 内嵌在 Requirement 中。
 * 作为对 AI/MCP 暴露的领域数据,字段用 snake_case(与 structured、设计文档一致)。
 */
@Data
public class DevPlan {

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    /** 计划顶层记录的代码仓库(GitHub 闭环) */
    private Repo repo;

    private Node root;

    @Data
    public static class Repo {
        private String url;
        private String provider;            // 预留:github / gitlab ...
        @JsonProperty("default_branch")
        private String defaultBranch;
    }

    @Data
    public static class Node {
        private String id;
        private String title;
        private String description;
        private String status;              // todo | in_progress | done | blocked

        @JsonProperty("blocked_reason")
        private String blockedReason;

        /** 关联需求 structured.modules[].name,继承其验收标准作只读参考(可空) */
        @JsonProperty("module_ref")
        private String moduleRef;

        /** 节点自带、可勾选的细化验收点 */
        @JsonProperty("acceptance_criteria")
        private List<AcceptanceItem> acceptanceCriteria = new ArrayList<>();

        /** 关联知识库 wiki path 数组 */
        @JsonProperty("related_docs")
        private List<String> relatedDocs = new ArrayList<>();

        /** 节点级产物(commit 不在此,跟着 log 走) */
        private Artifacts artifacts = new Artifacts();

        /** N 条工作日志,commit 挂在条目上 */
        private List<LogEntry> log = new ArrayList<>();

        /** 人对 AI 的纠偏指令(独立于 log,有生命周期) */
        private List<Correction> corrections = new ArrayList<>();

        private List<Node> children = new ArrayList<>();
    }

    @Data
    public static class AcceptanceItem {
        private String text;
        private boolean checked;
    }

    @Data
    public static class Artifacts {
        private String branch;
        @JsonProperty("pr_number")
        private Integer prNumber;
        @JsonProperty("pr_url")
        private String prUrl;
        @JsonProperty("tests_added")
        private Boolean testsAdded;
        @JsonProperty("tech_proposal_id")
        private String techProposalId;     // 预留 D 轮
    }

    @Data
    public static class LogEntry {
        private Instant timestamp;
        private String actor;               // ai | human
        private String action;              // created | status_change | note
        private String summary;
        private String detail;              // 为什么这么做(中间态日志)
        private String from;
        private String to;
        private Commit commit;              // 可空
    }

    @Data
    public static class Commit {
        private String sha;
        private String url;
        private String message;
        private List<String> files = new ArrayList<>();
    }

    @Data
    public static class Correction {
        private String id;
        private Instant timestamp;
        private String by;
        private String message;
        private boolean resolved;
    }
}
```

- [ ] **Step 2: 检查点 — 编译通过**

Run: `docker compose restart backend && docker compose logs --tail=80 backend`
Expected: 日志出现 `Started PotatoApplication`(此时 Service/Controller 还引用旧签名会编译失败 —— 若失败属预期,继续 Task 2/3 后再整体重启)。
> 说明:Task 1–4 是一组联动改动,中间编译可能失败。建议**改完 Task 1–4 再统一重启验证**,本步骤仅确认实体本身语法无误(可先 `docker compose exec backend mvn -q compile -o` 跳过下游)。

---

## Task 2:新建 `DevPlanDtos.java`(输入/输出契约)

**Files:**
- Create: `backend/src/main/java/com/potato/devplan/DevPlanDtos.java`

- [ ] **Step 1: 创建文件,完整内容如下**

```java
package com.potato.devplan;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** 进度树相关的请求/响应 DTO 集中存放。 */
public final class DevPlanDtos {

    private DevPlanDtos() {}

    /** 建树:单个节点输入(递归)。acceptance_criteria 传字符串数组,后端转 AcceptanceItem(checked=false)。 */
    public record NodeInput(
            String title,
            String description,
            @JsonProperty("module_ref") String moduleRef,
            @JsonProperty("acceptance_criteria") List<String> acceptanceCriteria,
            @JsonProperty("related_docs") List<String> relatedDocs,
            List<NodeInput> children) {
    }

    /** 建树请求 */
    public record CreateDevPlanRequest(
            @JsonProperty("root_title") String rootTitle,
            DevPlan.Repo repo,
            List<NodeInput> nodes) {
    }

    /** 更新节点请求 */
    public record UpdateNodeRequest(
            String status,
            DevPlan.Artifacts artifacts,
            @JsonProperty("log_message") String logMessage,
            @JsonProperty("log_detail") String logDetail,
            @JsonProperty("blocked_reason") String blockedReason,
            DevPlan.Commit commit,
            @JsonProperty("acceptance_criteria") List<DevPlan.AcceptanceItem> acceptanceCriteria) {
    }

    /** 更新节点响应:节点 + 软警告 */
    public record UpdateNodeResponse(DevPlan.Node node, List<String> warnings) {
    }

    /** 服务层内部返回 */
    public record UpdateResult(DevPlan.Node node, List<String> warnings) {
    }

    /** 新增纠偏请求 */
    public record AddCorrectionRequest(String message) {
    }

    /** 兜底:更新节点时 artifacts 也可接受松散 Map(预留,本轮前端用强类型) */
    public record LooseArtifacts(Map<String, Object> fields) {
    }
}
```

- [ ] **Step 2: 检查点**

无需单独编译(随 Task 3/4 一起)。确认 import 与包名正确。

---

# 阶段二:后端服务逻辑(含单测)

## Task 3:重写 `DevPlanService`(建树 / updateNode 软警告+自动日志+actor / corrections)

**Files:**
- Modify(整体替换): `backend/src/main/java/com/potato/devplan/DevPlanService.java`

- [ ] **Step 1: 用以下完整内容替换 `DevPlanService.java`**

```java
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
import java.util.List;
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
            String from = node.getStatus();
            node.setStatus(in.status());
            node.setBlockedReason("blocked".equals(in.status()) ? in.blockedReason() : null);
            node.getLog().add(log(actor, "status_change",
                    in.logMessage() != null ? in.logMessage() : "状态变更",
                    in.logDetail(), from, in.status(), in.commit()));
            appendedLog = true;
        }

        // 产物(逐字段覆盖非空值)
        if (in.artifacts() != null) {
            mergeArtifacts(node.getArtifacts(), in.artifacts());
        }

        // 验收点(整列表替换)
        if (in.acceptanceCriteria() != null) {
            node.setAcceptanceCriteria(new ArrayList<>(in.acceptanceCriteria()));
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

        return new UpdateResult(node, computeWarnings(node));
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
        private int n = 0;
        int next() { return ++n; }
    }
}
```

- [ ] **Step 2: 检查点**

随 Task 4 一起编译验证(Controller 仍需改才能编译通过)。

---

## Task 4:更新 `DevPlanController`(入参/返回扩展 + corrections 端点 + actor 推断)

**Files:**
- Modify(整体替换): `backend/src/main/java/com/potato/devplan/DevPlanController.java`

- [ ] **Step 1: 用以下完整内容替换 `DevPlanController.java`**

```java
package com.potato.devplan;

import com.potato.devplan.DevPlanDtos.AddCorrectionRequest;
import com.potato.devplan.DevPlanDtos.CreateDevPlanRequest;
import com.potato.devplan.DevPlanDtos.UpdateNodeRequest;
import com.potato.devplan.DevPlanDtos.UpdateNodeResponse;
import com.potato.devplan.DevPlanDtos.UpdateResult;
import com.potato.permission.PermissionService;
import com.potato.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/requirements/{reqId}/dev-plan")
public class DevPlanController {

    private final DevPlanService service;
    private final PermissionService permissionService;

    public DevPlanController(DevPlanService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @PostMapping
    public DevPlan create(@AuthenticationPrincipal User user,
                          @PathVariable String reqId,
                          @RequestBody CreateDevPlanRequest req) {
        permissionService.check(user, "dev_plan", "create");
        return service.create(reqId, req.rootTitle(), req.repo(), req.nodes());
    }

    @PatchMapping("/nodes/{nodeId}")
    public UpdateNodeResponse updateNode(@AuthenticationPrincipal User user,
                                         Authentication authentication,
                                         @PathVariable String reqId,
                                         @PathVariable String nodeId,
                                         @RequestBody UpdateNodeRequest req) {
        permissionService.check(user, "dev_plan", "update");
        UpdateResult r = service.updateNode(reqId, nodeId, req, actorOf(authentication));
        return new UpdateNodeResponse(r.node(), r.warnings());
    }

    /** 新增纠偏:任何可查看该需求的人 */
    @PostMapping("/nodes/{nodeId}/corrections")
    public DevPlan.Correction addCorrection(@AuthenticationPrincipal User user,
                                            @PathVariable String reqId,
                                            @PathVariable String nodeId,
                                            @RequestBody AddCorrectionRequest req) {
        permissionService.check(user, "requirement", "view");
        return service.addCorrection(reqId, nodeId, user.getUsername(), req.message());
    }

    /** 标记纠偏已解决:development */
    @PatchMapping("/nodes/{nodeId}/corrections/{correctionId}")
    public DevPlan.Correction resolveCorrection(@AuthenticationPrincipal User user,
                                                @PathVariable String reqId,
                                                @PathVariable String nodeId,
                                                @PathVariable String correctionId) {
        permissionService.check(user, "dev_plan", "update");
        return service.resolveCorrection(reqId, nodeId, correctionId);
    }

    /** API Key 渠道 → ai;JWT 渠道 → human(authority 由 AuthTokenFilter 注入) */
    private String actorOf(Authentication authentication) {
        if (authentication != null) {
            for (GrantedAuthority a : authentication.getAuthorities()) {
                if ("CHANNEL_APIKEY".equals(a.getAuthority())) return "ai";
            }
        }
        return "human";
    }
}
```

- [ ] **Step 2: 检查点 — 整体编译**

Run: `docker compose restart backend && docker compose logs -f backend`
Expected: `Started PotatoApplication`,无编译错误。若报错,先按报错定位(常见:lombok getter 名、record 字段名)。

---

## Task 5:`AuthTokenFilter` 注入认证渠道 authority

**Files:**
- Modify: `backend/src/main/java/com/potato/auth/AuthTokenFilter.java`

- [ ] **Step 1: 在 `doFilterInternal` 内,替换构建 authorities 的代码块**

找到这段:
```java
            String token = header.substring(7).trim();
            Optional<User> user = resolveUser(token);
            user.ifPresent(u -> {
                List<SimpleGrantedAuthority> authorities = u.getFunctions().stream()
                        .map(f -> new SimpleGrantedAuthority("ROLE_" + f))
                        .toList();
```
替换为(加入渠道 authority):
```java
            String token = header.substring(7).trim();
            boolean viaApiKey = token.startsWith("mcp_");
            Optional<User> user = resolveUser(token);
            user.ifPresent(u -> {
                List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>(
                        u.getFunctions().stream()
                                .map(f -> new SimpleGrantedAuthority("ROLE_" + f))
                                .toList());
                authorities.add(new SimpleGrantedAuthority(viaApiKey ? "CHANNEL_APIKEY" : "CHANNEL_JWT"));
```

> 注意:原 `user.ifPresent(u -> { ... })` 闭包内后续代码(构建 `UsernamePasswordAuthenticationToken`、`setDetails`、`setAuthentication`)保持不变;只是 `authorities` 的构造改为可变 List 并追加渠道项。确认闭包结尾 `});` 完整。

- [ ] **Step 2: 检查点**

Run: `docker compose restart backend && docker compose logs --tail=60 backend`
Expected: `Started PotatoApplication`。

---

## Task 6:`DataSeeder` 补 corrections 权限规则

**Files:**
- Modify: `backend/src/main/java/com/potato/config/DataSeeder.java:67-76`

- [ ] **Step 1: 在 `seedPermissionRules()` 的规则列表里追加两条**

把:
```java
                rule("dev_plan", "create", "development"),
                rule("dev_plan", "update", "development")
        );
```
改为:
```java
                rule("dev_plan", "create", "development"),
                rule("dev_plan", "update", "development"),
                rule("dev_plan", "comment", "development", "testing", "product")
        );
```
> 说明:`dev_plan/comment` = 留纠偏的语义权限位(留作未来用)。**当前 Controller 留纠偏复用 `requirement/view` 检查**(已能覆盖 development/testing/product),此条为前瞻冗余,无害。`resolveCorrection` 用 `dev_plan/update`。

- [ ] **Step 2: 检查点 — 重置权限 seed 使其重新写入**

> `seedPermissionRules` 仅在 `permission_rules` 为空时写入。要让新规则生效,需清空该集合后重启:
```bash
docker compose exec mongo mongosh potato --quiet --eval "db.permission_rules.deleteMany({})"
docker compose restart backend
```
Expected: 后端日志 `Seeded 9 permission rules.`(原 8 条 + 新增 1 条)。

---

# 阶段三:后端单测

## Task 7:为 `DevPlanService` 写 JUnit 单测

**Files:**
- Create: `backend/src/test/java/com/potato/devplan/DevPlanServiceTest.java`

- [ ] **Step 1: 创建测试(先写,预期 RED)**

```java
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
}
```

- [ ] **Step 2: 跑测试,预期先 RED 再 GREEN**

Run: `docker compose exec backend mvn -q -Dtest=DevPlanServiceTest test`
Expected: 全绿。若 RED,按断言信息修 Service(本计划 Service 已按这些断言实现,正常应直接 GREEN)。

- [ ] **Step 3: 检查点**

后端逻辑层完成。

---

# 阶段四:MCP 工具增强

## Task 8:增强 `mcp-server/src/tools.ts` 三个工具

**Files:**
- Modify: `mcp-server/src/tools.ts`

- [ ] **Step 1: 替换 `create_dev_plan` 工具注册块**(支持 repo + 节点新字段)

把现有 `server.tool('create_dev_plan', ... )` 整段替换为:
```ts
  server.tool(
    'create_dev_plan',
    '为需求创建模块化开发计划树(Herness)。按软件模块分解节点(可嵌套 children),每个节点可带 module_ref(关联需求模块名)、acceptance_criteria(验收点文本数组)、related_docs(知识库 path)。建树时可在 repo 记录 GitHub 仓库。已存在则返回 409。',
    {
      requirement_id: z.string().describe('需求 ID'),
      repo: z
        .object({
          url: z.string(),
          provider: z.string().optional(),
          default_branch: z.string().optional(),
        })
        .optional()
        .describe('GitHub 仓库,如 {url:"https://github.com/org/repo", default_branch:"main"}'),
      nodes: z
        .array(
          z.object({
            title: z.string(),
            description: z.string().optional(),
            module_ref: z.string().optional(),
            acceptance_criteria: z.array(z.string()).optional(),
            related_docs: z.array(z.string()).optional(),
            children: z.array(z.any()).optional(),
          }),
        )
        .describe('分解出的节点(children 可继续嵌套同样结构)'),
      root_title: z.string().optional().describe('根节点标题,默认用需求标题'),
    },
    async ({ requirement_id, repo, nodes, root_title }) => {
      try {
        const plan = await backendRequest<unknown>(`/requirements/${requirement_id}/dev-plan`, apiKey, {
          method: 'POST',
          body: JSON.stringify({ root_title, repo, nodes }),
        })
        return { content: [{ type: 'text' as const, text: JSON.stringify(plan, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )
```

- [ ] **Step 2: 替换 `update_dev_plan_node` 工具注册块**(支持 commit / log_detail / 透传 warnings)

```ts
  server.tool(
    'update_dev_plan_node',
    '更新某个进度节点:状态、artifacts(branch/pr_number/pr_url/tests_added)、commit(本次提交,挂到工作日志)、log_message(摘要)/log_detail(为什么这么做)、blocked_reason。完成一段编码后用本工具上报 commit。响应可能含 warnings(软提醒,不阻断)。',
    {
      requirement_id: z.string().describe('需求 ID'),
      node_id: z.string().describe('节点 ID,如 node_1'),
      status: z.enum(['todo', 'in_progress', 'done', 'blocked']).optional(),
      artifacts: z
        .object({
          branch: z.string().optional(),
          pr_number: z.number().optional(),
          pr_url: z.string().optional(),
          tests_added: z.boolean().optional(),
        })
        .optional(),
      commit: z
        .object({
          sha: z.string(),
          url: z.string().optional(),
          message: z.string().optional(),
          files: z.array(z.string()).optional(),
        })
        .optional()
        .describe('本次提交;url 可省略(平台用 repo.url + sha 拼)'),
      log_message: z.string().optional().describe('本次操作摘要'),
      log_detail: z.string().optional().describe('为什么这么做(决策依据,写进中间态日志)'),
      blocked_reason: z.string().optional().describe('status=blocked 时必填'),
    },
    async ({ requirement_id, node_id, status, artifacts, commit, log_message, log_detail, blocked_reason }) => {
      try {
        const res = await backendRequest<unknown>(
          `/requirements/${requirement_id}/dev-plan/nodes/${node_id}`,
          apiKey,
          {
            method: 'PATCH',
            body: JSON.stringify({ status, artifacts, commit, log_message, log_detail, blocked_reason }),
          },
        )
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )
```

- [ ] **Step 3: 替换 `summarizePlan` 函数**(让 get_requirement_detail 带上更多字段)

把文件底部的 `summarizePlan` 替换为:
```ts
function summarizePlan(plan: Record<string, any>) {
  const flat: Array<Record<string, any>> = []
  const walk = (n: Record<string, any> | undefined) => {
    if (!n) return
    flat.push({
      id: n.id,
      title: n.title,
      status: n.status,
      module_ref: n.module_ref,
      acceptance_criteria: n.acceptance_criteria, // 含 checked,AI 可见哪些没勾
      related_docs: n.related_docs,
      blocked_reason: n.blocked_reason,
      open_corrections: (n.corrections ?? []).filter((c: any) => !c.resolved), // 未解决纠偏
    })
    ;(n.children ?? []).forEach(walk)
  }
  walk(plan.root)
  return { repo: plan.repo, root_status: plan.root?.status, nodes: flat }
}
```
> `get_requirement_detail` 的工具体无需改(它已调用 `summarizePlan(r.devPlan)`)。

- [ ] **Step 4: 检查点**

Run: `docker compose restart mcp-server && docker compose logs --tail=40 mcp-server`
Expected: MCP 服务正常起来(无 TS 编译/启动报错)。真实工具行为留待里程碑 7 用 Claude Code 联调。

---

# 阶段五:前端

## Task 9:扩展 `types/index.ts`

**Files:**
- Modify: `frontend/src/types/index.ts`(替换"进度树"区块)

- [ ] **Step 1: 把 `// ---- 进度树(里程碑 3)----` 到 `export interface DevPlan {...}` 整段替换为**

```ts
// ---- 进度树(里程碑 3 + 做深)----

export interface AcceptanceItem {
  text: string
  checked: boolean
}

export interface DevPlanCommit {
  sha: string
  url?: string
  message?: string
  files?: string[]
}

export interface DevPlanArtifacts {
  branch?: string
  pr_number?: number
  pr_url?: string
  tests_added?: boolean
  tech_proposal_id?: string
}

export interface DevPlanLog {
  timestamp: string
  actor?: string // ai | human
  action: string // created | status_change | note
  summary?: string
  detail?: string
  from?: string
  to?: string
  commit?: DevPlanCommit
}

export interface DevPlanCorrection {
  id: string
  timestamp: string
  by: string
  message: string
  resolved: boolean
}

export interface DevPlanNode {
  id: string
  title: string
  description?: string
  status: string // todo | in_progress | done | blocked
  blocked_reason?: string
  module_ref?: string
  acceptance_criteria?: AcceptanceItem[]
  related_docs?: string[]
  artifacts?: DevPlanArtifacts
  log?: DevPlanLog[]
  corrections?: DevPlanCorrection[]
  children?: DevPlanNode[]
}

export interface DevPlanRepo {
  url: string
  provider?: string
  default_branch?: string
}

export interface DevPlan {
  created_at?: string
  updated_at?: string
  repo?: DevPlanRepo
  root: DevPlanNode
}
```

- [ ] **Step 2: 检查点**

前端热重载;此时引用旧字段处(useDevPlan/DevPlanTree)可能有 TS 报错,随后续 Task 修复。

---

## Task 10:重写 `api/devplan.ts`

**Files:**
- Modify(整体替换): `frontend/src/api/devplan.ts`

- [ ] **Step 1: 完整替换为**

```ts
// 数据访问层:开发进度树接口
import { request } from './client'
import type {
  AcceptanceItem,
  DevPlan,
  DevPlanArtifacts,
  DevPlanCommit,
  DevPlanCorrection,
  DevPlanNode,
  DevPlanRepo,
} from '../types'

export interface NodeInput {
  title: string
  description?: string
  module_ref?: string
  acceptance_criteria?: string[]
  related_docs?: string[]
  children?: NodeInput[]
}

export function createDevPlan(
  reqId: string,
  nodes: NodeInput[],
  opts?: { rootTitle?: string; repo?: DevPlanRepo },
): Promise<DevPlan> {
  return request<DevPlan>(`/requirements/${reqId}/dev-plan`, {
    method: 'POST',
    body: JSON.stringify({ root_title: opts?.rootTitle, repo: opts?.repo, nodes }),
  })
}

export interface NodeUpdate {
  status?: string
  artifacts?: DevPlanArtifacts
  commit?: DevPlanCommit
  log_message?: string
  log_detail?: string
  blocked_reason?: string
  acceptance_criteria?: AcceptanceItem[]
}

export interface UpdateNodeResponse {
  node: DevPlanNode
  warnings: string[]
}

export function updateDevPlanNode(
  reqId: string,
  nodeId: string,
  updates: NodeUpdate,
): Promise<UpdateNodeResponse> {
  return request<UpdateNodeResponse>(`/requirements/${reqId}/dev-plan/nodes/${nodeId}`, {
    method: 'PATCH',
    body: JSON.stringify(updates),
  })
}

export function addCorrection(
  reqId: string,
  nodeId: string,
  message: string,
): Promise<DevPlanCorrection> {
  return request<DevPlanCorrection>(`/requirements/${reqId}/dev-plan/nodes/${nodeId}/corrections`, {
    method: 'POST',
    body: JSON.stringify({ message }),
  })
}

export function resolveCorrection(
  reqId: string,
  nodeId: string,
  correctionId: string,
): Promise<DevPlanCorrection> {
  return request<DevPlanCorrection>(
    `/requirements/${reqId}/dev-plan/nodes/${nodeId}/corrections/${correctionId}`,
    { method: 'PATCH' },
  )
}
```

- [ ] **Step 2: 检查点** — TS 编译该文件无误。

---

## Task 11:重写 `features/useDevPlan.ts`

**Files:**
- Modify(整体替换): `frontend/src/features/useDevPlan.ts`

- [ ] **Step 1: 完整替换为**

```ts
// 逻辑层:开发进度树操作。数据源是需求详情的 devPlan,变更后回调 onChanged 刷新。
import { useState } from 'react'
import { message } from 'antd'
import {
  addCorrection,
  createDevPlan,
  resolveCorrection,
  updateDevPlanNode,
  type NodeInput,
  type NodeUpdate,
} from '../api/devplan'
import type { DevPlanRepo, StructuredModule } from '../types'

export function useDevPlan(reqId: string, onChanged: () => Promise<void> | void) {
  const [busy, setBusy] = useState(false)

  const run = async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true)
    try {
      const r = await fn()
      await onChanged()
      return r
    } catch (e) {
      message.error(e instanceof Error ? e.message : '操作失败')
      return undefined
    } finally {
      setBusy(false)
    }
  }

  // 基于需求 structured.modules 预生成初始树(每模块一个一级节点,继承验收标准)
  const generateFromModules = async (modules: StructuredModule[], repo?: DevPlanRepo): Promise<void> => {
    const nodes: NodeInput[] = (modules ?? [])
      .filter((m) => m.name)
      .map((m) => ({
        title: m.name,
        description: m.description,
        module_ref: m.name,
        acceptance_criteria: m.acceptance_criteria ?? [],
      }))
    await run(() =>
      createDevPlan(reqId, nodes.length ? nodes : [{ title: '根任务' }], {
        repo: repo?.url ? repo : undefined,
      }),
    )
  }

  // 更新节点;返回软警告(供视图弹提示)
  const updateNode = async (nodeId: string, updates: NodeUpdate): Promise<string[]> => {
    const res = await run(() => updateDevPlanNode(reqId, nodeId, updates))
    return res?.warnings ?? []
  }

  const leaveCorrection = (nodeId: string, msg: string) =>
    run(() => addCorrection(reqId, nodeId, msg))

  const markCorrectionResolved = (nodeId: string, cid: string) =>
    run(() => resolveCorrection(reqId, nodeId, cid))

  return { busy, generateFromModules, updateNode, leaveCorrection, markCorrectionResolved }
}
```

- [ ] **Step 2: 检查点** — TS 无误(RequirementDetailPage 仍引用旧 API,下一 Task 修)。

---

## Task 12:新建节点详情抽屉 `components/NodeDetailDrawer.tsx`

**Files:**
- Create: `frontend/src/components/NodeDetailDrawer.tsx`

- [ ] **Step 1: 创建文件,完整内容如下**

```tsx
// 视图层:节点详情抽屉。展示描述/验收/关联文档/产物/工作日志(带 commit 链接)/纠偏,并把操作转给 hook。
import { useState } from 'react'
import {
  Button,
  Checkbox,
  Divider,
  Drawer,
  Dropdown,
  Empty,
  Input,
  Space,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd'
import type { AcceptanceItem, DevPlanNode, DevPlanRepo } from '../types'

const { Text, Paragraph, Link } = Typography

const STATUS_LABEL: Record<string, string> = {
  todo: '待办',
  in_progress: '进行中',
  done: '完成',
  blocked: '阻塞',
}
const STATUS_COLOR: Record<string, string> = {
  todo: 'default',
  in_progress: 'processing',
  done: 'success',
  blocked: 'error',
}
const STATUSES = ['todo', 'in_progress', 'done', 'blocked']

function commitHref(repo: DevPlanRepo | undefined, sha: string, url?: string): string | undefined {
  if (url) return url
  if (repo?.url) return `${repo.url.replace(/\/$/, '')}/commit/${sha}`
  return undefined
}

export default function NodeDetailDrawer({
  node,
  repo,
  open,
  onClose,
  onChangeStatus,
  onToggleAcceptance,
  onLeaveCorrection,
  onResolveCorrection,
}: {
  node: DevPlanNode | null
  repo?: DevPlanRepo
  open: boolean
  onClose: () => void
  onChangeStatus: (nodeId: string, status: string) => Promise<string[]>
  onToggleAcceptance: (nodeId: string, items: AcceptanceItem[]) => Promise<void>
  onLeaveCorrection: (nodeId: string, msg: string) => Promise<void>
  onResolveCorrection: (nodeId: string, cid: string) => Promise<void>
}) {
  const [correction, setCorrection] = useState('')

  if (!node) return null

  const changeStatus = async (status: string) => {
    const warnings = await onChangeStatus(node.id, status)
    if (warnings.length) warnings.forEach((w) => message.warning(w))
    else message.success(`已改为「${STATUS_LABEL[status] ?? status}」`)
  }

  const toggleAcceptance = async (idx: number) => {
    const items = (node.acceptance_criteria ?? []).map((a, i) =>
      i === idx ? { ...a, checked: !a.checked } : a,
    )
    await onToggleAcceptance(node.id, items)
  }

  const submitCorrection = async () => {
    if (!correction.trim()) return
    await onLeaveCorrection(node.id, correction.trim())
    setCorrection('')
    message.success('已留下纠偏指令')
  }

  return (
    <Drawer
      title={
        <Space>
          <Tag color={STATUS_COLOR[node.status] ?? 'default'}>{STATUS_LABEL[node.status] ?? node.status}</Tag>
          <span>{node.title}</span>
        </Space>
      }
      width={460}
      open={open}
      onClose={onClose}
      extra={
        <Dropdown
          menu={{
            items: STATUSES.map((s) => ({ key: s, label: STATUS_LABEL[s] })),
            // 所有状态统一交给 changeStatus → onChangeStatus(页面层负责 blocked 弹窗收原因)
            onClick: ({ key }) => changeStatus(key),
          }}
        >
          <Button size="small">改状态 ▾</Button>
        </Dropdown>
      }
    >
      {node.description && <Paragraph type="secondary">{node.description}</Paragraph>}
      {node.status === 'blocked' && node.blocked_reason && (
        <Paragraph type="danger">阻塞原因:{node.blocked_reason}</Paragraph>
      )}

      <Divider orientation="left" plain>验收标准</Divider>
      {node.module_ref && <Text type="secondary">继承自模块:{node.module_ref}</Text>}
      {(node.acceptance_criteria ?? []).length ? (
        <Space direction="vertical">
          {(node.acceptance_criteria ?? []).map((a, i) => (
            <Checkbox key={i} checked={a.checked} onChange={() => toggleAcceptance(i)}>
              {a.text}
            </Checkbox>
          ))}
        </Space>
      ) : (
        <Text type="secondary">(无细化验收点)</Text>
      )}

      <Divider orientation="left" plain>关联文档</Divider>
      {(node.related_docs ?? []).length ? (
        <Space direction="vertical">
          {(node.related_docs ?? []).map((p) => (
            <Link key={p} href={`/wiki?path=${encodeURIComponent(p)}`} target="_blank">
              {p}
            </Link>
          ))}
        </Space>
      ) : (
        <Text type="secondary">(无)</Text>
      )}

      <Divider orientation="left" plain>产物</Divider>
      <Space direction="vertical" size={2}>
        {node.artifacts?.branch && <Text>分支:{node.artifacts.branch}</Text>}
        {node.artifacts?.pr_number != null && (
          <Text>
            PR:{' '}
            {node.artifacts.pr_url ? (
              <Link href={node.artifacts.pr_url} target="_blank">#{node.artifacts.pr_number}</Link>
            ) : (
              `#${node.artifacts.pr_number}`
            )}
          </Text>
        )}
        {node.artifacts?.tests_added != null && <Text>测试:{node.artifacts.tests_added ? '已加' : '未加'}</Text>}
        {!node.artifacts?.branch && node.artifacts?.pr_number == null && (
          <Text type="secondary">(无)</Text>
        )}
      </Space>

      <Divider orientation="left" plain>工作日志</Divider>
      {(node.log ?? []).length ? (
        <Timeline
          items={(node.log ?? []).map((e) => ({
            color: e.actor === 'ai' ? 'blue' : 'green',
            children: (
              <div>
                <Text strong>
                  {e.actor === 'ai' ? '🤖 AI' : '👤 人'} · {e.summary}
                </Text>
                {e.from && e.to && (
                  <div>
                    <Text type="secondary">{e.from} → {e.to}</Text>
                  </div>
                )}
                {e.detail && <Paragraph type="secondary" style={{ margin: 0 }}>{e.detail}</Paragraph>}
                {e.commit && (
                  <div>
                    {commitHref(repo, e.commit.sha, e.commit.url) ? (
                      <Link href={commitHref(repo, e.commit.sha, e.commit.url)} target="_blank">
                        commit {e.commit.sha.slice(0, 7)}
                      </Link>
                    ) : (
                      <Text code>commit {e.commit.sha.slice(0, 7)}</Text>
                    )}
                    {e.commit.message ? <Text type="secondary"> — {e.commit.message}</Text> : null}
                  </div>
                )}
              </div>
            ),
          }))}
        />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无日志" />
      )}

      <Divider orientation="left" plain>纠偏(给 AI 的指令)</Divider>
      {(node.corrections ?? []).map((c) => (
        <div key={c.id} style={{ marginBottom: 8 }}>
          <Space>
            <Tag color={c.resolved ? 'success' : 'warning'}>{c.resolved ? '已解决' : '未解决'}</Tag>
            <Text>{c.message}</Text>
          </Space>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>by {c.by}</Text>
            {!c.resolved && (
              <Button type="link" size="small" onClick={() => onResolveCorrection(node.id, c.id)}>
                标记已解决
              </Button>
            )}
          </div>
        </div>
      ))}
      <Space.Compact style={{ width: '100%', marginTop: 8 }}>
        <Input
          placeholder="留一条自然语言纠偏指令,AI 下次会读到"
          value={correction}
          onChange={(e) => setCorrection(e.target.value)}
          onPressEnter={submitCorrection}
        />
        <Button type="primary" onClick={submitCorrection}>留言</Button>
      </Space.Compact>
    </Drawer>
  )
}
```

> 注:`blocked` 必填原因的弹窗在 Task 14 的页面层 `changeNodeStatus`(作为 `onChangeStatus` 传入)统一处理 —— 它收集原因后带 `blocked_reason` 调后端。本组件只负责把选中的状态透传上去。

- [ ] **Step 2: 检查点** — 文件 TS 无误(尚未挂载)。

---

## Task 13:重写 `components/DevPlanTree.tsx`(总览条 + 树 + 选中回调)

**Files:**
- Modify(整体替换): `frontend/src/components/DevPlanTree.tsx`

- [ ] **Step 1: 完整替换为**

```tsx
// 视图层:开发进度树。顶部总览条(状态计数/阻塞高亮/仓库) + antd Tree(点节点回调选中)。
import { Card, Space, Tag, Tree, Typography } from 'antd'
import type { DataNode } from 'antd/es/tree'
import type { DevPlan, DevPlanNode } from '../types'

const STATUS_COLOR: Record<string, string> = {
  todo: 'default',
  in_progress: 'processing',
  done: 'success',
  blocked: 'error',
}
const STATUS_LABEL: Record<string, string> = {
  todo: '待办',
  in_progress: '进行中',
  done: '完成',
  blocked: '阻塞',
}

function collectStats(node: DevPlanNode, acc: Record<string, number>, leaves: { done: number; total: number }) {
  acc[node.status] = (acc[node.status] ?? 0) + 1
  const isLeaf = !node.children?.length
  if (isLeaf) {
    leaves.total += 1
    if (node.status === 'done') leaves.done += 1
  }
  node.children?.forEach((c) => collectStats(c, acc, leaves))
}

function nodeTitle(node: DevPlanNode) {
  const hasCommit = node.log?.some((e) => e.commit)
  const openCorrections = (node.corrections ?? []).filter((c) => !c.resolved).length
  return (
    <Space size={6}>
      <Tag color={STATUS_COLOR[node.status] ?? 'default'}>{STATUS_LABEL[node.status] ?? node.status}</Tag>
      <Typography.Text strong>{node.title}</Typography.Text>
      {hasCommit && <Tag color="blue">commit</Tag>}
      {node.artifacts?.pr_number != null && <Tag color="geekblue">PR #{node.artifacts.pr_number}</Tag>}
      {openCorrections > 0 && <Tag color="warning">纠偏 {openCorrections}</Tag>}
    </Space>
  )
}

function toTreeData(node: DevPlanNode): DataNode {
  return {
    key: node.id,
    title: nodeTitle(node),
    children: node.children?.length ? node.children.map(toTreeData) : undefined,
  }
}

export default function DevPlanTree({
  plan,
  onSelectNode,
}: {
  plan: DevPlan
  onSelectNode: (nodeId: string) => void
}) {
  const counts: Record<string, number> = {}
  const leaves = { done: 0, total: 0 }
  collectStats(plan.root, counts, leaves)
  const pct = leaves.total ? Math.round((leaves.done / leaves.total) * 100) : 0

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card size="small">
        <Space wrap split="·">
          <span>进度 {pct}%(叶子 {leaves.done}/{leaves.total})</span>
          <Space size={4}>
            <Tag>{STATUS_LABEL.todo} {counts.todo ?? 0}</Tag>
            <Tag color="processing">{STATUS_LABEL.in_progress} {counts.in_progress ?? 0}</Tag>
            <Tag color="success">{STATUS_LABEL.done} {counts.done ?? 0}</Tag>
            <Tag color="error">{STATUS_LABEL.blocked} {counts.blocked ?? 0}</Tag>
          </Space>
          {plan.repo?.url && (
            <Typography.Link href={plan.repo.url} target="_blank">🔗 {plan.repo.url.replace(/^https?:\/\//, '')}</Typography.Link>
          )}
        </Space>
      </Card>
      <Card size="small">
        <Tree
          treeData={[toTreeData(plan.root)]}
          defaultExpandAll
          selectable
          onSelect={(keys) => keys[0] && onSelectNode(String(keys[0]))}
        />
      </Card>
    </Space>
  )
}
```

- [ ] **Step 2: 检查点** — TS 无误(详情页接线在下一 Task)。

---

## Task 14:接线 `pages/RequirementDetailPage.tsx`(选中态 + 抽屉 + repo 输入)

**Files:**
- Modify: `frontend/src/pages/RequirementDetailPage.tsx`

- [ ] **Step 1: 替换 import 段**(第 1–9 行)为

```tsx
// 视图层:需求详情 + 状态流转 + 编辑入口 + 开发进度树
import { useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Input, Space, Spin, Tabs, Tag, Typography, message } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { useRequirementDetail } from '../features/useRequirementDetail'
import { useDevPlan } from '../features/useDevPlan'
import { statusColor, statusLabel } from '../features/requirementStatus'
import StructuredView from '../components/StructuredView'
import DevPlanTree from '../components/DevPlanTree'
import NodeDetailDrawer from '../components/NodeDetailDrawer'
import { updateDevPlanNode } from '../api/devplan'
import type { AcceptanceItem, DevPlanNode } from '../types'
```

- [ ] **Step 2: 替换组件体内"状态与回调"部分**

把现有的:
```tsx
  const dp = useDevPlan(id, reload)
  const [activeTab, setActiveTab] = useState('doc')
```
替换为:
```tsx
  const dp = useDevPlan(id, reload)
  const [activeTab, setActiveTab] = useState('doc')
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [repoUrl, setRepoUrl] = useState('')

  // 从最新树里按 id 找选中节点(刷新后保持引用最新)
  const findNode = (n: DevPlanNode | undefined, target: string): DevPlanNode | null => {
    if (!n) return null
    if (n.id === target) return n
    for (const c of n.children ?? []) {
      const f = findNode(c, target)
      if (f) return f
    }
    return null
  }
  const selectedNode = useMemo(
    () => (data?.devPlan && selectedNodeId ? findNode(data.devPlan.root, selectedNodeId) : null),
    [data, selectedNodeId],
  )

  // 改状态:blocked 走弹窗收原因,统一带 blocked_reason 调后端,返回 warnings
  const changeNodeStatus = async (nodeId: string, status: string): Promise<string[]> => {
    let blocked_reason: string | undefined
    if (status === 'blocked') {
      const r = window.prompt('请填写阻塞原因(blocked 必填):') ?? ''
      if (!r.trim()) {
        message.info('已取消(blocked 需要原因)')
        return []
      }
      blocked_reason = r.trim()
    }
    return dp.updateNode(nodeId, { status, blocked_reason, log_message: `手动改为 ${status}` })
  }

  const toggleAcceptance = async (nodeId: string, items: AcceptanceItem[]): Promise<void> => {
    await updateDevPlanNode(id, nodeId, { acceptance_criteria: items })
    await reload()
  }
```

> 说明:`toggleAcceptance` 直接调 api(不需软警告/统一 busy),改完 `reload()`。其余操作走 `dp.*`。

- [ ] **Step 3: 替换 `devPlanTab` 定义**

把现有 `const devPlanTab = data.devPlan ? (...) : (...)` 整段替换为:
```tsx
  const devPlanTab = data.devPlan ? (
    <>
      <DevPlanTree plan={data.devPlan} onSelectNode={setSelectedNodeId} />
      <NodeDetailDrawer
        node={selectedNode}
        repo={data.devPlan.repo}
        open={!!selectedNode}
        onClose={() => setSelectedNodeId(null)}
        onChangeStatus={changeNodeStatus}
        onToggleAcceptance={toggleAcceptance}
        onLeaveCorrection={dp.leaveCorrection}
        onResolveCorrection={dp.markCorrectionResolved}
      />
    </>
  ) : (
    <Space direction="vertical" style={{ width: '100%' }} align="center">
      <Empty description="还没有开发计划" />
      <Input
        style={{ maxWidth: 420 }}
        placeholder="(可选)GitHub 仓库地址,如 https://github.com/org/repo"
        value={repoUrl}
        onChange={(e) => setRepoUrl(e.target.value)}
      />
      <Button
        type="primary"
        loading={dp.busy}
        onClick={async () => {
          await dp.generateFromModules(
            data.structured.modules ?? [],
            repoUrl.trim() ? { url: repoUrl.trim(), provider: 'github', default_branch: 'main' } : undefined,
          )
          message.success('已生成开发计划')
        }}
      >
        基于模块生成开发计划
      </Button>
      <Typography.Text type="secondary">(根据「需求文档」里的模块生成初始树,并继承验收标准)</Typography.Text>
    </Space>
  )
```

- [ ] **Step 4: 删除过时的 `onChangeNodeStatus` 与 `onGenerate`**

原文件中旧的 `onChangeNodeStatus`(调 `dp.changeNodeStatus`)和 `onGenerate` 函数已被 Step 2/3 取代,删除它们,避免引用已不存在的 `dp.changeNodeStatus`。保留 `onChangeStatus`(需求状态流转,与节点无关)。

- [ ] **Step 5: 检查点 — 前端整体验证**

确保 `docker compose` 各服务在跑。浏览器打开一条 **confirmed** 需求详情 → 「开发进度」Tab:
- 无计划时:能填 repo、点"生成开发计划";生成后总览条显示状态计数 + 仓库链接。
- 点节点 → 右侧抽屉滑出:看到描述/验收(可勾选)/关联文档/产物/工作日志/纠偏区。
- 改状态为 done(无产物)→ 顶部出现软警告 toast;改 blocked → 弹窗要求原因,留空则取消。
- 勾选验收点 → 刷新后保持;留一条纠偏 → 出现"未解决";点"标记已解决"→ 变"已解决"。
Expected: 以上全部正常;无控制台报错。

---

# 阶段六:文档

## Task 15:同步开发说明书

**Files:**
- Modify: `docs/平台开发说明.md`

- [ ] **Step 1: 更新里程碑表与数据模型段**

- 在 §3 数据模型把 `devPlan` 的 Node 字段更新为做深后的结构(repo / module_ref / acceptance_criteria[{text,checked}] / related_docs / artifacts 结构化 / log 增 actor+detail+commit / corrections)。
- 在 §9 里程碑表新增一行:`Herness 进度树做深(A 节点信息深度 + C 可观测可干预 + B 轻量) ✅`,并在 §2.3 关键决策追加:GitHub 闭环(repo 记在 DevPlan 顶层、commit 挂工作日志、人工/MCP 回填、不接 webhook)、actor 由认证渠道推断。
- 在 §5 MCP 段标注三个工具已增强透传新字段。

- [ ] **Step 2: 检查点** — 文档与实现一致,无遗留"待实现"误标。

---

## 自检结果(Self-Review)

**Spec 覆盖:**
- §3 数据模型 → Task 1/2(DevPlan/Repo/Node/AcceptanceItem/Artifacts/LogEntry/Commit/Correction)✅
- GitHub 闭环(repo 顶层 + commit 挂 log + 回填)→ Task 1(repo/commit 字段)、Task 3(commit 进 log)、Task 8(MCP 上报)、Task 12/13(链接渲染)✅
- §4 C 轴(总览条/详情抽屉/纠偏)→ Task 12/13/14 ✅
- §5 B 轴(blocked 硬校验 / 三类软警告 / 自动日志 / actor 推断)→ Task 3(逻辑)、Task 5(actor 渠道)、Task 7(单测)✅
- §6 API/MCP 改动 → Task 4(端点)、Task 8(工具)✅
- §7 权限 seed → Task 6 ✅
- §8 兼容(旧 log 容错)→ 由 Jackson 宽松反序列化覆盖;新字段默认空集合,旧文档读取不报错 ✅
- §10 测试 → Task 7(后端单测)+ 各前端验证检查点 ✅

**占位符扫描:** 无 TBD/TODO;每个改动步骤都给了完整代码或精确改动位置。✅

**类型一致性核对:**
- 后端:`UpdateNodeRequest`/`UpdateResult`/`UpdateNodeResponse` 在 Task 2 定义,Task 3/4 一致使用;`actorOf` 读取 `CHANNEL_APIKEY`(Task 5 注入)一致 ✅
- 前端:`NodeUpdate`/`UpdateNodeResponse`/`AcceptanceItem`/`DevPlanRepo`/`DevPlanCommit` 在 Task 9/10 定义,Task 11/12/13/14 一致引用;`useDevPlan` 暴露 `updateNode/generateFromModules/leaveCorrection/markCorrectionResolved`,详情页与抽屉按此调用 ✅
- MCP:`create_dev_plan` body `{root_title, repo, nodes}` 与后端 `CreateDevPlanRequest` 字段名(snake_case)一致;`update_dev_plan_node` body 与 `UpdateNodeRequest` 一致 ✅

**已知简化(留自举阶段细化,符合 spec §9):** 验收勾选未单独留痕到 log;actor 纯按渠道推断;软警告文案为初版;关联文档链接指向 `/wiki?path=`(需确认 wiki 路由是否支持 query 定位,不支持则后续改为 path 参数)。
