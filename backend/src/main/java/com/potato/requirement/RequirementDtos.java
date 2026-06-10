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
        java.util.List<com.potato.common.DocLink> docLinks) {
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
