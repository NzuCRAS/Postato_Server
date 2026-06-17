package com.potato.wiki;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikiServiceTest {

    @Mock
    WikiPageRepository repo;

    WikiService service;

    @BeforeEach
    void setUp() {
        service = new WikiService(repo);
    }

    private WikiPage page(String title, String content, List<String> tags) {
        WikiPage p = new WikiPage();
        p.setTitle(title);
        p.setContent(content);
        p.setTags(tags);
        return p;
    }

    private List<String> titles(List<WikiPage> pages) {
        return pages.stream().map(WikiPage::getTitle).toList();
    }

    // ---- upsert ----

    @Test
    void upsert_creates_when_path_absent() {
        when(repo.findByPath("/vue/toast")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.upsertByPath("/vue/toast", "Toast", "内容", null, List.of("toast"), null, "u1");
        assertThat(p.getPath()).isEqualTo("/vue/toast");
        assertThat(p.getVersion()).isEqualTo(1);
    }

    @Test
    void upsert_updates_when_path_exists() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        existing.setPath("/vue/toast");
        existing.setVersion(1);
        when(repo.findByPath("/vue/toast")).thenReturn(Optional.of(existing));
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.upsertByPath("/vue/toast", "Toast v2", "新内容", null, null, null, "u2");
        assertThat(p.getVersion()).isEqualTo(2);
        assertThat(p.getTitle()).isEqualTo("Toast v2");
    }

    // ---- search 多模式 ----

    private void stubPages() {
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(
                page("Herness 开发契约", "Herness 流程说明", List.of("agent", "herness")),
                page("Toast 组件", "vue toast 实现", List.of("vue", "toast")),
                page("临时方案", "草稿内容", List.of("tech-proposal", "tmp"))
        ));
    }

    @Test
    void fuzzy_tokenized_and_matches_noncontiguous() {
        stubPages();
        // "herness 契约" 两词非连续,fuzzy 应命中"Herness 开发契约"
        List<WikiPage> r = service.search("herness 契约", MatchMode.FUZZY, false, null);
        assertThat(titles(r)).containsExactly("Herness 开发契约");
    }

    @Test
    void exact_requires_contiguous() {
        stubPages();
        assertThat(service.search("herness 契约", MatchMode.EXACT, false, null)).isEmpty();
        assertThat(titles(service.search("toast 组件", MatchMode.EXACT, false, null))).containsExactly("Toast 组件");
    }

    @Test
    void tag_mode_matches_only_tags() {
        stubPages();
        // "vue" 在 Toast 页的 tags;在标题/内容里没有别的页含 vue 标签
        assertThat(titles(service.search("vue", MatchMode.TAG, false, null))).containsExactly("Toast 组件");
        // "流程" 只在 content,TAG 模式查不到
        assertThat(service.search("流程", MatchMode.TAG, false, null)).isEmpty();
    }

    @Test
    void tmp_excluded_by_default_but_included_when_flag() {
        stubPages();
        assertThat(service.search("草稿", MatchMode.FUZZY, false, null)).isEmpty();
        assertThat(titles(service.search("草稿", MatchMode.FUZZY, true, null))).containsExactly("临时方案");
    }

    @Test
    void vector_mode_is_not_implemented() {
        assertThatThrownBy(() -> service.search("x", MatchMode.VECTOR, false, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("向量");
    }

    // ---- category ----

    @Test
    void create_defaults_category_to_doc_when_null() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("T", "/p", null, "c", null, null, null, "u");
        assertThat(p.getCategory()).isEqualTo("doc");
    }

    @Test
    void create_rejects_invalid_category() {
        assertThatThrownBy(() -> service.create("T", "/p", null, "c", "bogus", null, null, "u"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("category");
    }

    @Test
    void create_accepts_experience_category() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("T", "/p", null, "c", "experience", null, null, "u");
        assertThat(p.getCategory()).isEqualTo("experience");
    }

    @Test
    void search_filters_by_category_treating_null_as_doc() {
        WikiPage exp = page("经验页", "缓存雪崩处理", List.of("cache"));
        exp.setCategory("experience");
        WikiPage plain = page("普通页", "缓存说明", List.of("cache")); // category null → 视为 doc
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(exp, plain));
        assertThat(titles(service.search("缓存", MatchMode.FUZZY, false, "experience"))).containsExactly("经验页");
        assertThat(titles(service.search("缓存", MatchMode.FUZZY, false, "doc"))).containsExactly("普通页");
    }

    @Test
    void create_accepts_runlog_category() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("执行文档", "/runs/r1/run-1", null, "轨迹", "runlog", null, null, "u");
        assertThat(p.getCategory()).isEqualTo("runlog");
    }

    @Test
    void search_excludes_runlog_by_default_but_returns_when_filtered() {
        WikiPage run = page("执行文档", "缓存相关执行轨迹", List.of("run"));
        run.setCategory("runlog");
        WikiPage doc = page("普通页", "缓存说明", List.of("cache"));
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(run, doc));
        // 默认(category=null)不返回 runlog,避免执行文档污染检索
        assertThat(titles(service.search("缓存", MatchMode.FUZZY, false, null))).containsExactly("普通页");
        // 显式 category=runlog 才返回
        assertThat(titles(service.search("缓存", MatchMode.FUZZY, false, "runlog"))).containsExactly("执行文档");
    }

    // ---- update 改 path(晋升用) ----

    @Test
    void update_moves_path_when_changed() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        existing.setPath("/tech-proposals/r1/node_1");
        existing.setVersion(1);
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.findByPath("/experience/cache-avalanche")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.update("id1", "经验", "/experience/cache-avalanche", "正文",
                "experience", List.of("cache"), "/experience", "u");
        assertThat(p.getPath()).isEqualTo("/experience/cache-avalanche");
        assertThat(p.getCategory()).isEqualTo("experience");
    }

    @Test
    void update_rejects_path_conflict() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        existing.setPath("/tech-proposals/r1/node_1");
        WikiPage other = new WikiPage();
        other.setId("id2");
        other.setPath("/experience/taken");
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.findByPath("/experience/taken")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.update("id1", "x", "/experience/taken", null, null, null, null, "u"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("路径已存在");
    }

    // ---- 目录移动 / 路径规范化 ----

    @Test
    void create_normalizes_path_and_parent() {
        when(repo.findByPath("/vue/toast")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("Toast", "vue//toast/", "ignored-parent", "c", null, null, null, "u");
        assertThat(p.getPath()).isEqualTo("/vue/toast");
        assertThat(p.getParentPath()).isEqualTo("/vue");
    }

    @Test
    void moveDir_cascades_prefix() {
        WikiPage a = new WikiPage();
        a.setPath("/dev/a");
        WikiPage b = new WikiPage();
        b.setPath("/dev/a/b");
        WikiPage other = new WikiPage();
        other.setPath("/other");
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(a, b, other));
        when(repo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        List<WikiPage> moved = service.moveDir("/dev/a", "/docs/x", "u");
        assertThat(moved).extracting(WikiPage::getPath).containsExactlyInAnyOrder("/docs/x", "/docs/x/b");
        assertThat(a.getParentPath()).isEqualTo("/docs");
        assertThat(b.getParentPath()).isEqualTo("/docs/x");
        assertThat(other.getPath()).isEqualTo("/other");
    }

    @Test
    void moveDir_rejects_target_conflict() {
        WikiPage a = new WikiPage();
        a.setPath("/dev/a");
        WikiPage taken = new WikiPage();
        taken.setPath("/docs/x");
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(a, taken));
        assertThatThrownBy(() -> service.moveDir("/dev/a", "/docs/x", "u"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void moveDir_rejects_into_own_subtree() {
        assertThatThrownBy(() -> service.moveDir("/dev/a", "/dev/a/sub", "u"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("子目录");
    }

    // ---- 目录删除(级联前缀) ----

    @Test
    void deleteDir_cascades_prefix_and_keeps_others() {
        WikiPage a = new WikiPage();
        a.setPath("/dev/a");
        WikiPage b = new WikiPage();
        b.setPath("/dev/a/b");
        WikiPage other = new WikiPage();
        other.setPath("/other");
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(a, b, other));
        List<WikiPage> deleted = service.deleteDir("/dev/a");
        assertThat(deleted).extracting(WikiPage::getPath).containsExactlyInAnyOrder("/dev/a", "/dev/a/b");
        // 只删命中前缀的页,/other 不在删除集合
        verify(repo).deleteAll(deleted);
    }

    @Test
    void deleteDir_throws_404_when_empty() {
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of());
        assertThatThrownBy(() -> service.deleteDir("/nope"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void deleteDir_rejects_empty_prefix() {
        assertThatThrownBy(() -> service.deleteDir("/"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能为空");
    }

    // ---- 相关性排序检索(searchRanked,SOP 节点注入用) ----

    @Test
    void searchRanked_or_semantics_recalls_partial_matches() {
        // 旧 search 多词 AND 会因"无文档同时含三词"召回空;searchRanked 用 OR + 打分应都召回
        WikiPage a = page("Toast 组件", "vue 实现", List.of("vue"));
        a.setCategory("asset");
        WikiPage b = page("HTTP 工具", "请求封装", List.of("util"));
        b.setCategory("asset");
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(a, b));
        List<WikiPage> r = service.searchRanked("组件 工具 复用", "asset", 5);
        assertThat(titles(r)).containsExactlyInAnyOrder("Toast 组件", "HTTP 工具");
    }

    @Test
    void searchRanked_ranks_title_hits_above_content_hits() {
        WikiPage titleHit = page("缓存方案", "其他内容", List.of());
        titleHit.setCategory("experience");
        WikiPage contentHit = page("随笔", "讲了缓存的注意事项", List.of());
        contentHit.setCategory("experience");
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(contentHit, titleHit)); // 顺序故意反着
        List<WikiPage> r = service.searchRanked("缓存", "experience", 5);
        assertThat(titles(r)).containsExactly("缓存方案", "随笔"); // 标题命中(权重高)排前
    }

    @Test
    void searchRanked_empty_query_returns_empty() {
        assertThat(service.searchRanked("   ", "asset", 5)).isEmpty();
    }

    @Test
    void searchRanked_respects_topK_category_and_excludes_tmp() {
        WikiPage a = page("缓存一", "缓存", List.of());
        a.setCategory("experience");
        WikiPage b = page("缓存二", "缓存", List.of());
        b.setCategory("experience");
        WikiPage c = page("缓存三", "缓存", List.of());
        c.setCategory("experience");
        WikiPage other = page("缓存四", "缓存", List.of()); // 异类分类应被过滤
        other.setCategory("asset");
        WikiPage tmp = page("缓存五", "缓存", List.of("tmp")); // tmp 应排除
        tmp.setCategory("experience");
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(a, b, c, other, tmp));
        List<WikiPage> r = service.searchRanked("缓存", "experience", 2);
        assertThat(r).hasSize(2); // TopK 截断
        assertThat(titles(r)).doesNotContain("缓存四", "缓存五");
    }

    // ---- assets ----

    @Test
    void addAsset_appends_and_saves() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage.Asset a = new WikiPage.Asset();
        a.setObjectKey("wiki/id1/x-demo.html");
        a.setName("demo.html");
        WikiPage p = service.addAsset("id1", a);
        assertThat(p.getAssets()).hasSize(1);
        assertThat(p.getAssets().get(0).getObjectKey()).isEqualTo("wiki/id1/x-demo.html");
    }

    @Test
    void removeAsset_drops_matching_key() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        WikiPage.Asset a = new WikiPage.Asset();
        a.setObjectKey("wiki/id1/x-demo.html");
        existing.getAssets().add(a);
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.removeAsset("id1", "wiki/id1/x-demo.html");
        assertThat(p.getAssets()).isEmpty();
    }

    // ---- delete ----

    @Test
    void delete_removes_existing_page() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        WikiPage p = service.delete("id1");
        assertThat(p.getId()).isEqualTo("id1");
        verify(repo).delete(existing);
    }

    @Test
    void delete_throws_404_when_missing() {
        when(repo.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete("nope"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不存在");
    }

    // ---- kind folder/doc ----

    @Test
    void create_defaults_kind_to_doc() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("X", "/x", null, "c", null, null, null, "u");
        assertThat(p.getKind()).isEqualTo("doc");
    }

    @Test
    void create_folder_kind() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("F", "/f", null, "desc", null, null, "folder", "u");
        assertThat(p.getKind()).isEqualTo("folder");
    }

    @Test
    void create_allows_doc_under_folder() {
        WikiPage folder = new WikiPage();
        folder.setPath("/f");
        folder.setKind("folder");
        when(repo.findByPath("/f/b")).thenReturn(Optional.empty());
        when(repo.findByPath("/f")).thenReturn(Optional.of(folder));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("B", "/f/b", null, "c", null, null, "doc", "u");
        assertThat(p.getPath()).isEqualTo("/f/b");
    }

    @Test
    void create_rejects_doc_under_doc() {
        WikiPage parentDoc = new WikiPage();
        parentDoc.setPath("/a");
        parentDoc.setKind("doc");
        when(repo.findByPath("/a/b")).thenReturn(Optional.empty());
        when(repo.findByPath("/a")).thenReturn(Optional.of(parentDoc));
        assertThatThrownBy(() -> service.create("B", "/a/b", null, "c", null, null, "doc", "u"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("文档下");
    }

    // ---- getByPath(MCP resources 按 path 取页) ----

    @Test
    void getByPath_returns_normalized_match() {
        WikiPage p = new WikiPage();
        p.setPath("/agent/herness-contract");
        when(repo.findByPath("/agent/herness-contract")).thenReturn(Optional.of(p));
        WikiPage r = service.getByPath("agent//herness-contract/"); // 规范化后匹配
        assertThat(r.getPath()).isEqualTo("/agent/herness-contract");
    }

    @Test
    void getByPath_throws_404_when_missing() {
        when(repo.findByPath("/nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByPath("/nope"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不存在");
    }
}
