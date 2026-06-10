package com.potato.wiki;

import java.util.List;

/** 创建/更新知识库文档请求体(create 时 title/path 必填,在 service 中校验) */
record WikiPageRequest(
        String title,
        String path,
        String parentPath,
        String content,
        List<String> tags) {
}
