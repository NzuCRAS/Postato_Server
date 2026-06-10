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
