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

    /** 在父节点下追加子节点请求 */
    public record AddNodesRequest(List<NodeInput> nodes) {
    }

    /** 重置(入档)请求 */
    public record ResetRequest(String reason) {
    }

    /** 兜底:更新节点时 artifacts 也可接受松散 Map(预留,本轮前端用强类型) */
    public record LooseArtifacts(Map<String, Object> fields) {
    }
}
