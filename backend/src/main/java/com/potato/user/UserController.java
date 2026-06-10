package com.potato.user;

import com.potato.user.User.ApiKey;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
