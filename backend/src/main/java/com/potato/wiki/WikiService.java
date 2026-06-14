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
    private static final Set<String> VALID_CATEGORY = Set.of("doc", "asset", "standard", "experience", "runlog");
    private static final Set<String> VALID_KIND = Set.of("folder", "doc");

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

    public WikiPage create(String title, String path, String parentPath, String content, String category, List<String> tags, String kind, String userId) {
        if (title == null || title.isBlank() || path == null || path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题和路径必填");
        }
        String normalized = normalizePath(path);
        repository.findByPath(normalized).ifPresent(p -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "路径已存在: " + normalized);
        });
        // 不能在文档(doc)下建子项:父若为已存在的 doc 则拒绝(挡"文件下存文件";父是虚拟目录/folder/根则放行)
        String parent = parentOf(normalized);
        if (parent != null) {
            repository.findByPath(parent).ifPresent(pp -> {
                if ("doc".equals(effectiveKind(pp))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能在文档下创建子项,请先建文件夹: " + parent);
                }
            });
        }
        WikiPage page = new WikiPage();
        page.setProjectId(DEFAULT_PROJECT);
        page.setTitle(title);
        page.setPath(normalized);
        page.setParentPath(parentOf(normalized));
        page.setContent(content);
        page.setCategory(normalizeCategory(category));
        page.setKind(normalizeKind(kind));
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
        if (path != null && !path.isBlank()) {
            String normalized = normalizePath(path);
            if (!normalized.equals(page.getPath())) {
                repository.findByPath(normalized).ifPresent(p -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "路径已存在: " + normalized);
                });
                page.setPath(normalized);
                page.setParentPath(parentOf(normalized));
            }
        }
        if (content != null) page.setContent(content);
        if (category != null) page.setCategory(validateCategory(category));
        if (tags != null) page.setTags(tags);
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
        String normalized = normalizePath(path);
        return repository.findByPath(normalized)
                .map(existing -> update(existing.getId(), title, normalized, content, category, tags, parentPath, userId))
                .orElseGet(() -> create(title, normalized, parentPath, content, category, tags, "doc", userId));
    }

    /**
     * 把 fromPrefix 目录(及其下所有文档)整体移动/重命名到 toPrefix —— 物化路径前缀级联替换,像 mv 一个文件夹。
     * 目标路径与本次移动范围外的文档冲突则 409;不能移动到自身或其子目录下。
     */
    public List<WikiPage> moveDir(String fromPrefix, String toPrefix, String userId) {
        String from = normalizePath(fromPrefix);
        String to = normalizePath(toPrefix);
        if ("/".equals(from) || "/".equals(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "源/目标目录不能为空");
        }
        if (to.equals(from) || to.startsWith(from + "/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能移动到自身或其子目录下");
        }
        List<WikiPage> all = repository.findAllByOrderByPathAsc();
        List<WikiPage> affected = new ArrayList<>();
        for (WikiPage p : all) {
            String pp = p.getPath();
            if (pp != null && (pp.equals(from) || pp.startsWith(from + "/"))) affected.add(p);
        }
        if (affected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "目录不存在或为空: " + from);
        }
        Set<String> movingPaths = new java.util.HashSet<>();
        for (WikiPage p : affected) movingPaths.add(p.getPath());
        for (WikiPage p : affected) {
            String newPath = to + p.getPath().substring(from.length());
            for (WikiPage other : all) {
                if (!movingPaths.contains(other.getPath()) && newPath.equals(other.getPath())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "目标路径已存在: " + newPath);
                }
            }
        }
        Instant now = Instant.now();
        for (WikiPage p : affected) {
            String newPath = to + p.getPath().substring(from.length());
            p.setPath(newPath);
            p.setParentPath(parentOf(newPath));
            p.setUpdatedBy(userId);
            p.setUpdatedAt(now);
        }
        return repository.saveAll(affected);
    }

    /** 规范化物化路径:统一前导 /、单 / 分隔、去尾 /、trim 每段、丢弃空段。 */
    private String normalizePath(String raw) {
        if (raw == null) return "/";
        StringBuilder sb = new StringBuilder();
        for (String seg : raw.trim().split("/")) {
            String s = seg.trim();
            if (!s.isEmpty()) sb.append('/').append(s);
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    /** 父路径(path 去掉末段);顶层(如 /a)返回 null。 */
    private String parentOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx <= 0 ? null : path.substring(0, idx);
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

    /** 删除 wiki 页;返回被删的页(供调用方清理其 MinIO 资产)。不存在 → 404。 */
    public WikiPage delete(String id) {
        WikiPage page = get(id);
        repository.delete(page);
        return page;
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
            // 执行文档(runlog)是过程轨迹,不污染知识检索;仅显式 category=runlog 时返回
            if (filterCat == null && "runlog".equals(effectiveCategory(p))) continue;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 category: " + category + "(允许 doc/asset/standard/experience/runlog)");
        }
        return c;
    }

    /** 读取用:存量空值视为 doc。 */
    private String effectiveCategory(WikiPage p) {
        return (p.getCategory() == null || p.getCategory().isBlank()) ? DEFAULT_CATEGORY : p.getCategory();
    }

    /** 写入用 kind:null/blank → doc;非法 → 400。 */
    private String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) return "doc";
        String k = kind.trim();
        if (!VALID_KIND.contains(k)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 kind: " + kind + "(允许 folder/doc)");
        }
        return k;
    }

    /** 读取用 kind:存量空值视为 doc。 */
    private String effectiveKind(WikiPage p) {
        return (p.getKind() == null || p.getKind().isBlank()) ? "doc" : p.getKind();
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
