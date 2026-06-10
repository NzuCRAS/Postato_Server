package com.potato.devplan;

import com.potato.devplan.DevPlanDtos.AddCorrectionRequest;
import com.potato.devplan.DevPlanDtos.CreateDevPlanRequest;
import com.potato.devplan.DevPlanDtos.UpdateNodeRequest;
import com.potato.devplan.DevPlanDtos.UpdateNodeResponse;
import com.potato.devplan.DevPlanDtos.UpdateResult;
import com.potato.permission.PermissionService;
import com.potato.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
        UpdateResult r = service.updateNode(reqId, nodeId, req, actorOf(authentication));
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

    /** API Key 渠道 → ai;JWT 渠道 → human(authority 由 AuthTokenFilter 注入) */
    private String actorOf(Authentication authentication) {
        if (authentication != null) {
            for (GrantedAuthority a : authentication.getAuthorities()) {
                if ("CHANNEL_APIKEY".equals(a.getAuthority())) return "ai";
            }
        }
        return "human";
    }
}
