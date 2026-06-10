package com.potato.permission;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PermissionRuleRepository extends MongoRepository<PermissionRule, String> {

    Optional<PermissionRule> findByResourceAndAction(String resource, String action);
}
