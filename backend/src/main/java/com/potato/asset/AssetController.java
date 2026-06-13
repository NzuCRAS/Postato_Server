package com.potato.asset;

import com.potato.permission.PermissionService;
import com.potato.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 资产库:OSS 对象可视化(列桶 + 交叉引用 + 标孤儿)。权限复用 wiki/read。 */
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;
    private final PermissionService permissionService;

    public AssetController(AssetService assetService, PermissionService permissionService) {
        this.assetService = assetService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<AssetView> list(@AuthenticationPrincipal User user) throws Exception {
        permissionService.check(user, "wiki", "read");
        return assetService.listAssets();
    }
}
