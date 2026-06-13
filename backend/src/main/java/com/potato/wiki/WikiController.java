package com.potato.wiki;

import com.potato.permission.PermissionService;
import com.potato.storage.StorageService;
import com.potato.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wiki")
public class WikiController {

    private final WikiService service;
    private final PermissionService permissionService;
    private final StorageService storageService;

    public WikiController(WikiService service, PermissionService permissionService, StorageService storageService) {
        this.service = service;
        this.permissionService = permissionService;
        this.storageService = storageService;
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
        return service.create(req.title(), req.path(), req.parentPath(), req.content(), req.category(), req.tags(), user.getId());
    }

    /** 按 path upsert(知识沉淀 / 技术方案用):命中更新、否则创建。 */
    @PostMapping("/pages/upsert")
    public WikiPage upsert(@AuthenticationPrincipal User user, @RequestBody WikiPageRequest req) {
        permissionService.check(user, "wiki", "edit");
        return service.upsertByPath(req.path(), req.title(), req.content(), req.category(), req.tags(), req.parentPath(), user.getId());
    }

    @PutMapping("/pages/{id}")
    public WikiPage update(@AuthenticationPrincipal User user, @PathVariable String id, @RequestBody WikiPageRequest req) {
        permissionService.check(user, "wiki", "edit");
        return service.update(id, req.title(), req.path(), req.content(), req.category(), req.tags(), req.parentPath(), user.getId());
    }

    /** 整目录移动/重命名:把 fromPrefix 目录及其下所有文档的路径前缀级联替换为 toPrefix。 */
    @PostMapping("/move-dir")
    public List<WikiPage> moveDir(@AuthenticationPrincipal User user, @RequestBody MoveDirRequest req) {
        permissionService.check(user, "wiki", "edit");
        return service.moveDir(req.fromPrefix(), req.toPrefix(), user.getId());
    }

    @GetMapping("/search")
    public List<WikiPage> search(@AuthenticationPrincipal User user,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(name = "match_mode", required = false) String matchMode,
                                 @RequestParam(name = "include_tmp", required = false, defaultValue = "false") boolean includeTmp,
                                 @RequestParam(name = "category", required = false) String category) {
        permissionService.check(user, "wiki", "read");
        return service.search(q, MatchMode.fromValue(matchMode), includeTmp, category);
    }

    /** 上传资产到某页(后端代理 → MinIO),返回更新后的页。 */
    @PostMapping("/pages/{id}/assets")
    public WikiPage uploadAsset(@AuthenticationPrincipal User user, @PathVariable String id,
                               @RequestPart("file") MultipartFile file) throws Exception {
        permissionService.check(user, "wiki", "edit");
        WikiPage.Asset asset = storageService.upload(id, file);
        return service.addAsset(id, asset);
    }

    /** 删除某页的一个资产(MinIO 对象 + 页记录),objectKey 作查询参数。 */
    @DeleteMapping("/pages/{id}/assets")
    public WikiPage deleteAsset(@AuthenticationPrincipal User user, @PathVariable String id,
                               @RequestParam("objectKey") String objectKey) throws Exception {
        permissionService.check(user, "wiki", "edit");
        storageService.delete(objectKey);
        return service.removeAsset(id, objectKey);
    }
}
