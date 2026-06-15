package com.potato.permission.dict;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 职能/资源/动作三类字典的通用 CRUD。三类结构一致,用泛型 helper 复用。
 * key 受控不可变(规则/用户引用它);删除引用保护见 PermissionRuleService(node_2 接入)。
 */
@Service
public class PermissionDictService {

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
}
