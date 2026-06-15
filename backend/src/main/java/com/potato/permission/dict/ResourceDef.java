package com.potato.permission.dict;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/** 已注册资源字典。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document("permission_resources")
public class ResourceDef extends PermissionDef {
}
