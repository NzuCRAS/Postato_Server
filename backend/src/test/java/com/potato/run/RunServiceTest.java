package com.potato.run;

import com.potato.archgraph.ArchGraphService;
import com.potato.requirement.Requirement;
import com.potato.requirement.RequirementRepository;
import com.potato.wiki.WikiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunServiceTest {

    @Mock
    SopRunRepository runRepo;
    @Mock
    RequirementRepository reqRepo;
    @Mock
    WikiService wikiService;
    @Mock
    ArchGraphService archGraphService;

    RunService service;

    @BeforeEach
    void setUp() {
        service = new RunService(runRepo, reqRepo, wikiService, archGraphService);
    }

    private SopRun running(String... statuses) {
        SopRun run = new SopRun();
        run.setReqId("r1");
        run.setProjectId("default");
        run.setTier("Medium");
        run.setStatus("running");
        List<SopRun.RunStep> steps = new ArrayList<>();
        int firstPending = -1;
        for (int i = 0; i < statuses.length; i++) {
            SopRun.RunStep s = new SopRun.RunStep();
            s.setKey("s" + i);
            s.setTitle("步" + i);
            s.setStatus(statuses[i]);
            steps.add(s);
            if (firstPending < 0 && "pending".equals(statuses[i])) firstPending = i;
        }
        run.setSteps(steps);
        run.setCurrentStepIndex(firstPending < 0 ? statuses.length : firstPending);
        return run;
    }

    @Test
    void getOrStart_creates_run_with_full_step_template() {
        Requirement req = new Requirement();
        req.setId("r1");
        req.setProjectId("default");
        req.setTier("Medium");
        req.setTitle("需求一");
        when(runRepo.findFirstByReqIdAndStatusOrderByCreatedAtDesc("r1", "running")).thenReturn(Optional.empty());
        when(reqRepo.findById("r1")).thenReturn(Optional.of(req));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        SopRun run = service.getOrStart("r1");
        assertThat(run.getSteps()).hasSize(10);
        assertThat(run.getSteps().get(0).getKey()).isEqualTo("triage");
        assertThat(run.getCurrentStepIndex()).isZero();
    }

    @Test
    void advance_returns_current_step_and_prev() {
        SopRun run = running("done", "pending", "pending"); // 当前在 idx1
        when(runRepo.findFirstByReqIdAndStatusOrderByCreatedAtDesc("r1", "running")).thenReturn(Optional.of(run));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        RunDtos.AdvanceResult r = service.advance("r1");
        assertThat(r.stepIndex()).isEqualTo(1);
        assertThat(r.currentStep().getKey()).isEqualTo("s1");
        assertThat(r.prevStep().status()).isEqualTo("done");
        assertThat(r.allStepsDone()).isFalse();
    }

    @Test
    void complete_records_and_advances_cursor() {
        SopRun run = running("pending", "pending");
        when(runRepo.findFirstByReqIdAndStatusOrderByCreatedAtDesc("r1", "running")).thenReturn(Optional.of(run));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.complete("r1", "做了分流", "done", null);
        assertThat(run.getSteps().get(0).getStatus()).isEqualTo("done");
        assertThat(run.getSteps().get(0).getNote()).isEqualTo("做了分流");
        assertThat(run.getCurrentStepIndex()).isEqualTo(1);
    }

    @Test
    void complete_skipped_requires_reason() {
        SopRun run = running("pending");
        when(runRepo.findFirstByReqIdAndStatusOrderByCreatedAtDesc("r1", "running")).thenReturn(Optional.of(run));
        assertThatThrownBy(() -> service.complete("r1", null, "skipped", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("skip_reason");
    }

    @Test
    void complete_skipped_with_reason_records_reason() {
        SopRun run = running("pending");
        when(runRepo.findFirstByReqIdAndStatusOrderByCreatedAtDesc("r1", "running")).thenReturn(Optional.of(run));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.complete("r1", null, "skipped", "tier=Small 豁免技术方案");
        assertThat(run.getSteps().get(0).getStatus()).isEqualTo("skipped");
        assertThat(run.getSteps().get(0).getSkipReason()).isEqualTo("tier=Small 豁免技术方案");
    }

    @Test
    void finish_rejects_when_step_pending() {
        SopRun run = running("done", "pending");
        when(runRepo.findFirstByReqIdAndStatusOrderByCreatedAtDesc("r1", "running")).thenReturn(Optional.of(run));
        assertThatThrownBy(() -> service.finish("r1", "u1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("未走过的步骤");
    }

    @Test
    void finish_assembles_runlog_and_marks_finished() {
        SopRun run = running("done", "skipped");
        run.getSteps().get(1).setSkipReason("不需要");
        when(runRepo.findFirstByReqIdAndStatusOrderByCreatedAtDesc("r1", "running")).thenReturn(Optional.of(run));
        when(runRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.finish("r1", "u1");
        verify(wikiService).upsertByPath(eq("/runs/run-r1"), any(), any(), eq("runlog"), any(), eq("/runs"), eq("u1"));
        assertThat(run.getStatus()).isEqualTo("finished");
        assertThat(run.getRunlogPath()).isEqualTo("/runs/run-r1");
    }
}
