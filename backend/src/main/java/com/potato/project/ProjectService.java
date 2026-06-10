package com.potato.project;

import com.potato.common.DocLink;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    public List<Project> list() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    public Project get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));
    }

    public Project create(String name, String descriptionMd, String userId) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目名必填");
        }
        Project p = new Project();
        p.setName(name);
        p.setDescriptionMd(descriptionMd);
        p.setCreatedBy(userId);
        Instant now = Instant.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return repository.save(p);
    }

    public Project update(String id, String name, String descriptionMd) {
        Project p = get(id);
        if (name != null && !name.isBlank()) p.setName(name);
        if (descriptionMd != null) p.setDescriptionMd(descriptionMd);
        return touch(p);
    }

    public Project addRepo(String id, Project.Repo repo) {
        if (repo == null || repo.getUrl() == null || repo.getUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repo.url 必填");
        }
        Project p = get(id);
        repo.setId("repo_" + UUID.randomUUID().toString().substring(0, 8));
        p.getRepos().add(repo);
        return touch(p);
    }

    public Project removeRepo(String id, String repoId) {
        Project p = get(id);
        p.getRepos().removeIf(r -> repoId.equals(r.getId()));
        return touch(p);
    }

    public Project addDocLink(String id, DocLink link) {
        if (link == null || link.getPath() == null || link.getPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "doc path 必填");
        }
        Project p = get(id);
        p.getDocLinks().add(link);
        return touch(p);
    }

    public Project removeDocLink(String id, String path) {
        Project p = get(id);
        p.getDocLinks().removeIf(d -> path.equals(d.getPath()));
        return touch(p);
    }

    private Project touch(Project p) {
        p.setUpdatedAt(Instant.now());
        return repository.save(p);
    }
}
