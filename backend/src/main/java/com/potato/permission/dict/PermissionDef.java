package com.potato.permission.dict;

import lombok.Data;
import org.springframework.data.annotation.Id;

/** 权限字典项基类:受控的 key + 可读 label + 说明。子类各对应一个集合。 */
@Data
public abstract class PermissionDef {

    @Id
    private String id;

    /** 受控标识(规则/用户引用的就是它),不可变 */
    private String key;

    /** 可读名 */
    private String label;

    private String description;
}
