package com.potato.user;

import java.time.Instant;
import java.util.List;

/** admin 用户管理用的请求/视图 DTO。 */
public class UserDtos {

    public record CreateUserRequest(String username, String password, List<String> functions) {
    }

    public record UpdateFunctionsRequest(List<String> functions) {
    }

    public record ResetPasswordRequest(String password) {
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    /** 脱敏用户视图:不含 passwordHash、不含 API Key 明文(仅数量)。 */
    public record UserView(String id, String username, List<String> functions, int apiKeyCount, Instant createdAt) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getUsername(), u.getFunctions(),
                    u.getApiKeys() == null ? 0 : u.getApiKeys().size(), u.getCreatedAt());
        }
    }
}
