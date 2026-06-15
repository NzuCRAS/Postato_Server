package com.potato.permission;

import com.potato.permission.dict.ActionDefRepository;
import com.potato.permission.dict.FunctionDefRepository;
import com.potato.permission.dict.ResourceDefRepository;
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
    @Mock
    ResourceDefRepository resourceRepo;
    @Mock
    ActionDefRepository actionRepo;
    @Mock
    FunctionDefRepository functionRepo;

    PermissionRuleService service;

    @BeforeEach
    void setUp() {
        service = new PermissionRuleService(repo, resourceRepo, actionRepo, functionRepo);
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
        when(resourceRepo.existsByKey("report")).thenReturn(true);
        when(actionRepo.existsByKey("view")).thenReturn(true);
        when(functionRepo.existsByKey("product")).thenReturn(true);
        when(repo.findByResourceAndAction("report", "view")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        PermissionRule r = service.create("report", "view", List.of("product"));
        assertThat(r.getResource()).isEqualTo("report");
        assertThat(r.getRequiredFunctions()).containsExactly("product");
    }

    @Test
    void create_rejects_unregistered_resource() {
        when(resourceRepo.existsByKey("ghost")).thenReturn(false);
        assertThatThrownBy(() -> service.create("ghost", "view", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("资源未注册");
    }

    @Test
    void create_rejects_unregistered_action() {
        when(resourceRepo.existsByKey("wiki")).thenReturn(true);
        when(actionRepo.existsByKey("ghost")).thenReturn(false);
        assertThatThrownBy(() -> service.create("wiki", "ghost", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("动作未注册");
    }

    @Test
    void create_rejects_unregistered_function() {
        when(resourceRepo.existsByKey("wiki")).thenReturn(true);
        when(actionRepo.existsByKey("read")).thenReturn(true);
        when(functionRepo.existsByKey("ghost")).thenReturn(false);
        assertThatThrownBy(() -> service.create("wiki", "read", List.of("ghost")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("职能未注册");
    }

    @Test
    void create_rejects_duplicate() {
        when(resourceRepo.existsByKey("wiki")).thenReturn(true);
        when(actionRepo.existsByKey("read")).thenReturn(true);
        when(repo.findByResourceAndAction("wiki", "read")).thenReturn(Optional.of(new PermissionRule()));
        assertThatThrownBy(() -> service.create("wiki", "read", List.of()))
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
        when(functionRepo.existsByKey("development")).thenReturn(true);
        when(functionRepo.existsByKey("product")).thenReturn(true);
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
