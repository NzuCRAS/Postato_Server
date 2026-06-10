package com.potato.project;

import com.potato.common.DocLink;
import com.potato.permission.PermissionService;
import com.potato.project.ProjectDtos.ProjectRequest;
import com.potato.project.ProjectDtos.RepoRequest;
import com.potato.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService service;
    private final PermissionService permissionService;

    public ProjectController(ProjectService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<Project> list(@AuthenticationPrincipal User user) {
        permissionService.check(user, "project", "view");
        return service.list();
    }

    @GetMapping("/{id}")
    public Project get(@AuthenticationPrincipal User user, @PathVariable String id) {
        permissionService.check(user, "project", "view");
        return service.get(id);
    }

    @PostMapping
    public Project create(@AuthenticationPrincipal User user, @RequestBody ProjectRequest req) {
        permissionService.check(user, "project", "create");
        return service.create(req.name(), req.descriptionMd(), user.getId());
    }

    @PutMapping("/{id}")
    public Project update(@AuthenticationPrincipal User user, @PathVariable String id, @RequestBody ProjectRequest req) {
        permissionService.check(user, "project", "edit");
        return service.update(id, req.name(), req.descriptionMd());
    }

    @PostMapping("/{id}/repos")
    public Project addRepo(@AuthenticationPrincipal User user, @PathVariable String id, @RequestBody RepoRequest req) {
        permissionService.check(user, "project", "edit");
        Project.Repo repo = new Project.Repo();
        repo.setName(req.name());
        repo.setUrl(req.url());
        repo.setProvider(req.provider());
        repo.setDefaultBranch(req.defaultBranch());
        return service.addRepo(id, repo);
    }

    @DeleteMapping("/{id}/repos/{repoId}")
    public Project removeRepo(@AuthenticationPrincipal User user, @PathVariable String id, @PathVariable String repoId) {
        permissionService.check(user, "project", "edit");
        return service.removeRepo(id, repoId);
    }

    @PostMapping("/{id}/doc-links")
    public Project addDocLink(@AuthenticationPrincipal User user, @PathVariable String id, @RequestBody DocLink link) {
        permissionService.check(user, "project", "edit");
        return service.addDocLink(id, link);
    }

    @DeleteMapping("/{id}/doc-links")
    public Project removeDocLink(@AuthenticationPrincipal User user, @PathVariable String id, @RequestParam String path) {
        permissionService.check(user, "project", "edit");
        return service.removeDocLink(id, path);
    }
}
