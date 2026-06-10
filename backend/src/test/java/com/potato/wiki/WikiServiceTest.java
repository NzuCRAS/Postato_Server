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
        WikiPage p = service.upsertByPath("/vue/toast", "Toast", "内容", List.of("toast"), null, "u1");
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
        WikiPage p = service.upsertByPath("/vue/toast", "Toast v2", "新内容", null, null, "u2");
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
        List<WikiPage> r = service.search("herness 契约", MatchMode.FUZZY, false);
        assertThat(titles(r)).containsExactly("Herness 开发契约");
    }

    @Test
    void exact_requires_contiguous() {
        stubPages();
        assertThat(service.search("herness 契约", MatchMode.EXACT, false)).isEmpty();
        assertThat(titles(service.search("toast 组件", MatchMode.EXACT, false))).containsExactly("Toast 组件");
    }

    @Test
    void tag_mode_matches_only_tags() {
        stubPages();
        // "vue" 在 Toast 页的 tags;在标题/内容里没有别的页含 vue 标签
        assertThat(titles(service.search("vue", MatchMode.TAG, false))).containsExactly("Toast 组件");
        // "流程" 只在 content,TAG 模式查不到
        assertThat(service.search("流程", MatchMode.TAG, false)).isEmpty();
    }

    @Test
    void tmp_excluded_by_default_but_included_when_flag() {
        stubPages();
        assertThat(service.search("草稿", MatchMode.FUZZY, false)).isEmpty();
        assertThat(titles(service.search("草稿", MatchMode.FUZZY, true))).containsExactly("临时方案");
    }

    @Test
    void vector_mode_is_not_implemented() {
        assertThatThrownBy(() -> service.search("x", MatchMode.VECTOR, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("向量");
    }
}
