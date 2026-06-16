package com.potato.run;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SopRunRepository extends MongoRepository<SopRun, String> {

    /** 一个需求的最新运行(按创建倒序取第一个 running)。 */
    Optional<SopRun> findFirstByReqIdAndStatusOrderByCreatedAtDesc(String reqId, String status);

    List<SopRun> findByReqIdOrderByCreatedAtDesc(String reqId);
}
