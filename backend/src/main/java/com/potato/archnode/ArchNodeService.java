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

    private final ArchNodeRepository repository;

    public ArchNodeService(ArchNodeRepository repository) {
        this.repository = repository;
    }

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
}
