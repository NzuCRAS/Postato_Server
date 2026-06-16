package com.potato.run;

import com.potato.permission.PermissionService;
import com.potato.run.RunDtos.AdvanceResult;
import com.potato.run.RunDtos.CompleteRequest;
import com.potato.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SOP 执行工作流:平台驱动的步骤机。写操作走 dev_plan/update。 */
@RestController
@RequestMapping("/api/v1/runs")
public class RunController {

    private final RunService service;
    private final PermissionService permissionService;

    public RunController(RunService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    /** 取当前 Run(无则新建)。 */
    @GetMapping
    public SopRun get(@AuthenticationPrincipal User user, @RequestParam String reqId) {
        permissionService.check(user, "project", "view");
        return service.getOrStart(reqId);
    }

    /** 推进:返回当前步 + 注入文档 + 上一步结果。 */
    @PostMapping("/advance")
    public AdvanceResult advance(@AuthenticationPrincipal User user, @RequestParam String reqId) {
        permissionService.check(user, "dev_plan", "update");
        return service.advance(reqId);
    }

    /** 登记当前步(按序;skipped 必填原因)。 */
    @PostMapping("/complete")
    public SopRun complete(@AuthenticationPrincipal User user, @RequestParam String reqId,
                           @RequestBody CompleteRequest req) {
        permissionService.check(user, "dev_plan", "update");
        return service.complete(reqId, req.note(), req.status(), req.skipReason());
    }

    /** 收尾:校验全步走过,组装 runlog 落 wiki。 */
    @PostMapping("/finish")
    public SopRun finish(@AuthenticationPrincipal User user, @RequestParam String reqId) {
        permissionService.check(user, "dev_plan", "update");
        return service.finish(reqId, user.getId());
    }
}
