package com.potato.archgraph;

import com.potato.archgraph.ArchGraphDtos.EdgeRequest;
import com.potato.archgraph.ArchGraphDtos.Graph;
import com.potato.archgraph.ArchGraphDtos.ModuleRequest;
import com.potato.archgraph.ArchGraphDtos.RelateDocRequest;
import com.potato.permission.PermissionService;
import com.potato.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 架构图谱:模块 + 依赖边 + 模块档案。读 project/view,写 arch/edit_management。 */
@RestController
@RequestMapping("/api/v1/arch-graph")
public class ArchGraphController {

    private final ArchGraphService service;
    private final PermissionService permissionService;

    public ArchGraphController(ArchGraphService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @GetMapping
    public Graph graph(@AuthenticationPrincipal User user,
                       @RequestParam(name = "projectId", defaultValue = "default") String projectId) {
        permissionService.check(user, "project", "view");
        return service.getGraph(projectId);
    }

    @PostMapping("/modules")
    public ArchModule upsertModule(@AuthenticationPrincipal User user,
                                   @RequestParam(name = "projectId", defaultValue = "default") String projectId,
                                   @RequestBody ModuleRequest req) {
        permissionService.check(user, "arch", "edit_management");
        return service.upsertModule(projectId, req.key(), req.title(), req.description(),
                req.group(), req.implStatus(), req.relatedCode(), req.order());
    }

    @DeleteMapping("/modules/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModule(@AuthenticationPrincipal User user,
                             @RequestParam(name = "projectId", defaultValue = "default") String projectId,
                             @PathVariable String key) {
        permissionService.check(user, "arch", "edit_management");
        service.deleteModule(projectId, key);
    }

    @PostMapping("/edges")
    public ArchEdge upsertEdge(@AuthenticationPrincipal User user,
                               @RequestParam(name = "projectId", defaultValue = "default") String projectId,
                               @RequestBody EdgeRequest req) {
        permissionService.check(user, "arch", "edit_management");
        return service.upsertEdge(projectId, req.from(), req.to(), req.kind(), req.label());
    }

    @DeleteMapping("/edges/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEdge(@AuthenticationPrincipal User user, @PathVariable String id) {
        permissionService.check(user, "arch", "edit_management");
        service.deleteEdge(id);
    }

    @PostMapping("/relate-doc")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void relateDoc(@AuthenticationPrincipal User user,
                          @RequestParam(name = "projectId", defaultValue = "default") String projectId,
                          @RequestBody RelateDocRequest req) {
        permissionService.check(user, "arch", "edit_management");
        service.relateDoc(projectId, req.type(), req.ref(), req.title(), req.scope());
    }

    @PostMapping("/archive-legacy")
    public Map<String, Object> archiveLegacy(@AuthenticationPrincipal User user,
                                             @RequestParam(name = "projectId", defaultValue = "default") String projectId) {
        permissionService.check(user, "arch", "edit_management");
        return Map.of("archived", service.archiveLegacyTree(projectId));
    }
}
