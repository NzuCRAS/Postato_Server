package com.potato.permission.dict;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

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

    PermissionDictService service;

    @BeforeEach
    void setUp() {
        service = new PermissionDictService();
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
        assertThat(r.getLabel()).isEqualTo("reviewer"); // label 缺省回落 key
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
    void delete_removes_existing() {
        FunctionDef existing = def("testing", "测试");
        when(repo.findByKey("testing")).thenReturn(Optional.of(existing));
        service.delete(repo, "testing");
        verify(repo).delete(existing);
    }
}
