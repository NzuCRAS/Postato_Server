package com.potato.requirement;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class RequirementService {

    private static final Set<String> VALID_STATUSES = Set.of("draft", "clarifying", "confirmed", "deprecated");
    private static final String DEFAULT_PROJECT = "default";

    private final RequirementRepository repository;

    public RequirementService(RequirementRepository repository) {
        this.repository = repository;
    }

    public List<Requirement> list(String status) {
        if (status != null && !status.isBlank()) {
            return repository.findByStatus(status);
        }
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    public Requirement get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在"));
    }

    public Requirement create(String title, String descriptionMd, Structured structured, String status, String userId) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题必填");
        }
        Requirement r = new Requirement();
        r.setProjectId(DEFAULT_PROJECT);
        r.setTitle(title);
        r.setDescriptionMd(descriptionMd);
        r.setStructured(structured != null ? structured : new Structured());
        r.setStatus(normalizeStatus(status, "draft"));
        r.setVersion(1);
        r.setCreatedBy(userId);
        Instant now = Instant.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        return repository.save(r);
    }

    public Requirement update(String id, String title, String descriptionMd, Structured structured) {
        Requirement r = get(id);
        if (title != null) r.setTitle(title);
        if (descriptionMd != null) r.setDescriptionMd(descriptionMd);
        if (structured != null) {
            r.setStructured(structured);
            r.setVersion(r.getVersion() + 1); // structured 变更时版本自增
        }
        r.setUpdatedAt(Instant.now());
        return repository.save(r);
    }

    public Requirement updateStatus(String id, String status) {
        Requirement r = get(id);
        r.setStatus(normalizeStatus(status, null));
        r.setUpdatedAt(Instant.now());
        return repository.save(r);
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            if (fallback != null) return fallback;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 必填");
        }
        if (!VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法状态: " + status);
        }
        return status;
    }
}
