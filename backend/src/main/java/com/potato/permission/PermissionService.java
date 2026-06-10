package com.potato.permission;

import com.potato.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 统一授权判定。规则存 permission_rules 集合。
 * - admin 职能豁免一切
 * - 否则查 (resource, action) 规则,用户职能与 requiredFunctions 有交集即通过
 * - 无规则默认拒绝
 * MVP 先直查库;后续可加内存缓存(plan 已规划)。
 */
@Service
public class PermissionService {

    private final PermissionRuleRepository ruleRepository;

    public PermissionService(PermissionRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public boolean hasPermission(User user, String resource, String action) {
        if (user == null) return false;
        if (user.getFunctions().contains("admin")) return true;
        return ruleRepository.findByResourceAndAction(resource, action)
                .map(rule -> user.getFunctions().stream().anyMatch(rule.getRequiredFunctions()::contains))
                .orElse(false);
    }

    /** 无权限直接抛 403 */
    public void check(User user, String resource, String action) {
        if (!hasPermission(user, resource, action)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限执行该操作");
        }
    }
}
