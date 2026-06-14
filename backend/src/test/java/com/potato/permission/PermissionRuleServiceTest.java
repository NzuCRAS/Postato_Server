package com.potato.permission;

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
class PermissionRuleServiceTest {

    @Mock
    PermissionRuleRepository repo;

    PermissionRuleService service;

    @BeforeEach
    void setUp() {
        service = new PermissionRuleService(repo);
    }

    private PermissionRule rule(String id, String resource, String action, List<String> fns) {
        PermissionRule r = new PermissionRule();
        r.setId(id);
        r.setResource(resource);
        r.setAction(action);
        r.setRequiredFunctions(fns);
        return r;
    }

    @Test
    void create_saves_new_rule() {
        when(repo.findByResourceAndAction("report", "view")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        PermissionRule r = service.create("report", "view", List.of("product"));
        assertThat(r.getResource()).isEqualTo("report");
        assertThat(r.getAction()).isEqualTo("view");
        assertThat(r.getRequiredFunctions()).containsExactly("product");
    }

    @Test
    void create_rejects_duplicate() {
        when(repo.findByResourceAndAction("wiki", "read")).thenReturn(Optional.of(new PermissionRule()));
        assertThatThrownBy(() -> service.create("wiki", "read", List.of("development")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void create_rejects_blank() {
        assertThatThrownBy(() -> service.create("", "view", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("必填");
    }

    @Test
    void update_replaces_required_functions() {
        PermissionRule existing = rule("id1", "wiki", "edit", List.of("development"));
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        PermissionRule r = service.update("id1", List.of("development", "product"));
        assertThat(r.getRequiredFunctions()).containsExactly("development", "product");
    }

    @Test
    void update_throws_404_when_missing() {
        when(repo.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("nope", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void delete_removes_existing() {
        PermissionRule existing = rule("id1", "wiki", "edit", List.of("development"));
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        service.delete("id1");
        verify(repo).delete(existing);
    }
}
