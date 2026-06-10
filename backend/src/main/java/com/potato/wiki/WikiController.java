package com.potato.wiki;

import com.potato.permission.PermissionService;
import com.potato.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wiki")
public class WikiController {

    private final WikiService service;
    private final PermissionService permissionService;

    public WikiController(WikiService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @GetMapping("/pages")
    public List<WikiPage> list(@AuthenticationPrincipal User user) {
        permissionService.check(user, "wiki", "read");
        return service.list();
    }

    @GetMapping("/pages/{id}")
    public WikiPage get(@AuthenticationPrincipal User user, @PathVariable String id) {
        permissionService.check(user, "wiki", "read");
        return service.get(id);
    }

    @PostMapping("/pages")
    public WikiPage create(@AuthenticationPrincipal User user, @RequestBody WikiPageRequest req) {
        permissionService.check(user, "wiki", "edit");
        return service.create(req.title(), req.path(), req.parentPath(), req.content(), req.tags(), user.getId());
    }

    /** 按 path upsert(知识沉淀 / 技术方案用):命中更新、否则创建。 */
    @PostMapping("/pages/upsert")
    public WikiPage upsert(@AuthenticationPrincipal User user, @RequestBody WikiPageRequest req) {
        permissionService.check(user, "wiki", "edit");
        return service.upsertByPath(req.path(), req.title(), req.content(), req.tags(), req.parentPath(), user.getId());
    }

    @PutMapping("/pages/{id}")
    public WikiPage update(@AuthenticationPrincipal User user, @PathVariable String id, @RequestBody WikiPageRequest req) {
        permissionService.check(user, "wiki", "edit");
        return service.update(id, req.title(), req.content(), req.tags(), req.parentPath(), user.getId());
    }

    @GetMapping("/search")
    public List<WikiPage> search(@AuthenticationPrincipal User user,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(name = "match_mode", required = false) String matchMode,
                                 @RequestParam(name = "include_tmp", required = false, defaultValue = "false") boolean includeTmp) {
        permissionService.check(user, "wiki", "read");
        return service.search(q, MatchMode.fromValue(matchMode), includeTmp);
    }
}
