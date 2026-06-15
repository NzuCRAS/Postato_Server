package com.potato.permission.dict;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

/** 字典仓储基:按 key 查找/判存。三个具体字典各自继承。 */
@NoRepositoryBean
public interface PermissionDefRepository<T extends PermissionDef> extends MongoRepository<T, String> {

    Optional<T> findByKey(String key);

    boolean existsByKey(String key);
}
