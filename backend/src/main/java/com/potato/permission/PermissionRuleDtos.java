package com.potato.permission;

import java.util.List;

/** 权限规则 CRUD 的请求 DTO。 */
public class PermissionRuleDtos {

    public record CreateRuleRequest(String resource, String action, List<String> requiredFunctions) {
    }

    public record UpdateRuleRequest(List<String> requiredFunctions) {
    }
}
