package com.potato.wiki;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 知识检索匹配模式(接口 / MCP 工具均按此枚举写明):
 * - FUZZY  : 分词 AND,跨 title+content+tags 子串命中(默认)
 * - EXACT  : 整个查询作为连续子串,跨 title+content+tags 命中
 * - TAG    : 分词 AND,仅匹配 tags
 * - CONTENT: 分词 AND,仅匹配 content
 * - TITLE  : 分词 AND,仅匹配 title
 * - VECTOR : 向量相似度检索(预留,尚未实现)
 */
public enum MatchMode {
    FUZZY, EXACT, TAG, CONTENT, TITLE, VECTOR;

    /** 接受小写/混合大小写字符串(MCP 传 "fuzzy");空 → FUZZY;非法 → 400。 */
    @JsonCreator
    public static MatchMode fromValue(String v) {
        if (v == null || v.isBlank()) return FUZZY;
        try {
            return MatchMode.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 match_mode: " + v);
        }
    }
}
