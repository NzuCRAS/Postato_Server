package com.potato.permission;

import com.potato.permission.PermissionRuleDtos.CreateRuleRequest;
import com.potato.permission.PermissionRuleDtos.UpdateRuleRequest;
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

/** 权限规则管理:可视化查看 + 在线编辑。判定仍由 PermissionService 负责。 */
@RestController
@RequestMapping("/api/v1/permission-rules")
public class PermissionController {

    private final PermissionRuleService ruleService;
    private final PermissionService permissionService;

    public PermissionController(PermissionRuleService ruleService, PermissionService permissionService) {
        this.ruleService = ruleService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<PermissionRule> list(@AuthenticationPrincipal User user) {
        permissionService.check(user, "permission", "view");
        return ruleService.list();
    }

    @PostMapping
    public PermissionRule create(@AuthenticationPrincipal User user, @RequestBody CreateRuleRequest req) {
        permissionService.check(user, "permission", "manage");
        return ruleService.create(req.resource(), req.action(), req.requiredFunctions());
    }

    @PutMapping("/{id}")
    public PermissionRule update(@AuthenticationPrincipal User user, @PathVariable String id,
                                 @RequestBody UpdateRuleRequest req) {
        permissionService.check(user, "permission", "manage");
        return ruleService.update(id, req.requiredFunctions());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable String id) {
        permissionService.check(user, "permission", "manage");
        ruleService.delete(id);
    }
}
