package com.potato.permission;

import com.potato.permission.dict.ActionDefRepository;
import com.potato.permission.dict.FunctionDefRepository;
import com.potato.permission.dict.ResourceDefRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限规则 CRUD(供 admin 在线编辑)。判定逻辑在 PermissionService,这里只管规则数据维护。
 * (resource,action) 唯一;建/改时校验引用完整性——resource/action/requiredFunctions 必须出自已注册字典。
 */
@Service
public class PermissionRuleService {

    private final PermissionRuleRepository repository;
    private final ResourceDefRepository resourceDefRepository;
    private final ActionDefRepository actionDefRepository;
    private final FunctionDefRepository functionDefRepository;

    public PermissionRuleService(PermissionRuleRepository repository,
                                 ResourceDefRepository resourceDefRepository,
                                 ActionDefRepository actionDefRepository,
                                 FunctionDefRepository functionDefRepository) {
        this.repository = repository;
        this.resourceDefRepository = resourceDefRepository;
        this.actionDefRepository = actionDefRepository;
        this.functionDefRepository = functionDefRepository;
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
        if (!resourceDefRepository.existsByKey(res)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资源未注册: " + res);
        }
        if (!actionDefRepository.existsByKey(act)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "动作未注册: " + act);
        }
        validateFunctions(requiredFunctions);
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
        validateFunctions(requiredFunctions);
        PermissionRule rule = get(id);
        rule.setRequiredFunctions(requiredFunctions == null ? new ArrayList<>() : new ArrayList<>(requiredFunctions));
        return repository.save(rule);
    }

    public void delete(String id) {
        repository.delete(get(id));
    }

    /** 每个职能必须已注册。 */
    private void validateFunctions(List<String> requiredFunctions) {
        if (requiredFunctions == null) return;
        for (String f : requiredFunctions) {
            if (f != null && !functionDefRepository.existsByKey(f)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "职能未注册: " + f);
            }
        }
    }

    private PermissionRule get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在"));
    }
}
