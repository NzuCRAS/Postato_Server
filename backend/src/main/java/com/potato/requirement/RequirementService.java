package com.potato.requirement;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class RequirementService {

    private static final Set<String> VALID_STATUSES = Set.of("draft", "clarifying", "confirmed", "done", "deprecated");
    private static final Set<String> VALID_TYPES = Set.of("feature", "improvement", "bugfix");
    private static final Set<String> VALID_TIERS = Set.of("Large", "Medium", "Small");
    private static final String DEFAULT_TIER = "Medium";
    private static final String DEFAULT_PROJECT = "default";

    private final RequirementRepository repository;
    private final com.potato.archnode.ArchNodeService archNodeService;

    public RequirementService(RequirementRepository repository,
                              com.potato.archnode.ArchNodeService archNodeService) {
        this.repository = repository;
        this.archNodeService = archNodeService;
    }

    public List<Requirement> list(String status, String projectId) {
        boolean hasProject = projectId != null && !projectId.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasProject && hasStatus) return repository.findByProjectIdAndStatus(projectId, status);
        if (hasProject) return repository.findByProjectIdOrderByUpdatedAtDesc(projectId);
        if (hasStatus) return repository.findByStatus(status);
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    public Requirement get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在"));
    }

    public Requirement create(String title, String descriptionMd, Structured structured, String status,
                              String projectId, List<com.potato.common.DocLink> docLinks,
                              String type, String tier, String userId) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题必填");
        }
        Requirement r = new Requirement();
        r.setProjectId(projectId != null && !projectId.isBlank() ? projectId : DEFAULT_PROJECT);
        r.setTitle(title);
        r.setDescriptionMd(descriptionMd);
        r.setStructured(structured != null ? structured : new Structured());
        if (docLinks != null) r.setDocLinks(docLinks);
        r.setStatus(normalizeStatus(status, "draft"));
        r.setType(validateType(type));
        r.setTier(normalizeTier(tier));
        r.setVersion(1);
        r.setCreatedBy(userId);
        Instant now = Instant.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        return repository.save(r);
    }

    public Requirement update(String id, String title, String descriptionMd, Structured structured,
                              List<com.potato.common.DocLink> docLinks) {
        Requirement r = get(id);
        if (title != null) r.setTitle(title);
        if (descriptionMd != null) r.setDescriptionMd(descriptionMd);
        if (docLinks != null) r.setDocLinks(docLinks);
        if (structured != null) {
            r.setStructured(structured);
            r.setVersion(r.getVersion() + 1); // structured 变更时版本自增
        }
        r.setUpdatedAt(Instant.now());
        return repository.save(r);
    }

    public Requirement updateStatus(String id, String status) {
        Requirement r = get(id);
        r.setStatus(normalizeStatus(status, null));
        r.setUpdatedAt(Instant.now());
        return repository.save(r);
    }

    /**
     * ⑩ 需求完成回标:为需求建立到结构树节点的双向关联,并按需回标各叶子节点 impl_status。
     * 委托 ArchNodeService 处理 arch 侧(关联去重 + 叶子回标 + 祖先聚合);需求侧记录关联路径(去重,仅节点存在时)。
     * 返回更新后的关联列表 + 汇总软警告(不阻断)。
     */
    public RelateArchResponse relateArch(String reqId, List<ArchLink> links) {
        Requirement r = get(reqId);
        if (r.getRelatedArchNodes() == null) r.setRelatedArchNodes(new ArrayList<>());
        List<String> warnings = new ArrayList<>();
        if (links != null) {
            for (ArchLink link : links) {
                com.potato.archnode.ArchNodeService.RelateResult rr =
                        archNodeService.relateAndMark(r.getProjectId(), link.archPath(), link.implStatus(), reqId);
                if (rr.linked() && link.archPath() != null && !r.getRelatedArchNodes().contains(link.archPath())) {
                    r.getRelatedArchNodes().add(link.archPath());
                }
                warnings.addAll(rr.warnings());
            }
        }
        r.setUpdatedAt(Instant.now());
        repository.save(r);
        return new RelateArchResponse(r.getRelatedArchNodes(), warnings);
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            if (fallback != null) return fallback;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 必填");
        }
        if (!VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法状态: " + status);
        }
        return status;
    }

    /** 校验 type(可空,描述用):null/blank 放行;非法值 400。 */
    private String validateType(String type) {
        if (type == null || type.isBlank()) return null;
        if (!VALID_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 type: " + type + "(允许 feature/improvement/bugfix)");
        }
        return type;
    }

    /** tier 建议创建时选;null/blank → 默认 Medium;非法值 400。仅供参考,不硬门。 */
    private String normalizeTier(String tier) {
        if (tier == null || tier.isBlank()) return DEFAULT_TIER;
        if (!VALID_TIERS.contains(tier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 tier: " + tier + "(允许 Large/Medium/Small)");
        }
        return tier;
    }
}
