package com.potato.common;

import lombok.Data;

/** 关联到知识库文档的链接(项目/需求共用)。 */
@Data
public class DocLink {
    private String type;   // design | standard | reference
    private String title;
    private String path;   // wiki path
}
