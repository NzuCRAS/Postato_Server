package com.potato.permission.dict;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/** 职能字典(原 functions,受控注册制)。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document("permission_functions")
public class FunctionDef extends PermissionDef {
}
