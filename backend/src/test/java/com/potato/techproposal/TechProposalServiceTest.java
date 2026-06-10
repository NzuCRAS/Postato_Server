package com.potato.techproposal;

import com.potato.devplan.DevPlan;
import com.potato.devplan.DevPlanDtos;
import com.potato.devplan.DevPlanService;
import com.potato.wiki.WikiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechProposalServiceTest {

    @Mock
    WikiService wikiService;
    @Mock
    DevPlanService devPlanService;

    TechProposalService service;

    @BeforeEach
    void setUp() {
        service = new TechProposalService(wikiService, devPlanService);
    }

    @Test
    void create_upserts_wiki_page_and_links_node() {
        DevPlan.Node node = new DevPlan.Node();
        node.setId("node_4");
        when(devPlanService.updateNode(eq("r1"), eq("node_4"), any(), eq("ai")))
                .thenReturn(new DevPlanDtos.UpdateResult(node, List.of()));

        TechProposalService.Result r = service.create(
                "r1", "node_4", "请假表单方案", "## 架构…", List.of("leave"), true, "ai", "u1");

        // 路径确定;wiki 页带默认标签 tech-proposal/tmp + 自定义标签
        assertThat(r.proposalPath()).isEqualTo("/tech-proposals/r1/node_4");
        verify(wikiService).upsertByPath(
                eq("/tech-proposals/r1/node_4"), eq("请假表单方案"), eq("## 架构…"),
                argThat(tags -> tags.contains("tech-proposal") && tags.contains("tmp") && tags.contains("leave")),
                eq("/tech-proposals/r1"), eq("u1"));

        // 节点更新:status=in_progress(markInProgress),artifacts 写入 tech_proposal_id
        ArgumentCaptor<DevPlanDtos.UpdateNodeRequest> cap =
                ArgumentCaptor.forClass(DevPlanDtos.UpdateNodeRequest.class);
        verify(devPlanService).updateNode(eq("r1"), eq("node_4"), cap.capture(), eq("ai"));
        assertThat(cap.getValue().status()).isEqualTo("in_progress");
        assertThat(cap.getValue().artifacts().getTechProposalId()).isEqualTo("/tech-proposals/r1/node_4");
    }
}
