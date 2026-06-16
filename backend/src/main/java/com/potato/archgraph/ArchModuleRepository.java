package com.potato.archgraph;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArchModuleRepository extends MongoRepository<ArchModule, String> {

    Optional<ArchModule> findByProjectIdAndKey(String projectId, String key);

    List<ArchModule> findByProjectIdOrderByOrderAsc(String projectId);

    boolean existsByProjectIdAndKey(String projectId, String key);
}
