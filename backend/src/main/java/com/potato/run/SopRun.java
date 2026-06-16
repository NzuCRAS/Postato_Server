package com.potato.run;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SOP 执行工作流实例(按需求维护)。平台驱动 LLM 逐步走 SOP:每步注入文档、实时留痕。
 * 与 dev_plan 分层不复制:Run 证「流程合规 + 文档注入 + 忽略原因」,dev_plan 证「工作产物」。
 */
@Data
@Document("sop_runs")
public class SopRun {

    @Id
    private String id;

    private String reqId;
    private String projectId;
    private String tier;

    private String status = "running";   // running | finished | aborted

    @JsonProperty("current_step_index")
    private int currentStepIndex;

    private List<RunStep> steps = new ArrayList<>();

    @JsonProperty("runlog_path")
    private String runlogPath;            // finish 后回写的 wiki path

    private Instant createdAt;
    private Instant updatedAt;

    /** 工作流的一步。 */
    @Data
    public static class RunStep {
        private String key;
        private String title;
        private String status = "pending";    // pending | done | skipped
        private String note;                  // 本步执行结果(LLM 提供)
        @JsonProperty("skip_reason")
        private String skipReason;            // skipped 必填
        @JsonProperty("injected_docs")
        private List<String> injectedDocs = new ArrayList<>();   // 该步平台注入过的文档 ref
        private Instant at;
    }
}
