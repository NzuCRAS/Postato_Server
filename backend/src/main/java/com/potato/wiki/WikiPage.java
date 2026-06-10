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
    private List<String> tags = new ArrayList<>();
    private String status = "published"; // draft | published | archived
    private int version;

    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
