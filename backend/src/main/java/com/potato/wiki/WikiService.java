package com.potato.wiki;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class WikiService {

    private static final String DEFAULT_PROJECT = "default";
    static final String DEFAULT_CATEGORY = "doc";
    private static final Set<String> VALID_CATEGORY = Set.of("doc", "asset", "standard", "experience");

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

    public WikiPage create(String title, String path, String parentPath, String content, String category, List<String> tags, String userId) {
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
        page.setCategory(normalizeCategory(category));
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

    public WikiPage update(String id, String title, String path, String content, String category, List<String> tags, String parentPath, String userId) {
        WikiPage page = get(id);
        if (title != null) page.setTitle(title);
        if (path != null && !path.isBlank() && !path.equals(page.getPath())) {
            repository.findByPath(path).ifPresent(p -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "路径已存在: " + path);
            });
            page.setPath(path);
        }
        if (content != null) page.setContent(content);
        if (category != null) page.setCategory(validateCategory(category));
        if (tags != null) page.setTags(tags);
        if (parentPath != null) page.setParentPath(parentPath);
        page.setVersion(page.getVersion() + 1);
        page.setUpdatedBy(userId);
        page.setUpdatedAt(Instant.now());
        return repository.save(page);
    }

    /** 按 path upsert:命中则更新、否则创建。供知识沉淀 / 技术方案复用。 */
    public WikiPage upsertByPath(String path, String title, String content, String category, List<String> tags, String parentPath, String userId) {
        if (path == null || path.isBlank() || title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题和路径必填");
        }
        return repository.findByPath(path)
                .map(existing -> update(existing.getId(), title, path, content, category, tags, parentPath, userId))
                .orElseGet(() -> create(title, path, parentPath, content, category, tags, userId));
    }

    /** 给页追加一个资产(已上传到 MinIO 后调用)。 */
    public WikiPage addAsset(String id, WikiPage.Asset asset) {
        WikiPage page = get(id);
        page.getAssets().add(asset);
        page.setUpdatedAt(Instant.now());
        return repository.save(page);
    }

    /** 按 objectKey 移除页上的资产记录(MinIO 中的对象删除由调用方处理)。 */
    public WikiPage removeAsset(String id, String objectKey) {
        WikiPage page = get(id);
        page.getAssets().removeIf(a -> a.getObjectKey() != null && a.getObjectKey().equals(objectKey));
        page.setUpdatedAt(Instant.now());
        return repository.save(page);
    }

    /**
     * 多模式检索。VECTOR 预留未实现(报 501)。默认排除 tmp 标签页(临时方案不污染知识结果)。
     * category 非空时按资产分类过滤(doc/asset/standard;存量空值视为 doc)。
     * 数据量小,在 Java 内分词过滤;量大后可换 mongo 文本索引 / 向量库。
     */
    public List<WikiPage> search(String q, MatchMode mode, boolean includeTmp, String category) {
        MatchMode m = mode != null ? mode : MatchMode.FUZZY;
        if (m == MatchMode.VECTOR) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "向量检索尚未实现(RAG 预留)");
        }
        String filterCat = (category == null || category.isBlank()) ? null : category.trim();
        String query = q == null ? "" : q.trim().toLowerCase();
        List<WikiPage> result = new ArrayList<>();
        for (WikiPage p : repository.findAllByOrderByPathAsc()) {
            if (!includeTmp && p.getTags() != null && p.getTags().contains("tmp")) continue;
            if (filterCat != null && !filterCat.equals(effectiveCategory(p))) continue;
            if (query.isEmpty() || matches(p, query, m)) result.add(p);
        }
        return result;
    }

    /** 写入用:null/blank → 默认 doc;非法值 → 400。 */
    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return DEFAULT_CATEGORY;
        return validateCategory(category);
    }

    /** 校验受控分类值,合法则返回 trim 后的值,否则 400。 */
    private String validateCategory(String category) {
        String c = category.trim();
        if (!VALID_CATEGORY.contains(c)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 category: " + category + "(允许 doc/asset/standard)");
        }
        return c;
    }

    /** 读取用:存量空值视为 doc。 */
    private String effectiveCategory(WikiPage p) {
        return (p.getCategory() == null || p.getCategory().isBlank()) ? DEFAULT_CATEGORY : p.getCategory();
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
