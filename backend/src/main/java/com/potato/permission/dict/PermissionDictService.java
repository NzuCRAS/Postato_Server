package com.potato.permission.dict;

import com.potato.permission.PermissionRuleRepository;
import com.potato.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 职能/资源/动作三类字典的通用 CRUD。三类结构一致,用泛型 helper 复用。
 * key 受控不可变(规则/用户引用它);删除带引用保护:被规则或用户引用的项不可删。
 */
@Service
public class PermissionDictService {

    private final PermissionRuleRepository ruleRepository;
    private final UserRepository userRepository;

    public PermissionDictService(PermissionRuleRepository ruleRepository, UserRepository userRepository) {
        this.ruleRepository = ruleRepository;
        this.userRepository = userRepository;
    }

    public <T extends PermissionDef> List<T> list(PermissionDefRepository<T> repo) {
        return repo.findAll();
    }

    public <T extends PermissionDef> T create(PermissionDefRepository<T> repo, T entity) {
        String key = entity.getKey() == null ? null : entity.getKey().trim();
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key 必填");
        }
        if (repo.existsByKey(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "key 已存在: " + key);
        }
        entity.setKey(key);
        if (entity.getLabel() == null || entity.getLabel().isBlank()) {
            entity.setLabel(key);
        }
        return repo.save(entity);
    }

    public <T extends PermissionDef> T update(PermissionDefRepository<T> repo, String key, String label, String description) {
        T e = repo.findByKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "字典项不存在: " + key));
        if (label != null) e.setLabel(label);
        if (description != null) e.setDescription(description);
        return repo.save(e);
    }

    public <T extends PermissionDef> T getByKey(PermissionDefRepository<T> repo, String key) {
        return repo.findByKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "字典项不存在: " + key));
    }

    public <T extends PermissionDef> void delete(PermissionDefRepository<T> repo, String key) {
        repo.delete(getByKey(repo, key));
    }

    // ---- 带引用保护的删除(类型相关,各查对应引用)----

    /** 职能被任一规则的 requiredFunctions 或任一用户的 functions 引用则拒删。 */
    public void deleteFunction(FunctionDefRepository repo, String key) {
        boolean usedByRule = ruleRepository.findAll().stream()
                .anyMatch(r -> r.getRequiredFunctions() != null && r.getRequiredFunctions().contains(key));
        boolean usedByUser = userRepository.findAll().stream()
                .anyMatch(u -> u.getFunctions() != null && u.getFunctions().contains(key));
        if (usedByRule || usedByUser) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "职能被规则或用户引用,不能删除: " + key);
        }
        delete(repo, key);
    }

    /** 资源被任一规则引用则拒删。 */
    public void deleteResource(ResourceDefRepository repo, String key) {
        if (ruleRepository.findAll().stream().anyMatch(r -> key.equals(r.getResource()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "资源被规则引用,不能删除: " + key);
        }
        delete(repo, key);
    }

    /** 动作被任一规则引用则拒删。 */
    public void deleteAction(ActionDefRepository repo, String key) {
        if (ruleRepository.findAll().stream().anyMatch(r -> key.equals(r.getAction()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "动作被规则引用,不能删除: " + key);
        }
        delete(repo, key);
    }
}

