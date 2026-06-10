package com.potato.archnode;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目结构树节点(业务域驱动 L0–L4)。独立集合 + 物化路径(path),
 * 便于标签跨切面查询、.project.yaml 自动同步、节点生命周期管理。
 */
@Data
@Document("arch_nodes")
public class ArchNode {

    @Id
    private String id;

    private String projectId;
    private String parentId;      // 可空(顶层)
    private String path;          // 物化路径,项目内唯一,如 /用户域/认证上下文/登录模块

    private String layer;         // L0|L1|L2|L3|L4
    private String type;          // domain|context|module|component|service|page...
    private String title;
    private String description;

    private List<String> tags = new ArrayList<>();

    @JsonProperty("related_docs")
    private List<String> relatedDocs = new ArrayList<>();      // wiki path

    @JsonProperty("related_code")
    private List<String> relatedCode = new ArrayList<>();      // glob

    @JsonProperty("related_requirements")
    private List<String> relatedRequirements = new ArrayList<>();

    private String source = "manual";   // manual | sync(.project.yaml)
    private String repoId;               // sync 来源仓库
    private String status = "active";    // active | archived
    private int order;

    private Instant createdAt;
    private Instant updatedAt;
}
