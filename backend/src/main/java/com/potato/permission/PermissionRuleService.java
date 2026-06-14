package com.potato.permission;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限规则 CRUD(供 admin 在线编辑)。判定逻辑在 PermissionService,这里只管规则数据维护。
 * (resource,action) 唯一;增删改不影响判定算法。
 */
@Service
public class PermissionRuleService {

    private final PermissionRuleRepository repository;

    public PermissionRuleService(PermissionRuleRepository repository) {
        this.repository = repository;
    }

    public List<PermissionRule> list() {
        return repository.findAll();
    }

    public PermissionRule create(String resource, String action, List<String> requiredFunctions) {
        if (resource == null || resource.isBlank() || action == null || action.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resource 和 action 必填");
        }
        String res = resource.trim();
        String act = action.trim();
        repository.findByResourceAndAction(res, act).ifPresent(r -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "规则已存在: " + res + "/" + act);
        });
        PermissionRule rule = new PermissionRule();
        rule.setResource(res);
        rule.setAction(act);
        rule.setRequiredFunctions(requiredFunctions == null ? new ArrayList<>() : new ArrayList<>(requiredFunctions));
        return repository.save(rule);
    }

    public PermissionRule update(String id, List<String> requiredFunctions) {
        PermissionRule rule = get(id);
        rule.setRequiredFunctions(requiredFunctions == null ? new ArrayList<>() : new ArrayList<>(requiredFunctions));
        return repository.save(rule);
    }

    public void delete(String id) {
        repository.delete(get(id));
    }

    private PermissionRule get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在"));
    }
}
