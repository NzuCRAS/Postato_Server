package com.potato.asset;

import java.time.Instant;
import java.util.List;

/** 资产库视图:一个 OSS 对象 + 交叉引用出的引用页 + 孤儿标记。 */
public record AssetView(
        String objectKey,
        String name,
        String url,
        String contentType,
        long size,
        Instant lastModified,
        List<PageRef> referencingPages,
        boolean orphan) {

    /** 引用了该资产的 wiki 页(精简引用)。 */
    public record PageRef(String id, String title, String path) {
    }
}
