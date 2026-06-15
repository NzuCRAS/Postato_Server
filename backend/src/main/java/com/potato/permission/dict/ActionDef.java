package com.potato.permission.dict;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/** 全局动作字典(跨资源复用)。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document("permission_actions")
public class ActionDef extends PermissionDef {
}
