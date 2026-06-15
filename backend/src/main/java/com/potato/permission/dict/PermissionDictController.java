package com.potato.permission.dict;

import com.potato.permission.PermissionService;
import com.potato.permission.dict.PermissionDictDtos.CreateDefRequest;
import com.potato.permission.dict.PermissionDictDtos.UpdateDefRequest;
import com.potato.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 权限字典管理:职能 / 资源 / 动作(均 admin via permission/view|manage)。 */
@RestController
@RequestMapping("/api/v1/permission")
public class PermissionDictController {

    private final PermissionDictService service;
    private final PermissionService permissionService;
    private final FunctionDefRepository functionRepo;
    private final ResourceDefRepository resourceRepo;
    private final ActionDefRepository actionRepo;

    public PermissionDictController(PermissionDictService service, PermissionService permissionService,
                                    FunctionDefRepository functionRepo, ResourceDefRepository resourceRepo,
                                    ActionDefRepository actionRepo) {
        this.service = service;
        this.permissionService = permissionService;
        this.functionRepo = functionRepo;
        this.resourceRepo = resourceRepo;
        this.actionRepo = actionRepo;
    }

    // ---- 职能 functions ----
    @GetMapping("/functions")
    public List<FunctionDef> listFunctions(@AuthenticationPrincipal User user) {
        permissionService.check(user, "permission", "view");
        return service.list(functionRepo);
    }

    @PostMapping("/functions")
    public FunctionDef createFunction(@AuthenticationPrincipal User user, @RequestBody CreateDefRequest req) {
        permissionService.check(user, "permission", "manage");
        FunctionDef d = new FunctionDef();
        d.setKey(req.key());
        d.setLabel(req.label());
        d.setDescription(req.description());
        return service.create(functionRepo, d);
    }

    @PutMapping("/functions/{key}")
    public FunctionDef updateFunction(@AuthenticationPrincipal User user, @PathVariable String key, @RequestBody UpdateDefRequest req) {
        permissionService.check(user, "permission", "manage");
        return service.update(functionRepo, key, req.label(), req.description());
    }

    @DeleteMapping("/functions/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFunction(@AuthenticationPrincipal User user, @PathVariable String key) {
        permissionService.check(user, "permission", "manage");
        service.deleteFunction(functionRepo, key);
    }

    // ---- 资源 resources ----
    @GetMapping("/resources")
    public List<ResourceDef> listResources(@AuthenticationPrincipal User user) {
        permissionService.check(user, "permission", "view");
        return service.list(resourceRepo);
    }

    @PostMapping("/resources")
    public ResourceDef createResource(@AuthenticationPrincipal User user, @RequestBody CreateDefRequest req) {
        permissionService.check(user, "permission", "manage");
        ResourceDef d = new ResourceDef();
        d.setKey(req.key());
        d.setLabel(req.label());
        d.setDescription(req.description());
        return service.create(resourceRepo, d);
    }

    @PutMapping("/resources/{key}")
    public ResourceDef updateResource(@AuthenticationPrincipal User user, @PathVariable String key, @RequestBody UpdateDefRequest req) {
        permissionService.check(user, "permission", "manage");
        return service.update(resourceRepo, key, req.label(), req.description());
    }

    @DeleteMapping("/resources/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResource(@AuthenticationPrincipal User user, @PathVariable String key) {
        permissionService.check(user, "permission", "manage");
        service.deleteResource(resourceRepo, key);
    }

    // ---- 动作 actions ----
    @GetMapping("/actions")
    public List<ActionDef> listActions(@AuthenticationPrincipal User user) {
        permissionService.check(user, "permission", "view");
        return service.list(actionRepo);
    }

    @PostMapping("/actions")
    public ActionDef createAction(@AuthenticationPrincipal User user, @RequestBody CreateDefRequest req) {
        permissionService.check(user, "permission", "manage");
        ActionDef d = new ActionDef();
        d.setKey(req.key());
        d.setLabel(req.label());
        d.setDescription(req.description());
        return service.create(actionRepo, d);
    }

    @PutMapping("/actions/{key}")
    public ActionDef updateAction(@AuthenticationPrincipal User user, @PathVariable String key, @RequestBody UpdateDefRequest req) {
        permissionService.check(user, "permission", "manage");
        return service.update(actionRepo, key, req.label(), req.description());
    }

    @DeleteMapping("/actions/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAction(@AuthenticationPrincipal User user, @PathVariable String key) {
        permissionService.check(user, "permission", "manage");
        service.deleteAction(actionRepo, key);
    }
}
