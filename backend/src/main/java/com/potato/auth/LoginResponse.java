package com.potato.auth;

import java.util.List;

public record LoginResponse(
        String token,
        String userId,
        String username,
        List<String> functions) {
}
