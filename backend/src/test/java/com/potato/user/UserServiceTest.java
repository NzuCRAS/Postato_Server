package com.potato.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository repo;
    @Mock
    PasswordEncoder encoder;

    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repo, encoder);
    }

    private User user(String id, List<String> functions) {
        User u = new User();
        u.setId(id);
        u.setUsername("u-" + id);
        u.setFunctions(functions);
        return u;
    }

    @Test
    void create_encodes_password_and_saves() {
        when(repo.findByUsername("bob")).thenReturn(Optional.empty());
        when(encoder.encode("pw")).thenReturn("HASH");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        User u = service.create("bob", "pw", List.of("development"));
        assertThat(u.getUsername()).isEqualTo("bob");
        assertThat(u.getPasswordHash()).isEqualTo("HASH");
        assertThat(u.getFunctions()).containsExactly("development");
    }

    @Test
    void create_rejects_duplicate_username() {
        when(repo.findByUsername("bob")).thenReturn(Optional.of(new User()));
        assertThatThrownBy(() -> service.create("bob", "pw", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void create_rejects_blank() {
        assertThatThrownBy(() -> service.create("", "pw", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("必填");
    }

    @Test
    void updateFunctions_changes_when_other_admin_exists() {
        User u1 = user("id1", List.of("admin"));
        User u2 = user("id2", List.of("admin"));
        when(repo.findById("id1")).thenReturn(Optional.of(u1));
        when(repo.findAll()).thenReturn(List.of(u1, u2));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        User r = service.updateFunctions("id1", List.of("development"));
        assertThat(r.getFunctions()).containsExactly("development");
    }

    @Test
    void updateFunctions_rejects_demoting_last_admin() {
        User u1 = user("id1", List.of("admin"));
        when(repo.findById("id1")).thenReturn(Optional.of(u1));
        when(repo.findAll()).thenReturn(List.of(u1));
        assertThatThrownBy(() -> service.updateFunctions("id1", List.of("development")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("最后一个管理员");
    }

    @Test
    void resetPassword_encodes_and_saves() {
        User u1 = user("id1", List.of("development"));
        when(repo.findById("id1")).thenReturn(Optional.of(u1));
        when(encoder.encode("new")).thenReturn("NEWHASH");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.resetPassword("id1", "new");
        assertThat(u1.getPasswordHash()).isEqualTo("NEWHASH");
    }

    @Test
    void delete_rejects_self() {
        assertThatThrownBy(() -> service.delete("id1", "id1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能删除自己");
        verify(repo, never()).delete(any());
    }

    @Test
    void delete_rejects_last_admin() {
        User u1 = user("id1", List.of("admin"));
        when(repo.findById("id1")).thenReturn(Optional.of(u1));
        when(repo.findAll()).thenReturn(List.of(u1));
        assertThatThrownBy(() -> service.delete("id1", "other"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("最后一个管理员");
    }

    @Test
    void delete_removes_non_admin() {
        User u1 = user("id1", List.of("development"));
        when(repo.findById("id1")).thenReturn(Optional.of(u1));
        service.delete("id1", "admin-id");
        verify(repo).delete(u1);
    }
}
