package com.potato.user;

import com.potato.permission.PermissionService;
import com.potato.user.User.ApiKey;
import com.potato.user.UserDtos.ChangePasswordRequest;
import com.potato.user.UserDtos.CreateUserRequest;
import com.potato.user.UserDtos.ResetPasswordRequest;
import com.potato.user.UserDtos.UpdateFunctionsRequest;
import com.potato.user.UserDtos.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PermissionService permissionService;

    public UserController(UserRepository userRepository, UserService userService, PermissionService permissionService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.permissionService = permissionService;
    }

    // ---- admin 用户管理 ----

    /** 列出用户:有 user/view(admin 豁免)→ 全部;否则只返自己(人人可见自己)。 */
    @GetMapping
    public List<UserView> list(@AuthenticationPrincipal User user) {
        if (permissionService.hasPermission(user, "user", "view")) {
            return userService.list().stream().map(UserView::of).toList();
        }
        return List.of(UserView.of(user));
    }

    /** 新建用户。 */
    @PostMapping
    public UserView create(@AuthenticationPrincipal User user, @RequestBody CreateUserRequest req) {
        permissionService.check(user, "user", "create");
        return UserView.of(userService.create(req.username(), req.password(), req.functions()));
    }

    /** 改某用户职能。 */
    @PutMapping("/{id}")
    public UserView updateFunctions(@AuthenticationPrincipal User user, @PathVariable String id,
                                    @RequestBody UpdateFunctionsRequest req) {
        permissionService.check(user, "user", "update");
        return UserView.of(userService.updateFunctions(id, req.functions()));
    }

    /** 重置某用户密码。 */
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@AuthenticationPrincipal User user, @PathVariable String id,
                              @RequestBody ResetPasswordRequest req) {
        permissionService.check(user, "user", "update");
        userService.resetPassword(id, req.password());
    }

    /** 删除某用户。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable String id) {
        permissionService.check(user, "user", "delete");
        userService.delete(id, user.getId());
    }

    // ---- 自助(当前登录用户)----

    /** 自助修改自己密码(校验旧密码)。任何登录用户可调。 */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMyPassword(@AuthenticationPrincipal User user, @RequestBody ChangePasswordRequest req) {
        userService.changeOwnPassword(user.getId(), req.oldPassword(), req.newPassword());
    }

    /** 当前登录用户信息(API Key 仅返回脱敏前缀) */
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal User user) {
        List<Map<String, Object>> keys = user.getApiKeys().stream()
                .map(k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", k.getId());
                    m.put("name", k.getName());
                    m.put("keyPreview", maskKey(k.getKey()));
                    m.put("createdAt", k.getCreatedAt());
                    return m;
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("functions", user.getFunctions());
        result.put("apiKeys", keys);
        return result;
    }

    /** 生成新的 API Key —— 明文 key 仅在此刻返回一次 */
    @PostMapping("/me/api-keys")
    public Map<String, Object> createApiKey(@AuthenticationPrincipal User user,
                                            @RequestBody(required = false) CreateApiKeyRequest req) {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(UUID.randomUUID().toString());
        apiKey.setKey("mcp_live_" + UUID.randomUUID().toString().replace("-", ""));
        apiKey.setName(req != null && req.name() != null && !req.name().isBlank() ? req.name() : "default");
        apiKey.setCreatedAt(Instant.now());
        user.getApiKeys().add(apiKey);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", apiKey.getName());
        result.put("key", apiKey.getKey());
        result.put("createdAt", apiKey.getCreatedAt());
        return result;
    }

    @DeleteMapping("/me/api-keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteApiKey(@AuthenticationPrincipal User user, @PathVariable String id) {
        boolean removed = user.getApiKeys().removeIf(k -> id.equals(k.getId()));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API Key 不存在");
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 16) {
            return "****";
        }
        return key.substring(0, 12) + "****" + key.substring(key.length() - 4);
    }
}
