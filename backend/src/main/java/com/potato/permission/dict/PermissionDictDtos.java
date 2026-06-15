package com.potato.permission.dict;

/** 字典 CRUD 请求 DTO。 */
public class PermissionDictDtos {

    public record CreateDefRequest(String key, String label, String description) {
    }

    public record UpdateDefRequest(String label, String description) {
    }
}
