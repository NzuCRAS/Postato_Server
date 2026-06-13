package com.potato.requirement;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** 创建/更新需求请求体(create 时 title 必填,在 service 中校验) */
record RequirementRequest(
        String title,
        String descriptionMd,
        Structured structured,
        String status,
        String projectId,
        java.util.List<com.potato.common.DocLink> docLinks,
        String type,
        String tier) {
}

/** 状态变更请求体 */
record UpdateStatusRequest(
        @NotBlank String status) {
}

/** 列表项摘要 */
record RequirementSummary(
        String id,
        String title,
        String status,
        int version,
        Instant updatedAt) {
}

/** ⑩ 需求关联结构树节点请求体(回标用) */
record ArchLinkRequest(java.util.List<ArchLink> links) {
}

/** 单条关联:arch 节点物化路径 + 可选回标的 impl_status */
record ArchLink(
        @com.fasterxml.jackson.annotation.JsonProperty("arch_path") String archPath,
        @com.fasterxml.jackson.annotation.JsonProperty("impl_status") String implStatus) {
}

/** 关联结果:更新后的关联列表 + 软警告 */
record RelateArchResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("related_arch_nodes") java.util.List<String> relatedArchNodes,
        java.util.List<String> warnings) {
}
