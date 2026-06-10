package com.potato.project;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 项目相关请求体。 */
public final class ProjectDtos {

    private ProjectDtos() {}

    public record ProjectRequest(String name, String descriptionMd) {
    }

    public record RepoRequest(
            String name,
            String url,
            String provider,
            @JsonProperty("default_branch") String defaultBranch) {
    }
    // 新增/删除 docLink 直接用 com.potato.common.DocLink 作为请求体
}
