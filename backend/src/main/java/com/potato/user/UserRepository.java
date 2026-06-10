package com.potato.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsername(String username);

    /** 按 API Key 查找用户(apiKeys 数组中 key 字段匹配) */
    Optional<User> findByApiKeysKey(String key);
}
