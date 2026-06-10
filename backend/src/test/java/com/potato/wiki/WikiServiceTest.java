package com.potato.wiki;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikiServiceTest {

    @Mock
    WikiPageRepository repo;
    @Mock
    MongoTemplate mongoTemplate;

    WikiService service;

    @BeforeEach
    void setUp() {
        service = new WikiService(repo, mongoTemplate);
    }

    @Test
    void upsert_creates_when_path_absent() {
        when(repo.findByPath("/vue/toast")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.upsertByPath("/vue/toast", "Toast", "内容", List.of("toast"), null, "u1");
        assertThat(p.getPath()).isEqualTo("/vue/toast");
        assertThat(p.getVersion()).isEqualTo(1);
        assertThat(p.getTags()).contains("toast");
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
        assertThat(p.getContent()).isEqualTo("新内容");
    }
}
