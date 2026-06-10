package com.potato.devplan;

import com.potato.auth.AuthChannel;
import com.potato.devplan.DevPlanDtos.AddCorrectionRequest;
import com.potato.devplan.DevPlanDtos.CreateDevPlanRequest;
import com.potato.devplan.DevPlanDtos.UpdateNodeRequest;
import com.potato.devplan.DevPlanDtos.UpdateNodeResponse;
import com.potato.devplan.DevPlanDtos.UpdateResult;
import com.potato.permission.PermissionService;
import com.potato.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/requirements/{reqId}/dev-plan")
public class DevPlanController {

    private final DevPlanService service;
    private final PermissionService permissionService;

    public DevPlanController(DevPlanService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @PostMapping
    public DevPlan create(@AuthenticationPrincipal User user,
                          @PathVariable String reqId,
                          @RequestBody CreateDevPlanRequest req) {
        permissionService.check(user, "dev_plan", "create");
        return service.create(reqId, req.rootTitle(), req.repo(), req.nodes());
    }

    @PatchMapping("/nodes/{nodeId}")
    public UpdateNodeResponse updateNode(@AuthenticationPrincipal User user,
                                         Authentication authentication,
                                         @PathVariable String reqId,
                                         @PathVariable String nodeId,
                                         @RequestBody UpdateNodeRequest req) {
        permissionService.check(user, "dev_plan", "update");
        UpdateResult r = service.updateNode(reqId, nodeId, req, AuthChannel.actorOf(authentication));
        return new UpdateNodeResponse(r.node(), r.warnings());
    }

    /** 新增纠偏:任何可查看该需求的人 */
    @PostMapping("/nodes/{nodeId}/corrections")
    public DevPlan.Correction addCorrection(@AuthenticationPrincipal User user,
                                            @PathVariable String reqId,
                                            @PathVariable String nodeId,
                                            @RequestBody AddCorrectionRequest req) {
        permissionService.check(user, "requirement", "view");
        return service.addCorrection(reqId, nodeId, user.getUsername(), req.message());
    }

    /** 标记纠偏已解决:development */
    @PatchMapping("/nodes/{nodeId}/corrections/{correctionId}")
    public DevPlan.Correction resolveCorrection(@AuthenticationPrincipal User user,
                                                @PathVariable String reqId,
                                                @PathVariable String nodeId,
                                                @PathVariable String correctionId) {
        permissionService.check(user, "dev_plan", "update");
        return service.resolveCorrection(reqId, nodeId, correctionId);
    }

    /** 在父节点下追加子节点:development */
    @PostMapping("/nodes/{parentId}/children")
    public DevPlan.Node addNodes(@AuthenticationPrincipal User user,
                                 @PathVariable String reqId,
                                 @PathVariable String parentId,
                                 @RequestBody DevPlanDtos.AddNodesRequest req) {
        permissionService.check(user, "dev_plan", "update");
        return service.addNodes(reqId, parentId, req.nodes());
    }

    /** 重置(入档)当前开发计划:development。之后可重新 create。 */
    @PostMapping("/reset")
    public java.util.Map<String, Object> reset(@AuthenticationPrincipal User user,
                                               @PathVariable String reqId,
                                               @RequestBody(required = false) DevPlanDtos.ResetRequest req) {
        permissionService.check(user, "dev_plan", "update");
        service.resetPlan(reqId, req != null ? req.reason() : null);
        return java.util.Map.of("status", "reset");
    }

    /** 设置/更新进度树关联仓库:development */
    @PatchMapping("/repo")
    public DevPlan.Repo setRepo(@AuthenticationPrincipal User user,
                               @PathVariable String reqId,
                               @RequestBody DevPlan.Repo repo) {
        permissionService.check(user, "dev_plan", "update");
        return service.setRepo(reqId, repo);
    }
}
