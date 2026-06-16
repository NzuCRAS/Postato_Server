package com.potato.archgraph;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArchEdgeRepository extends MongoRepository<ArchEdge, String> {

    List<ArchEdge> findByProjectId(String projectId);

    Optional<ArchEdge> findByProjectIdAndFromAndToAndKind(String projectId, String from, String to, String kind);

    List<ArchEdge> findByProjectIdAndFrom(String projectId, String from);

    List<ArchEdge> findByProjectIdAndTo(String projectId, String to);
}
