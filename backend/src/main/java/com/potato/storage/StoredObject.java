package com.potato.storage;

import java.time.Instant;

/** 对象存储里的一个对象(由 listObjects 返回的原始元数据)。 */
public record StoredObject(String objectKey, long size, Instant lastModified, String url) {
}
