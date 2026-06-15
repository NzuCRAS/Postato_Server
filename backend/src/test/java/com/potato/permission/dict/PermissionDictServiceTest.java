package com.potato.permission.dict;

import com.potato.permission.PermissionRule;
import com.potato.permission.PermissionRuleRepository;
import com.potato.user.User;
import com.potato.user.UserRepository;
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
class PermissionDictServiceTest {

    @Mock
    FunctionDefRepository repo;
    @Mock
    PermissionRuleRepository ruleRepo;
    @Mock
    UserRepository userRepo;

    PermissionDictService service;

    @BeforeEach
    void setUp() {
        service = new PermissionDictService(ruleRepo, userRepo);
    }

    private FunctionDef def(String key, String label) {
        FunctionDef d = new FunctionDef();
        d.setKey(key);
        d.setLabel(label);
        return d;
    }

    @Test
    void create_saves_new_and_defaults_label_to_key() {
        FunctionDef input = new FunctionDef();
        input.setKey("reviewer");
        when(repo.existsByKey("reviewer")).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        FunctionDef r = service.create(repo, input);
        assertThat(r.getKey()).isEqualTo("reviewer");
        assertThat(r.getLabel()).isEqualTo("reviewer");
    }

    @Test
    void create_rejects_duplicate_key() {
        FunctionDef input = def("admin", "管理员");
        when(repo.existsByKey("admin")).thenReturn(true);
        assertThatThrownBy(() -> service.create(repo, input))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void create_rejects_blank_key() {
        assertThatThrownBy(() -> service.create(repo, new FunctionDef()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("key 必填");
    }

    @Test
    void update_changes_label_and_description_keeping_key() {
        FunctionDef existing = def("product", "产品");
        when(repo.findByKey("product")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        FunctionDef r = service.update(repo, "product", "产品经理", "PM 职能");
        assertThat(r.getKey()).isEqualTo("product");
        assertThat(r.getLabel()).isEqualTo("产品经理");
        assertThat(r.getDescription()).isEqualTo("PM 职能");
    }

    @Test
    void update_throws_404_when_missing() {
        when(repo.findByKey("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(repo, "nope", "x", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void deleteFunction_rejects_when_used_by_user() {
        User u = new User();
        u.setFunctions(List.of("admin"));
        when(ruleRepo.findAll()).thenReturn(List.of());
        when(userRepo.findAll()).thenReturn(List.of(u));
        assertThatThrownBy(() -> service.deleteFunction(repo, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能删除");
    }

    @Test
    void deleteFunction_rejects_when_used_by_rule() {
        PermissionRule r = new PermissionRule();
        r.setRequiredFunctions(List.of("product"));
        when(ruleRepo.findAll()).thenReturn(List.of(r));
        assertThatThrownBy(() -> service.deleteFunction(repo, "product"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能删除");
    }

    @Test
    void deleteFunction_deletes_when_unused() {
        FunctionDef existing = def("reviewer", "评审");
        when(ruleRepo.findAll()).thenReturn(List.of());
        when(userRepo.findAll()).thenReturn(List.of());
        when(repo.findByKey("reviewer")).thenReturn(Optional.of(existing));
        service.deleteFunction(repo, "reviewer");
        verify(repo).delete(existing);
    }

    @Test
    void deleteResource_rejects_when_used_by_rule() {
        PermissionRule r = new PermissionRule();
        r.setResource("wiki");
        when(ruleRepo.findAll()).thenReturn(List.of(r));
        assertThatThrownBy(() -> service.deleteResource(null, "wiki"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能删除");
    }
}
