package com.potato.techproposal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.potato.devplan.DevPlan;

import java.util.List;

/** 技术方案相关 DTO。 */
public final class TechProposalDtos {

    private TechProposalDtos() {}

    /** 创建技术方案请求(挂在某需求的某节点上)。 */
    public record CreateRequest(
            @JsonProperty("node_id") String nodeId,
            String title,
            String content,
            List<String> tags,
            @JsonProperty("mark_in_progress") Boolean markInProgress) {
    }

    /** 响应:技术方案 wiki 路径 + 更新后的节点。 */
    public record TechProposalResponse(
            @JsonProperty("proposal_path") String proposalPath,
            DevPlan.Node node) {
    }
}
