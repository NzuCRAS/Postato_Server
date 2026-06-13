package com.potato.requirement;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document("requirements")
public class Requirement {

    @Id
    private String id;

    private String projectId;   // 预留:第一切片为默认值 "default"
    private String repoId;      // 预留:可空,多仓库时使用

    private String title;
    private String descriptionMd;
    private String sourceHtml;  // 预留:第一切片不渲染 HTML 沙箱

    private String status;      // draft | clarifying | confirmed | deprecated
    private int version;

    /** 需求分类(描述用):feature 增量 / improvement 修改优化 / bugfix 维护与 bug 修复 */
    private String type;
    /** 复杂度档(创建时选,**仅供参考**——建议 potato 流程强度,非硬门):Large / Medium / Small */
    private String tier;

    private Structured structured = new Structured();

    private com.potato.devplan.DevPlan devPlan;

    /** 已重置入档的历史进度树(不删除,保留日志供日后排查) */
    private java.util.List<com.potato.devplan.DevPlan> archivedDevPlans = new java.util.ArrayList<>();

    /** 关联到知识库的设计/规范/效果参考文档 */
    private java.util.List<com.potato.common.DocLink> docLinks = new java.util.ArrayList<>();

    /** 该需求落地到的结构树节点(物化路径),与 ArchNode.related_requirements 双向(⑩ 需求↔结构树联动) */
    @com.fasterxml.jackson.annotation.JsonProperty("related_arch_nodes")
    private java.util.List<String> relatedArchNodes = new java.util.ArrayList<>();

    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
