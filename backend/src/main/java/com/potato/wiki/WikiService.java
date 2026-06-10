package com.potato.wiki;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class WikiService {

    private static final String DEFAULT_PROJECT = "default";

    private final WikiPageRepository repository;

    public WikiService(WikiPageRepository repository) {
        this.repository = repository;
    }

    public List<WikiPage> list() {
        return repository.findAllByOrderByPathAsc();
    }

    public WikiPage get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
    }

    public WikiPage create(String title, String path, String parentPath, String content, List<String> tags, String userId) {
        if (title == null || title.isBlank() || path == null || path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题和路径必填");
        }
        repository.findByPath(path).ifPresent(p -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "路径已存在: " + path);
        });
        WikiPage page = new WikiPage();
        page.setProjectId(DEFAULT_PROJECT);
        page.setTitle(title);
        page.setPath(path);
        page.setParentPath(parentPath);
        page.setContent(content);
        if (tags != null) page.setTags(tags);
        page.setStatus("published");
        page.setVersion(1);
        page.setCreatedBy(userId);
        page.setUpdatedBy(userId);
        Instant now = Instant.now();
        page.setCreatedAt(now);
        page.setUpdatedAt(now);
        return repository.save(page);
    }

    public WikiPage update(String id, String title, String content, List<String> tags, String parentPath, String userId) {
        WikiPage page = get(id);
        if (title != null) page.setTitle(title);
        if (content != null) page.setContent(content);
        if (tags != null) page.setTags(tags);
        if (parentPath != null) page.setParentPath(parentPath);
        page.setVersion(page.getVersion() + 1);
        page.setUpdatedBy(userId);
        page.setUpdatedAt(Instant.now());
        return repository.save(page);
    }

    /** 按 path upsert:命中则更新、否则创建。供知识沉淀 / 技术方案复用。 */
    public WikiPage upsertByPath(String path, String title, String content, List<String> tags, String parentPath, String userId) {
        if (path == null || path.isBlank() || title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题和路径必填");
        }
        return repository.findByPath(path)
                .map(existing -> update(existing.getId(), title, content, tags, parentPath, userId))
                .orElseGet(() -> create(title, path, parentPath, content, tags, userId));
    }

    /**
     * 多模式检索。VECTOR 预留未实现(报 501)。默认排除 tmp 标签页(临时方案不污染知识结果)。
     * 数据量小,在 Java 内分词过滤;量大后可换 mongo 文本索引 / 向量库。
     */
    public List<WikiPage> search(String q, MatchMode mode, boolean includeTmp) {
        MatchMode m = mode != null ? mode : MatchMode.FUZZY;
        if (m == MatchMode.VECTOR) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "向量检索尚未实现(RAG 预留)");
        }
        String query = q == null ? "" : q.trim().toLowerCase();
        List<WikiPage> result = new ArrayList<>();
        for (WikiPage p : repository.findAllByOrderByPathAsc()) {
            if (!includeTmp && p.getTags() != null && p.getTags().contains("tmp")) continue;
            if (query.isEmpty() || matches(p, query, m)) result.add(p);
        }
        return result;
    }

    private boolean matches(WikiPage p, String query, MatchMode m) {
        String hay = haystack(p, m);
        if (m == MatchMode.EXACT) {
            return hay.contains(query);
        }
        for (String term : query.split("\\s+")) {
            if (!term.isEmpty() && !hay.contains(term)) return false;
        }
        return true;
    }

    /** 按模式拼出参与匹配的小写文本(EXACT 用全字段)。 */
    private String haystack(WikiPage p, MatchMode m) {
        boolean useTitle = m == MatchMode.FUZZY || m == MatchMode.EXACT || m == MatchMode.TITLE;
        boolean useContent = m == MatchMode.FUZZY || m == MatchMode.EXACT || m == MatchMode.CONTENT;
        boolean useTags = m == MatchMode.FUZZY || m == MatchMode.EXACT || m == MatchMode.TAG;
        StringBuilder sb = new StringBuilder();
        if (useTitle && p.getTitle() != null) sb.append(p.getTitle()).append('\n');
        if (useContent && p.getContent() != null) sb.append(p.getContent()).append('\n');
        if (useTags && p.getTags() != null) sb.append(String.join(" ", p.getTags())).append('\n');
        return sb.toString().toLowerCase();
    }
}
