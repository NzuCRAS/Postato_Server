package com.potato.archnode;

import com.potato.archnode.ArchNodeDtos.CreateNodeRequest;
import com.potato.archnode.ArchNodeDtos.MoveNodeRequest;
import com.potato.archnode.ArchNodeDtos.UpdateNodeRequest;
import com.potato.permission.PermissionService;
import com.potato.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects/{pid}/arch")
public class ArchNodeController {

    private final ArchNodeService service;
    private final PermissionService permissionService;

    public ArchNodeController(ArchNodeService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    /** 列出结构树(可按 tag/layer 过滤 → 命中节点 + 祖先链)。前端按 parentId/path 组装树。 */
    @GetMapping
    public List<ArchNode> list(@AuthenticationPrincipal User user,
                               @PathVariable String pid,
                               @RequestParam(required = false) String tag,
                               @RequestParam(required = false) String layer) {
        permissionService.check(user, "project", "view");
        return service.list(pid, tag, layer);
    }

    @PostMapping("/nodes")
    public ArchNode create(@AuthenticationPrincipal User user,
                           @PathVariable String pid,
                           @RequestBody CreateNodeRequest req) {
        permissionService.check(user, "arch", "edit_management");
        return service.create(pid, req, "manual", null);
    }

    @PatchMapping("/nodes/{nodeId}")
    public ArchNode update(@AuthenticationPrincipal User user,
                           @PathVariable String pid,
                           @PathVariable String nodeId,
                           @RequestBody UpdateNodeRequest req) {
        permissionService.check(user, "arch", "edit_management");
        return service.update(nodeId, req);
    }

    @PostMapping("/nodes/{nodeId}/archive")
    public Map<String, Object> archive(@AuthenticationPrincipal User user,
                                       @PathVariable String pid,
                                       @PathVariable String nodeId) {
        permissionService.check(user, "arch", "edit_management");
        int n = service.archive(nodeId);
        return Map.of("archived", n);
    }

    @PostMapping("/nodes/{nodeId}/move")
    public ArchNode move(@AuthenticationPrincipal User user,
                         @PathVariable String pid,
                         @PathVariable String nodeId,
                         @RequestBody MoveNodeRequest req) {
        permissionService.check(user, "arch", "edit_management");
        return service.move(nodeId, req.newParentId());
    }

    /** .project.yaml 同步(AI/CI 推送):幂等 reconcile L3+ 工程树。 */
    @PostMapping("/sync")
    public Map<String, Object> sync(@AuthenticationPrincipal User user,
                                    @PathVariable String pid,
                                    @RequestBody ArchNodeDtos.SyncRequest req) {
        permissionService.check(user, "arch", "sync");
        return service.sync(pid, req.repoId(), req.modules());
    }

    /** 写入一棵结构子树(管理树或任意层):递归 upsert、自动按父推断 layer、按 path 幂等。MCP upsert_architecture 的后端。 */
    @PostMapping("/upsert-tree")
    public Map<String, Object> upsertTree(@AuthenticationPrincipal User user,
                                          @PathVariable String pid,
                                          @RequestBody ArchNodeDtos.UpsertTreeRequest req) {
        permissionService.check(user, "arch", "edit_management");
        return service.upsertTree(pid, req.parentPath(), req.nodes());
    }
}
