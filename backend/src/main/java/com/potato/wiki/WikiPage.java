package com.potato.wiki;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document("wiki_pages")
public class WikiPage {

    @Id
    private String id;

    private String projectId;

    private String title;
    private String path;        // 物化路径,如 /development/code-style/react(应用层保证唯一)
    private String parentPath;  // 父路径,可空(根节点)
    private String content;     // Markdown
    private String category;    // doc(默认) | asset(可复用资产) | standard(代码规范);存量空值读作 doc
    private String kind;        // folder | doc;存量空值读作 doc。folder=容器(content 当描述)、doc=叶子
    private List<String> tags = new ArrayList<>();
    private String status = "published"; // draft | published | archived
    private int version;

    private List<Asset> assets = new ArrayList<>(); // 挂载在本页的 OSS 资产(demo/图片/代码包)

    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    public static class Asset {
        private String name;        // 显示名(原文件名)
        private String objectKey;   // MinIO 中的 key,如 wiki/{pageId}/{uuid}-{filename}
        private String url;         // 浏览器可访问 URL(开发期公开读直拼)
        private String contentType; // MIME
        private long size;          // 字节
        private Instant uploadedAt; // 上传时间
    }
}
