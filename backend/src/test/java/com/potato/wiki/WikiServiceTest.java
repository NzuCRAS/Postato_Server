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
        WikiPage p = service.create("T", "/p", null, "c", null, null, "u");
        assertThat(p.getCategory()).isEqualTo("doc");
    }

    @Test
    void create_rejects_invalid_category() {
        assertThatThrownBy(() -> service.create("T", "/p", null, "c", "bogus", null, "u"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("category");
    }

    @Test
    void create_accepts_experience_category() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("T", "/p", null, "c", "experience", null, "u");
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
        WikiPage p = service.create("Toast", "vue//toast/", "ignored-parent", "c", null, null, "u");
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
}
