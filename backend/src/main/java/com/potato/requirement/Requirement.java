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

    private Structured structured = new Structured();

    private com.potato.devplan.DevPlan devPlan;

    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
