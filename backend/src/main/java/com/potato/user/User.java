package com.potato.user;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document("users")
public class User {

    @Id
    private String id;

    private String username;
    private String passwordHash;

    /** 职能标签:admin | product | development | testing(可多个) */
    private List<String> functions = new ArrayList<>();

    private List<ApiKey> apiKeys = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    @Data
    public static class ApiKey {
        private String id;
        private String key;
        private String name;
        private Instant createdAt;
    }
}
