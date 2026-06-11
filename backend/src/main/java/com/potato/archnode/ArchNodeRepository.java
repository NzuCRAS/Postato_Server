package com.potato.archnode;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArchNodeRepository extends MongoRepository<ArchNode, String> {

    List<ArchNode> findByProjectIdOrderByPathAsc(String projectId);

    Optional<ArchNode> findByProjectIdAndPath(String projectId, String path);

    List<ArchNode> findByProjectIdAndPathStartingWith(String projectId, String prefix);

    List<ArchNode> findByProjectIdAndRepoId(String projectId, String repoId);

    List<ArchNode> findByProjectIdAndParentId(String projectId, String parentId);
}
