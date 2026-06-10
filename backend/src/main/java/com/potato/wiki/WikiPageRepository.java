package com.potato.wiki;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface WikiPageRepository extends MongoRepository<WikiPage, String> {

    Optional<WikiPage> findByPath(String path);

    List<WikiPage> findAllByOrderByPathAsc();
}
