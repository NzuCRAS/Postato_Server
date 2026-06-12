package com.potato.archnode;

import com.potato.archnode.ArchNodeDtos.CreateNodeRequest;
import com.potato.archnode.ArchNodeDtos.SyncModule;
import com.potato.archnode.ArchNodeDtos.UpdateNodeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ArchNodeService {

    private static final Set<String> VALID_IMPL = Set.of("planned", "in_progress", "done");

    private final ArchNodeRepository repository;

    public ArchNodeService(ArchNodeRepository repository) {
        this.repository = repository;
    }

    /** 需求↔arch 关联结果:linked=节点是否存在并已关联;warnings=软提示。 */
    public record RelateResult(boolean linked, List<String> warnings) {}

    /**
     * 列出项目的活跃结构节点。带 tag/layer 过滤时返回命中节点 + 其祖先链(便于前端组装"按标签的跨切面动态树")。
     */
    public List<ArchNode> list(String projectId, String tag, String layer) {
        List<ArchNode> active = new ArrayList<>();
        for (ArchNode n : repository.findByProjectIdOrderByPathAsc(projectId)) {
            if (!"archived".equals(n.getStatus())) active.add(n);
        }
        boolean hasTag = tag != null && !tag.isBlank();
        boolean hasLayer = layer != null && !layer.isBlank();
        if (!hasTag && !hasLayer) return active;

        Set<String> keep = new LinkedHashSet<>();
        for (ArchNode n : active) {
            boolean tagOk = !hasTag || (n.getTags() != null && n.getTags().contains(tag));
            boolean layerOk = !hasLayer || layer.equals(n.getLayer());
            if (tagOk && layerOk) addWithAncestors(n.getPath(), keep);
        }
        List<ArchNode> result = new ArrayList<>();
        for (ArchNode n : active) if (keep.contains(n.getPath())) result.add(n);
        return result;
    }

    /** 把 path 及其所有祖先路径加入集合,如 /a/b/c → /a, /a/b, /a/b/c */
    private void addWithAncestors(String path, Set<String> set) {
        if (path == null || path.isBlank()) return;
        String[] parts = path.split("/");
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            cur.append("/").append(p);
            set.add(cur.toString());
        }
    }

    public ArchNode get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "结构节点不存在"));
    }

    public ArchNode create(String projectId, CreateNodeRequest in, String source, String repoId) {
        if (in.title() == null || in.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点标题必填");
        }
        String parentPath = "";
        if (in.parentId() != null && !in.parentId().isBlank()) {
            parentPath = get(in.parentId()).getPath();
        }
        String path = parentPath + "/" + in.title().trim();
        repository.findByProjectIdAndPath(projectId, path).ifPresent(x -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "结构路径已存在: " + path);
        });

        ArchNode n = new ArchNode();
        n.setProjectId(projectId);
        n.setParentId(in.parentId());
        n.setPath(path);
        n.setTitle(in.title().trim());
        n.setLayer(in.layer());
        n.setType(in.type());
        n.setDescription(in.description());
        if (in.tags() != null) n.setTags(in.tags());
        if (in.relatedDocs() != null) n.setRelatedDocs(in.relatedDocs());
        if (in.relatedCode() != null) n.setRelatedCode(in.relatedCode());
        if (in.relatedRequirements() != null) n.setRelatedRequirements(in.relatedRequirements());
        n.setSource(source != null ? source : "manual");
        n.setRepoId(repoId);
        n.setStatus("active");
        Instant now = Instant.now();
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        return repository.save(n);
    }

    public ArchNode update(String id, UpdateNodeRequest in) {
        ArchNode n = get(id);
        if (in.title() != null && !in.title().isBlank()) n.setTitle(in.title().trim());
        if (in.layer() != null) n.setLayer(in.layer());
        if (in.type() != null) n.setType(in.type());
        if (in.description() != null) n.setDescription(in.description());
        if (in.tags() != null) n.setTags(in.tags());
        if (in.relatedDocs() != null) n.setRelatedDocs(in.relatedDocs());
        if (in.relatedCode() != null) n.setRelatedCode(in.relatedCode());
        if (in.relatedRequirements() != null) n.setRelatedRequirements(in.relatedRequirements());
        n.setUpdatedAt(Instant.now());
        return repository.save(n);
    }

    /** 归档节点及其整棵子树(保留数据,留痕)。 */
    public int archive(String id) {
        ArchNode n = get(id);
        List<ArchNode> subtree = new ArrayList<>();
        subtree.add(n);
        subtree.addAll(repository.findByProjectIdAndPathStartingWith(n.getProjectId(), n.getPath() + "/"));
        Instant now = Instant.now();
        for (ArchNode x : subtree) {
            x.setStatus("archived");
            x.setUpdatedAt(now);
        }
        repository.saveAll(subtree);
        return subtree.size();
    }

    /** .project.yaml 同步:对某仓库声明的模块做幂等 reconcile(L3+ 工程树);消失的旧 sync 节点归档;不覆盖手动节点。 */
    public Map<String, Object> sync(String projectId, String repoId, List<SyncModule> modules) {
        if (repoId == null || repoId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repo_id 必填");
        }
        List<ArchNode> existingSync = repository.findByProjectIdAndRepoId(projectId, repoId);
        Set<String> incoming = new HashSet<>();
        List<ArchNode> toSave = new ArrayList<>();
        Instant now = Instant.now();
        int upserted = 0, archived = 0, skipped = 0;

        if (modules != null) {
            for (SyncModule m : modules) {
                if (m.node() == null || m.node().isBlank() || m.title() == null || m.title().isBlank()) {
                    skipped++;
                    continue;
                }
                Optional<ArchNode> parent = repository.findByProjectIdAndPath(projectId, m.node());
                if (parent.isEmpty()) {
                    skipped++; // 父管理节点不存在,跳过
                    continue;
                }
                String path = m.node() + "/" + m.title().trim();
                incoming.add(path);
                Optional<ArchNode> at = repository.findByProjectIdAndPath(projectId, path);
                if (at.isPresent() && !"sync".equals(at.get().getSource())) {
                    skipped++; // 不覆盖手动节点
                    continue;
                }
                ArchNode n = at.orElseGet(ArchNode::new);
                if (n.getCreatedAt() == null) {
                    n.setProjectId(projectId);
                    n.setPath(path);
                    n.setParentId(parent.get().getId());
                    n.setLayer(nextLayer(parent.get().getLayer()));
                    n.setCreatedAt(now);
                }
                n.setTitle(m.title().trim());
                n.setType(m.type());
                n.setSource("sync");
                n.setRepoId(repoId);
                n.setStatus("active");
                if (m.tags() != null) n.setTags(m.tags());
                if (m.relatedDocs() != null) n.setRelatedDocs(m.relatedDocs());
                if (m.relatedCode() != null) n.setRelatedCode(m.relatedCode());
                n.setUpdatedAt(now);
                toSave.add(n);
                upserted++;
            }
        }
        for (ArchNode n : existingSync) {
            if (!incoming.contains(n.getPath()) && "active".equals(n.getStatus())) {
                n.setStatus("archived");
                n.setUpdatedAt(now);
                toSave.add(n);
                archived++;
            }
        }
        repository.saveAll(toSave);
        return Map.of("upserted", upserted, "archived", archived, "skipped", skipped);
    }

    /**
     * 递归 upsert 一棵结构子树(管理树或任意层)。parent_path 为空挂到根;
     * 按 (projectId, path) 幂等:命中则更新字段并复活(保留原 source),否则新建 source=manual。
     * layer 显式优先,缺省按父层 +1(根 = L0)。逐节点即时保存以便子节点取到父 id(自动建中间层)。
     * 返回 {created, updated, paths}。
     */
    public Map<String, Object> upsertTree(String projectId, String parentPath, List<ArchNodeDtos.TreeNode> nodes) {
        ArchNode parent = null;
        if (parentPath != null && !parentPath.isBlank()) {
            parent = repository.findByProjectIdAndPath(projectId, parentPath)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "父路径不存在: " + parentPath));
        }
        List<String> paths = new ArrayList<>();
        int[] counts = {0, 0}; // [created, updated]
        if (nodes != null) {
            for (ArchNodeDtos.TreeNode tn : nodes) {
                upsertRecursive(projectId, parent, tn, paths, counts);
            }
        }
        if (parent != null) propagateUp(parent);   // 父及祖先按新增/变更的子节点链式重算 impl_status
        Map<String, Object> res = new HashMap<>();
        res.put("created", counts[0]);
        res.put("updated", counts[1]);
        res.put("paths", paths);
        return res;
    }

    private void upsertRecursive(String projectId, ArchNode parent, ArchNodeDtos.TreeNode tn,
                                 List<String> paths, int[] counts) {
        if (tn.title() == null || tn.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点标题必填");
        }
        String parentPath = parent == null ? "" : parent.getPath();
        String parentId = parent == null ? null : parent.getId();
        String parentLayer = parent == null ? null : parent.getLayer();
        String path = parentPath + "/" + tn.title().trim();

        ArchNode n = repository.findByProjectIdAndPath(projectId, path).orElse(null);
        Instant now = Instant.now();
        if (n == null) {
            n = new ArchNode();
            n.setProjectId(projectId);
            n.setPath(path);
            n.setSource("manual");
            n.setCreatedAt(now);
            n.setImplStatus(tn.implStatus() != null && !tn.implStatus().isBlank() ? tn.implStatus() : "planned");
            counts[0]++;
        } else {
            if (tn.implStatus() != null && !tn.implStatus().isBlank()) n.setImplStatus(tn.implStatus());
            counts[1]++;
        }
        n.setParentId(parentId);
        n.setStatus("active");   // 复活被归档的同路径节点
        n.setTitle(tn.title().trim());
        n.setLayer(tn.layer() != null && !tn.layer().isBlank() ? tn.layer() : autoLayer(parentLayer));
        if (tn.type() != null) n.setType(tn.type());
        if (tn.description() != null) n.setDescription(tn.description());
        if (tn.tags() != null) n.setTags(tn.tags());
        if (tn.relatedDocs() != null) n.setRelatedDocs(tn.relatedDocs());
        if (tn.relatedCode() != null) n.setRelatedCode(tn.relatedCode());
        n.setUpdatedAt(now);
        ArchNode saved = repository.save(n);
        paths.add(path);

        if (tn.children() != null && !tn.children().isEmpty()) {
            for (ArchNodeDtos.TreeNode child : tn.children()) {
                upsertRecursive(projectId, saved, child, paths, counts);
            }
            recompute(saved);   // 现在是非叶子,按子节点聚合 impl_status
        }
    }

    /** 缺省层级:根(无父)= L0,否则父层 +1。 */
    private String autoLayer(String parentLayer) {
        if (parentLayer == null) return "L0";
        return nextLayer(parentLayer);
    }

    /** 重算非叶子节点的 impl_status(基于活跃直接子);叶子(无活跃子)不变。返回是否变化。 */
    private boolean recompute(ArchNode node) {
        List<ArchNode> kids = activeChildren(node.getProjectId(), node.getId());
        if (kids.isEmpty()) return false;   // 叶子:状态由标注决定,不聚合
        boolean allDone = true, allPlanned = true;
        for (ArchNode k : kids) {
            if (!"done".equals(k.getImplStatus())) allDone = false;
            if (!"planned".equals(k.getImplStatus())) allPlanned = false;
        }
        String agg = allDone ? "done" : (allPlanned ? "planned" : "in_progress");
        if (!agg.equals(node.getImplStatus())) {
            node.setImplStatus(agg);
            node.setUpdatedAt(Instant.now());
            repository.save(node);
            return true;
        }
        return false;
    }

    private List<ArchNode> activeChildren(String projectId, String parentId) {
        List<ArchNode> all = repository.findByProjectIdAndParentId(projectId, parentId);
        List<ArchNode> active = new ArrayList<>();
        if (all != null) {
            for (ArchNode k : all) if (!"archived".equals(k.getStatus())) active.add(k);
        }
        return active;
    }

    /** 从 start 自身开始重算 impl_status 并逐级向上;某级状态未变即停(祖先不受影响)。 */
    private void propagateUp(ArchNode start) {
        ArchNode cur = start;
        while (cur != null) {
            boolean changed = recompute(cur);
            if (!changed) break;
            String pid = cur.getParentId();
            cur = (pid != null) ? repository.findById(pid).orElse(null) : null;
        }
    }

    private String nextLayer(String parentLayer) {
        if ("L0".equals(parentLayer)) return "L1";
        if ("L1".equals(parentLayer)) return "L2";
        if ("L2".equals(parentLayer)) return "L3";
        return "L4";
    }

    /** 移动节点到新父节点,递归重算该节点及子树的物化路径。 */
    public ArchNode move(String id, String newParentId) {
        ArchNode n = get(id);
        String oldPath = n.getPath();
        String newParentPath = "";
        if (newParentId != null && !newParentId.isBlank()) {
            ArchNode parent = get(newParentId);
            if (parent.getPath().startsWith(oldPath + "/") || parent.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能移动到自身或子孙节点下");
            }
            newParentPath = parent.getPath();
        }
        String newPath = newParentPath + "/" + n.getTitle();
        repository.findByProjectIdAndPath(n.getProjectId(), newPath).ifPresent(x -> {
            if (!x.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "目标路径已存在: " + newPath);
            }
        });

        List<ArchNode> descendants = repository.findByProjectIdAndPathStartingWith(n.getProjectId(), oldPath + "/");
        Instant now = Instant.now();
        n.setParentId(newParentId);
        n.setPath(newPath);
        n.setUpdatedAt(now);
        List<ArchNode> toSave = new ArrayList<>();
        toSave.add(n);
        for (ArchNode d : descendants) {
            d.setPath(newPath + d.getPath().substring(oldPath.length()));
            d.setUpdatedAt(now);
            toSave.add(d);
        }
        repository.saveAll(toSave);
        return n;
    }

    /**
     * ⑩ 需求↔结构树联动:把需求关联到 arch 节点(arch 侧 related_requirements 去重),并按需回标 impl_status。
     * impl_status 非法 → 400;path 空/节点不存在 → 记 warning 跳过(linked=false);
     * 叶子 → 直接设 impl_status + 对父 propagateUp(祖先链式聚合重算);
     * 非叶子 → 状态由子聚合,不直接设,记一条 warning(linked=true,关联仍建立)。
     */
    public RelateResult relateAndMark(String projectId, String path, String implStatus, String reqId) {
        List<String> warnings = new ArrayList<>();
        if (implStatus != null && !implStatus.isBlank() && !VALID_IMPL.contains(implStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 impl_status: " + implStatus);
        }
        if (path == null || path.isBlank()) {
            warnings.add("跳过空 arch_path");
            return new RelateResult(false, warnings);
        }
        Optional<ArchNode> opt = repository.findByProjectIdAndPath(projectId, path);
        if (opt.isEmpty()) {
            warnings.add("结构树节点不存在,跳过: " + path);
            return new RelateResult(false, warnings);
        }
        ArchNode n = opt.get();
        if (reqId != null && !reqId.isBlank() && !n.getRelatedRequirements().contains(reqId)) {
            n.getRelatedRequirements().add(reqId);
        }
        Instant now = Instant.now();
        if (implStatus != null && !implStatus.isBlank()) {
            if (activeChildren(projectId, n.getId()).isEmpty()) {
                n.setImplStatus(implStatus);          // 叶子:直接标注
                n.setUpdatedAt(now);
                repository.save(n);
                if (n.getParentId() != null) {        // 祖先链式聚合重算
                    ArchNode parent = repository.findById(n.getParentId()).orElse(null);
                    if (parent != null) propagateUp(parent);
                }
            } else {
                warnings.add("节点有子节点,impl_status 由子聚合,未直接设: " + path);
                n.setUpdatedAt(now);
                repository.save(n);
            }
        } else {
            n.setUpdatedAt(now);
            repository.save(n);
        }
        return new RelateResult(true, warnings);
    }
}
