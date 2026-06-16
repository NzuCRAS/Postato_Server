package com.potato.archgraph;

import java.util.List;

/** 架构图谱 CRUD 的请求 / 返回 DTO。 */
public class ArchGraphDtos {

    public record ModuleRequest(String key, String title, String description, String group,
                                String implStatus, List<String> relatedCode, Integer order) {
    }

    public record EdgeRequest(String from, String to, String kind, String label) {
    }

    /** 把一篇文档索引到一组模块(scope)。type ∈ requirement|tech_doc|experience。 */
    public record RelateDocRequest(String type, String ref, String title, List<String> scope) {
    }

    /** 整张图(模块 + 边),供前端/MCP 渲染。 */
    public record Graph(List<ArchModule> modules, List<ArchEdge> edges) {
    }
}
