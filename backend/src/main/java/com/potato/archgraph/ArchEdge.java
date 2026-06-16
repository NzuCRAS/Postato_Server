package com.potato.archgraph;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** 架构图谱的依赖边(有向)。from/to 为模块 key;kind 表达依赖类型。 */
@Data
@Document("arch_edges")
public class ArchEdge {

    @Id
    private String id;

    private String projectId;
    private String from;     // 模块 key
    private String to;       // 模块 key
    private String kind;     // calls | depends | data_flow | auth ...
    private String label;    // 可选说明

    private Instant createdAt;
    private Instant updatedAt;
}
