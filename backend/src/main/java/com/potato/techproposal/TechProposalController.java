package com.potato.techproposal;

import com.potato.auth.AuthChannel;
import com.potato.permission.PermissionService;
import com.potato.techproposal.TechProposalDtos.CreateRequest;
import com.potato.techproposal.TechProposalDtos.TechProposalResponse;
import com.potato.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/requirements/{reqId}/tech-proposals")
public class TechProposalController {

    private final TechProposalService service;
    private final PermissionService permissionService;

    public TechProposalController(TechProposalService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @PostMapping
    public TechProposalResponse create(@AuthenticationPrincipal User user,
                                       Authentication authentication,
                                       @PathVariable String reqId,
                                       @RequestBody CreateRequest req) {
        permissionService.check(user, "dev_plan", "update");
        TechProposalService.Result r = service.create(
                reqId, req.nodeId(), req.title(), req.content(), req.tags(),
                Boolean.TRUE.equals(req.markInProgress()), AuthChannel.actorOf(authentication), user.getId());
        return new TechProposalResponse(r.proposalPath(), r.node());
    }
}
