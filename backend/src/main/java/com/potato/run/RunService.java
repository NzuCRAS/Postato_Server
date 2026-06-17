package com.potato.run;

import com.potato.archgraph.ArchGraphDtos.Graph;
import com.potato.archgraph.ArchGraphService;
import com.potato.requirement.Requirement;
import com.potato.requirement.RequirementRepository;
import com.potato.requirement.Structured;
import com.potato.run.RunDtos.AdvanceResult;
import com.potato.run.RunDtos.DocInject;
import com.potato.run.RunDtos.PrevStep;
import com.potato.wiki.MatchMode;
import com.potato.wiki.WikiPage;
import com.potato.wiki.WikiService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SOP 执行工作流服务:平台驱动 LLM 逐步走 SOP——每步注入文档、实时留痕、硬校验不跳步。
 * finish 时把累积步骤组装成 runlog 落 wiki(category=runlog)。与 dev_plan 分层不复制。
 */
@Service
public class RunService {

    /** 固定 SOP 步骤序列(分流 + 九步收尾);不按 tier 删步,该免的标 skipped+原因。 */
    private record StepDef(String key, String title) {
    }

    private static final List<StepDef> STEPS = List.of(
            new StepDef("triage", "开工分流(按 tier 定流程强度)"),
            new StepDef("understand_req", "弄清需求"),
            new StepDef("understand_code", "摸代码与架构约束"),
            new StepDef("inject_standard", "注入代码规范"),
            new StepDef("find_assets", "查可复用资产"),
            new StepDef("find_experience", "检索先验经验"),
            new StepDef("plan", "制定实现计划"),
            new StepDef("implement", "逐节点实现(产物见 dev_plan)"),
            new StepDef("handle_warnings", "处理 warnings"),
            new StepDef("wrap_up", "收尾沉淀 + 回标"));

    private static final Set<String> VALID_STEP_STATUS = Set.of("done", "skipped");

    /** 注入候选数量上限(asset/experience 按相关性取 TopK,余下交 LLM 自检索)。 */
    private static final int INJECT_TOP_K = 5;

    private final SopRunRepository runRepository;
    private final RequirementRepository requirementRepository;
    private final WikiService wikiService;
    private final ArchGraphService archGraphService;

    public RunService(SopRunRepository runRepository, RequirementRepository requirementRepository,
                      WikiService wikiService, ArchGraphService archGraphService) {
        this.runRepository = runRepository;
        this.requirementRepository = requirementRepository;
        this.wikiService = wikiService;
        this.archGraphService = archGraphService;
    }

