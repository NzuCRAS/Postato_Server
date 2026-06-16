package com.potato.archgraph;

import com.potato.archgraph.ArchGraphDtos.Graph;
import com.potato.archnode.ArchNode;
import com.potato.archnode.ArchNodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 架构图谱服务:模块 + 依赖边 + 模块档案(需求/技术/经验索引)。
 * 替代旧 ArchNodeService 的分类树;旧 arch_nodes 经 archiveLegacyTree 归档不删。
 */
@Service
public class ArchGraphService {

    private static final Set<String> VALID_IMPL = Set.of("planned", "in_progress", "done");
    private static final Set<String> VALID_DOC_TYPE = Set.of("requirement", "tech_doc", "experience");

    private final ArchModuleRepository moduleRepository;
    private final ArchEdgeRepository edgeRepository;
    private final ArchNodeRepository legacyRepository;

    public ArchGraphService(ArchModuleRepository moduleRepository, ArchEdgeRepository edgeRepository,
                            ArchNodeRepository legacyRepository) {
        this.moduleRepository = moduleRepository;
        this.edgeRepository = edgeRepository;
        this.legacyRepository = legacyRepository;
    }

    // ---- 模块 ----

    public ArchModule upsertModule(String projectId, String key, String title, String description,
                                   String group, String implStatus, List<String> relatedCode, Integer order) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模块 key 必填");
        }
        String impl = normalizeImpl(implStatus);
        ArchModule m = moduleRepository.findByProjectIdAndKey(projectId, key).orElseGet(ArchModule::new);
        boolean isNew = m.getId() == null;
        if (isNew) {
            m.setProjectId(projectId);
            m.setKey(key.trim());
            m.setCreatedAt(Instant.now());
        }
        if (title != null) m.setTitle(title);
        if (description != null) m.setDescription(description);
        if (group != null) m.setGroup(group);
        if (impl != null) m.setImplStatus(impl);
        if (relatedCode != null) m.setRelatedCode(new ArrayList<>(relatedCode));
        if (order != null) m.setOrder(order);
        m.setUpdatedAt(Instant.now());
        return moduleRepository.save(m);
    }

    /** 删模块 + 清理涉及它的所有依赖边(档案随模块删除)。 */
    public void deleteModule(String projectId, String key) {
        ArchModule m = getModule(projectId, key);
        List<ArchEdge> related = new ArrayList<>();
        related.addAll(edgeRepository.findByProjectIdAndFrom(projectId, key));
        related.addAll(edgeRepository.findByProjectIdAndTo(projectId, key));
        edgeRepository.deleteAll(related);
        moduleRepository.delete(m);
    }

    public ArchModule getModule(String projectId, String key) {
        return moduleRepository.findByProjectIdAndKey(projectId, key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模块不存在: " + key));
    }

    // ---- 依赖边 ----

    public ArchEdge upsertEdge(String projectId, String from, String to, String kind, String label) {
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from/to 必填");
        }
        String k = (kind == null || kind.isBlank()) ? "depends" : kind.trim();
        if (!moduleRepository.existsByProjectIdAndKey(projectId, from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "源模块未注册: " + from);
        }
        if (!moduleRepository.existsByProjectIdAndKey(projectId, to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标模块未注册: " + to);
        }
        ArchEdge e = edgeRepository.findByProjectIdAndFromAndToAndKind(projectId, from, to, k).orElseGet(ArchEdge::new);
        if (e.getId() == null) {
            e.setProjectId(projectId);
            e.setFrom(from.trim());
            e.setTo(to.trim());
            e.setKind(k);
            e.setCreatedAt(Instant.now());
        }
        e.setLabel(label);
        e.setUpdatedAt(Instant.now());
        return edgeRepository.save(e);
    }

    public void deleteEdge(String id) {
        ArchEdge e = edgeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "边不存在"));
        edgeRepository.delete(e);
    }

    // ---- 模块档案(索引)----

    /**
     * 把一篇文档索引到 scope 里的每个模块(跨模块文档两端都挂,带 scope 标注)。
     * 同一模块内按 (type, ref) 去重(再次 relate 更新 title/scope)。
     */
    public void relateDoc(String projectId, String type, String ref, String title, List<String> scope) {
        if (type == null || !VALID_DOC_TYPE.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文档类型: " + type + "(requirement|tech_doc|experience)");
        }
        if (ref == null || ref.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ref 必填");
        }
        if (scope == null || scope.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope 至少含一个模块 key");
        }
        for (String key : scope) {
            ArchModule m = getModule(projectId, key);
            ArchModule.DocIndex item = m.getDocs().stream()
                    .filter(d -> type.equals(d.getType()) && ref.equals(d.getRef()))
                    .findFirst()
                    .orElseGet(() -> {
                        ArchModule.DocIndex d = new ArchModule.DocIndex();
                        d.setType(type);
                        d.setRef(ref.trim());
                        m.getDocs().add(d);
                        return d;
                    });
            if (title != null) item.setTitle(title);
            item.setScope(new ArrayList<>(scope));
            m.setUpdatedAt(Instant.now());
            moduleRepository.save(m);
        }
    }

    // ---- 整图 ----

    public Graph getGraph(String projectId) {
        return new Graph(
                moduleRepository.findByProjectIdOrderByOrderAsc(projectId),
                edgeRepository.findByProjectId(projectId));
    }

    // ---- 旧树归档(废弃重建)----

    /** 把旧 arch_nodes 全部标记 archived(不物理删,保留追溯)。返回归档条数。 */
    public int archiveLegacyTree(String projectId) {
        List<ArchNode> nodes = legacyRepository.findByProjectIdOrderByPathAsc(projectId);
        int n = 0;
        for (ArchNode node : nodes) {
            if (!"archived".equals(node.getStatus())) {
                node.setStatus("archived");
                node.setUpdatedAt(Instant.now());
                n++;
            }
        }
        legacyRepository.saveAll(nodes);
        return n;
    }

    private String normalizeImpl(String implStatus) {
        if (implStatus == null || implStatus.isBlank()) return null;
        String s = implStatus.trim();
        if (!VALID_IMPL.contains(s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 impl_status: " + implStatus + "(planned|in_progress|done)");
        }
        return s;
    }
}
