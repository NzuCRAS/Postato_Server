package com.potato.techproposal;

import com.potato.devplan.DevPlan;
import com.potato.devplan.DevPlanDtos.UpdateNodeRequest;
import com.potato.devplan.DevPlanDtos.UpdateResult;
import com.potato.devplan.DevPlanService;
import com.potato.wiki.WikiService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 技术方案 = 一篇 wiki 页(复用知识库)+ 关联到进度树节点(文档先行)。
 * 组合 WikiService(建/更新页)与 DevPlanService(写 tech_proposal_id、可选翻状态)。
 */
@Service
public class TechProposalService {

    private final WikiService wikiService;
    private final DevPlanService devPlanService;

    public TechProposalService(WikiService wikiService, DevPlanService devPlanService) {
        this.wikiService = wikiService;
        this.devPlanService = devPlanService;
    }

    /**
     * 为 reqId/nodeId 创建(或更新)技术方案页,并把路径写回节点。
     * 页 path 用可读的 title(而非需求/节点编号,编号对人无意义);靠 title 区分,
     * 同 title 重复调用走 upsert 更新。reqId/nodeId 经 artifacts.tech_proposal_id 与节点关联留痕。
     * @param actor 操作者(ai/human,按认证渠道)
     * @param userId 当前用户 id(wiki 作者)
     */
    public Result create(String reqId, String nodeId, String title, String content,
                         List<String> tags, boolean markInProgress, String actor, String userId) {
        // 用可读 title 作 path(/ 换 - 避免被切成多层目录),不再把编号塞进路径
        String safeTitle = (title == null || title.isBlank()) ? "untitled" : title.trim().replace('/', '-');
        String path = "/tech-proposals/" + safeTitle;

        List<String> merged = new ArrayList<>();
        merged.add("tech-proposal");
        merged.add("tmp");
        if (tags != null) {
            for (String t : tags) {
                if (t != null && !t.isBlank() && !merged.contains(t)) merged.add(t);
            }
        }
        wikiService.upsertByPath(path, title, content, "doc", merged, "/tech-proposals", userId);

        DevPlan.Artifacts artifacts = new DevPlan.Artifacts();
        artifacts.setTechProposalId(path);
        String status = markInProgress ? "in_progress" : null;
        UpdateNodeRequest up = new UpdateNodeRequest(status, artifacts, "生成技术方案", null, null, null, null, null);
        UpdateResult r = devPlanService.updateNode(reqId, nodeId, up, actor);

        return new Result(path, r.node());
    }

    public record Result(String proposalPath, DevPlan.Node node) {
    }
}
