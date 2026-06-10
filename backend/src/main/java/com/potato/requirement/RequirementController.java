package com.potato.requirement;

import com.potato.permission.PermissionService;
import com.potato.user.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requirements")
public class RequirementController {

    private final RequirementService service;
    private final PermissionService permissionService;

    public RequirementController(RequirementService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<RequirementSummary> list(@AuthenticationPrincipal User user,
                                         @RequestParam(required = false) String status) {
        permissionService.check(user, "requirement", "view");
        return service.list(status).stream()
                .map(r -> new RequirementSummary(r.getId(), r.getTitle(), r.getStatus(), r.getVersion(), r.getUpdatedAt()))
                .toList();
    }

    @GetMapping("/{id}")
    public Requirement get(@AuthenticationPrincipal User user, @PathVariable String id) {
        permissionService.check(user, "requirement", "view");
        return service.get(id);
    }

    @PostMapping
    public Requirement create(@AuthenticationPrincipal User user,
                              @Valid @RequestBody RequirementRequest req) {
        permissionService.check(user, "requirement", "create");
        return service.create(req.title(), req.descriptionMd(), req.structured(), req.status(), user.getId());
    }

    @PutMapping("/{id}")
    public Requirement update(@AuthenticationPrincipal User user,
                              @PathVariable String id,
                              @RequestBody RequirementRequest req) {
        permissionService.check(user, "requirement", "edit_structured");
        return service.update(id, req.title(), req.descriptionMd(), req.structured());
    }

    @PatchMapping("/{id}/status")
    public Requirement updateStatus(@AuthenticationPrincipal User user,
                                    @PathVariable String id,
                                    @Valid @RequestBody UpdateStatusRequest req) {
        permissionService.check(user, "requirement", "update_status");
        return service.updateStatus(id, req.status());
    }
}
