package com.potato.archgraph;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 架构图谱的模块节点(唯一节点实体)。区别于旧 ArchNode 分类树:
 * 模块即实质架构单元,依赖关系由 ArchEdge 表达,group 仅作 mermaid 视觉聚类。
 * 档案(docs)把需求/技术/经验索引到模块,跨模块文档以 scope 标注。
 */
@Data
@Document("arch_modules")
public class ArchModule {

    @Id
    private String id;

    private String projectId;
    private String key;          // 项目内唯一标识(如 permission、mcp-gateway),引用/关联都用它
    private String title;
    private String description;
    private String group;        // 子系统/粗分组名,仅用于 mermaid subgraph 聚类(非节点)

    @JsonProperty("impl_status")
    private String implStatus = "planned";   // planned | in_progress | done(模块自身状态,不聚合)

    @JsonProperty("related_code")
    private List<String> relatedCode = new ArrayList<>();   // glob

    private List<DocIndex> docs = new ArrayList<>();   // 档案:需求/技术/经验索引

    private int order;

    private Instant createdAt;
    private Instant updatedAt;

    /** 档案索引项。type 区分三类;scope 为涉及的模块 key 列表(跨模块文档 >1)。 */
    @Data
    public static class DocIndex {
        private String type;       // requirement | tech_doc | experience
        private String ref;        // 需求 id 或 wiki path
        private String title;      // 展示名(可选冗余)
        private List<String> scope = new ArrayList<>();   // 涉及模块 keys
    }
}
