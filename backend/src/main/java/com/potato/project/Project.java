package com.potato.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.potato.common.DocLink;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目 —— 平台最大粒度。含多代码仓库、关联文档(与知识库建索引);
 * 结构树节点单独存 arch_nodes 集合(按 projectId 关联),不内嵌于此。
 */
@Data
@Document("projects")
public class Project {

    @Id
    private String id;

    private String name;
    private String descriptionMd;

    private List<Repo> repos = new ArrayList<>();
    private List<DocLink> docLinks = new ArrayList<>();

    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    public static class Repo {
        private String id;            // repo_xxxx
        private String name;
        private String url;
        private String provider;
        @JsonProperty("default_branch")
        private String defaultBranch;
    }
}
