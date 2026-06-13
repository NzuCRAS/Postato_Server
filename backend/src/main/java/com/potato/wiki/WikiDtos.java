package com.potato.wiki;

import java.util.List;

/** 创建/更新知识库文档请求体(create 时 title/path 必填,在 service 中校验) */
record WikiPageRequest(
        String title,
        String path,
        String parentPath,
        String content,
        String category,
        List<String> tags) {
}

/** 整目录移动/重命名请求:把 fromPrefix 子树整体迁到 toPrefix。 */
record MoveDirRequest(String fromPrefix, String toPrefix) {
}
