package com.potato.permission;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Document("permission_rules")
public class PermissionRule {

    @Id
    private String id;

    private String resource;
    private String action;

    /** 允许的职能列表;用户职能与之有交集即通过 */
    private List<String> requiredFunctions = new ArrayList<>();
}
