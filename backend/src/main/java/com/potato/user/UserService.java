package com.potato.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * admin 视角的用户管理:列表 / 建 / 改职能 / 重置密码 / 删除。
 * 兜底:不能删除自己、不能删光或降级最后一个 admin(避免无人可管)。
 * 自助 API Key 仍在 UserController(/me)。
 */
@Service
public class UserService {

    private static final String ADMIN = "admin";

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> list() {
        return repository.findAll();
    }

    public User create(String username, String rawPassword, List<String> functions) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码必填");
        }
        String name = username.trim();
        repository.findByUsername(name).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在: " + name);
        });
        User u = new User();
        u.setUsername(name);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setFunctions(functions == null ? new ArrayList<>() : new ArrayList<>(functions));
        Instant now = Instant.now();
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        return repository.save(u);
    }

    public User updateFunctions(String id, List<String> functions) {
        User u = get(id);
        List<String> next = functions == null ? new ArrayList<>() : new ArrayList<>(functions);
        // 不能把最后一个 admin 降级,否则无人可管
        if (isAdmin(u) && !next.contains(ADMIN) && adminCount() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能移除最后一个管理员的 admin 职能");
        }
        u.setFunctions(next);
        u.setUpdatedAt(Instant.now());
        return repository.save(u);
    }

    public void resetPassword(String id, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码必填");
        }
        User u = get(id);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setUpdatedAt(Instant.now());
        repository.save(u);
    }

    /** 自助改密:校验旧密码后改新密码。 */
    public void changeOwnPassword(String id, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码必填");
        }
        User u = get(id);
        if (!passwordEncoder.matches(oldPassword == null ? "" : oldPassword, u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "旧密码不正确");
        }
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setUpdatedAt(Instant.now());
        repository.save(u);
    }

    public void delete(String id, String currentUserId) {
        if (id.equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除自己");
        }
        User u = get(id);
        if (isAdmin(u) && adminCount() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除最后一个管理员");
        }
        repository.delete(u);
    }

    private User get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private boolean isAdmin(User u) {
        return u.getFunctions() != null && u.getFunctions().contains(ADMIN);
    }

    private long adminCount() {
        return repository.findAll().stream().filter(this::isAdmin).count();
    }
}
