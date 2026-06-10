package com.potato.requirement;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RequirementRepository extends MongoRepository<Requirement, String> {

    List<Requirement> findByStatus(String status);

    List<Requirement> findAllByOrderByUpdatedAtDesc();

    List<Requirement> findByProjectIdOrderByUpdatedAtDesc(String projectId);

    List<Requirement> findByProjectIdAndStatus(String projectId, String status);
}
