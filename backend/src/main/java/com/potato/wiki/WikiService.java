package com.potato.wiki;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class WikiService {

    private static final String DEFAULT_PROJECT = "default";

    private final WikiPageRepository repository;
    private final MongoTemplate mongoTemplate;

    public WikiService(WikiPageRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
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

    /** 文本搜索:title/content/tags 包含关键词(regex,大小写不敏感,中文可用) */
    public List<WikiPage> search(String q) {
        if (q == null || q.isBlank()) {
            return list();
        }
        String regex = ".*" + Pattern.quote(q) + ".*";
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("title").regex(regex, "i"),
                Criteria.where("content").regex(regex, "i"),
                Criteria.where("tags").regex(regex, "i")
        );
        return mongoTemplate.find(Query.query(criteria), WikiPage.class);
    }
}