    /** 取当前 running run;无则按需求新建一份(实例化步骤模板)。 */
    public SopRun getOrStart(String reqId) {
        return runRepository.findFirstByReqIdAndStatusOrderByCreatedAtDesc(reqId, "running")
                .orElseGet(() -> {
                    Requirement req = requirementRepository.findById(reqId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在: " + reqId));
                    SopRun run = new SopRun();
                    run.setReqId(reqId);
                    run.setProjectId(req.getProjectId());
                    run.setTier(req.getTier());
                    run.setStatus("running");
                    run.setCurrentStepIndex(0);
                    List<SopRun.RunStep> steps = new ArrayList<>();
                    for (StepDef d : STEPS) {
                        SopRun.RunStep s = new SopRun.RunStep();
                        s.setKey(d.key());
                        s.setTitle(d.title());
                        steps.add(s);
                    }
                    run.setSteps(steps);
                    Instant now = Instant.now();
                    run.setCreatedAt(now);
                    run.setUpdatedAt(now);
                    return runRepository.save(run);
                });
    }

    /** 只读取最新 Run(不创建);供前端展示。 */
    public SopRun findLatest(String reqId) {
        return runRepository.findByReqIdOrderByCreatedAtDesc(reqId).stream().findFirst().orElse(null);
    }

    /** 推进:返回当前步 + 平台注入的文档 + 上一步结果摘要(不改变步状态)。 */
    public AdvanceResult advance(String reqId) {
        SopRun run = getOrStart(reqId);
        if (!"running".equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Run 已结束: " + run.getStatus());
        }
        int idx = run.getCurrentStepIndex();
        int total = run.getSteps().size();
        SopRun.RunStep cur = idx < total ? run.getSteps().get(idx) : null;
        List<DocInject> docs = cur != null ? injectFor(run, cur.getKey()) : List.of();
        if (cur != null) {
            for (DocInject d : docs) {
                if (d.ref() != null && !cur.getInjectedDocs().contains(d.ref())) cur.getInjectedDocs().add(d.ref());
            }
            run.setUpdatedAt(Instant.now());
            runRepository.save(run);
        }
        PrevStep prev = idx > 0
                ? toPrev(run.getSteps().get(idx - 1))
                : null;
        boolean allDone = run.getSteps().stream().noneMatch(s -> "pending".equals(s.getStatus()));
        return new AdvanceResult(reqId, run.getStatus(), idx, total, cur, prev, docs, allDone);
    }

    /** 登记当前步结果(按序;skipped 必填原因),游标后移。 */
    public SopRun complete(String reqId, String note, String status, String skipReason) {
        SopRun run = getOrStart(reqId);
        if (!"running".equals(run.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Run 已结束: " + run.getStatus());
        }
        int idx = run.getCurrentStepIndex();
        if (idx >= run.getSteps().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所有步骤已登记,请调用 finish");
        }
        String st = (status == null || status.isBlank()) ? "done" : status.trim();
        if (!VALID_STEP_STATUS.contains(st)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法步骤状态: " + status + "(done|skipped)");
        }
        if ("skipped".equals(st) && (skipReason == null || skipReason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "skipped 必须填写 skip_reason(不能静默跳过)");
        }
        SopRun.RunStep cur = run.getSteps().get(idx);
        cur.setNote(note);
        cur.setStatus(st);
        cur.setSkipReason("skipped".equals(st) ? skipReason : null);
        cur.setAt(Instant.now());
        run.setCurrentStepIndex(idx + 1);
        run.setUpdatedAt(Instant.now());
        return runRepository.save(run);
    }

    /** 收尾:校验每步都已 done/skipped,组装 runlog 落 wiki(category=runlog)。 */
    public SopRun finish(String reqId, String userId) {
        SopRun run = runRepository.findFirstByReqIdAndStatusOrderByCreatedAtDesc(reqId, "running")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有进行中的 Run"));
        List<String> pending = run.getSteps().stream()
                .filter(s -> "pending".equals(s.getStatus()))
                .map(SopRun.RunStep::getKey)
                .toList();
        if (!pending.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仍有未走过的步骤(需 done/skipped): " + pending);
        }
        String path = "/runs/run-" + reqId;
        wikiService.upsertByPath(path, "SOP 执行记录 " + reqId, assembleRunlog(run), "runlog",
                List.of("runlog", "run"), "/runs", userId);
        run.setStatus("finished");
        run.setRunlogPath(path);
        run.setUpdatedAt(Instant.now());
        return runRepository.save(run);
    }

    // ---- 文档注入(按步聚合)----

    private List<DocInject> injectFor(SopRun run, String stepKey) {
        return switch (stepKey) {
            case "understand_req" -> {
                Requirement req = requirementRepository.findById(run.getReqId()).orElse(null);
                if (req == null) yield List.of();
                String content = "标题: " + req.getTitle() + "\n类型/tier: " + req.getType() + "/" + req.getTier()
                        + "\n结构化: " + (req.getStructured() != null ? req.getStructured().toString() : "(无)");
                yield List.of(new DocInject("requirement", run.getReqId(), req.getTitle(), null, content));
            }
            case "understand_code" -> {
                Graph g = archGraphService.getGraph(run.getProjectId());
                StringBuilder sb = new StringBuilder();
                g.modules().forEach(m -> sb.append("- ").append(m.getKey())
                        .append("(").append(m.getGroup()).append("): ").append(m.getTitle()).append("\n"));
                yield List.of(new DocInject("arch", null, "架构概览(模块)", null, sb.toString()));
            }
            // 必读类:standard 全文直接注入
            case "inject_standard" -> fullText("standard");
            // 检索类:按需求关键词相关性召回 TopK 候选(非分类全量),LLM 挑选后 fetch_doc 取全文;不足可自检索
            case "find_assets" -> rankedMetaList(run, "asset");
            case "find_experience" -> rankedMetaList(run, "experience");
            default -> List.of();
        };
    }

    private List<DocInject> fullText(String category) {
        List<DocInject> out = new ArrayList<>();
        for (WikiPage p : wikiService.search("", MatchMode.FUZZY, false, category)) {
            out.add(new DocInject(category, p.getPath(), p.getTitle(), null, p.getContent()));
        }
        return out;
    }

    /**
     * 按需求关键词相关性召回该分类的 TopK 候选(替代旧的"空查询全量"),并附一条兜底提示。
     * 相关度来自 WikiService.searchRanked(OR + 字段加权 + 中文 bigram);语义不足时 LLM 可自行 search_knowledge。
     */
    private List<DocInject> rankedMetaList(SopRun run, String category) {
        Requirement req = requirementRepository.findById(run.getReqId()).orElse(null);
        String query = req != null ? buildReqQuery(req) : "";
        List<DocInject> out = new ArrayList<>();
        for (WikiPage p : wikiService.searchRanked(query, category, INJECT_TOP_K)) {
            out.add(new DocInject(category, p.getPath(), p.getTitle(), brief(p.getContent()), null));
        }
        // 兜底提示(ref=null 不计入 step.injectedDocs):平台只给相关 TopK,不足/不相关请 LLM 自检索
        out.add(new DocInject("guide", null, "检索提示",
                "以上为按需求关键词召回的 Top" + INJECT_TOP_K + " 个 " + category
                        + " 候选(可能为空)。若不足或不相关,请用 search_knowledge(category=\"" + category
                        + "\", q=…) 自行补检,再 fetch_doc 取全文。", null));
        return out;
    }

    /** 从需求拼检索查询:标题 + 模块名/描述 + 用户故事(供 searchRanked 分词召回)。 */
    private String buildReqQuery(Requirement req) {
        StringBuilder sb = new StringBuilder();
        if (req.getTitle() != null) sb.append(req.getTitle()).append(' ');
        Structured s = req.getStructured();
        if (s != null) {
            if (s.getModules() != null) {
                for (Structured.Module m : s.getModules()) {
                    if (m.getName() != null) sb.append(m.getName()).append(' ');
                    if (m.getDescription() != null) sb.append(m.getDescription()).append(' ');
                }
            }
            if (s.getUserStories() != null) {
                for (String us : s.getUserStories()) sb.append(us).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private String brief(String content) {
        if (content == null) return "";
        String c = content.strip();
        return c.length() <= 120 ? c : c.substring(0, 120) + "…";
    }

    private PrevStep toPrev(SopRun.RunStep s) {
        return new PrevStep(s.getKey(), s.getTitle(), s.getStatus(), s.getNote());
    }

    private String assembleRunlog(SopRun run) {
        StringBuilder sb = new StringBuilder();
        sb.append("# SOP 执行记录 — ").append(run.getReqId()).append("\n\n");
        sb.append("- tier: ").append(run.getTier()).append(" | 步骤数: ").append(run.getSteps().size()).append("\n");
        sb.append("- 说明: Run 记录方法论流程合规与文档注入;工作产物(commit/验证)见 dev_plan。\n\n");
        for (SopRun.RunStep s : run.getSteps()) {
            sb.append("## ").append(s.getKey()).append(" — ").append(s.getTitle())
                    .append("  [").append(s.getStatus()).append("]\n");
            if (s.getNote() != null && !s.getNote().isBlank()) sb.append(s.getNote()).append("\n");
            if (s.getSkipReason() != null && !s.getSkipReason().isBlank()) {
                sb.append("> 忽略原因: ").append(s.getSkipReason()).append("\n");
            }
            if (!s.getInjectedDocs().isEmpty()) {
                sb.append("- 注入文档: ").append(String.join("、", s.getInjectedDocs())).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
