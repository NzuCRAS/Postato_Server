package com.potato.requirement;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化需求 —— 对 MCP/AI 暴露的领域契约。
 * 字段用 snake_case(与设计文档、MCP Tool 约定一致)。
 */
@Data
public class Structured {

    @JsonProperty("user_stories")
    private List<String> userStories = new ArrayList<>();

    private List<Module> modules = new ArrayList<>();

    @JsonProperty("interaction_flow")
    private String interactionFlow;

    @JsonProperty("ambiguous_points")
    private List<String> ambiguousPoints = new ArrayList<>();

    @Data
    public static class Module {
        private String name;
        private String description;

        @JsonProperty("acceptance_criteria")
        private List<String> acceptanceCriteria = new ArrayList<>();

        @JsonProperty("ui_states")
        private List<String> uiStates = new ArrayList<>();

        @JsonProperty("related_assets")
        private List<String> relatedAssets = new ArrayList<>();
    }
}
