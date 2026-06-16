package com.potato.run;

import java.util.List;

/** SOP 工作流 Run 的请求/返回 DTO。 */
public class RunDtos {

    /** complete_step 请求。 */
    public record CompleteRequest(String note, String status, String skipReason) {
    }

    /** advance 返回:当前步 + 注入文档 + 上一步结果摘要 + 进度。 */
    public record AdvanceResult(
            String reqId,
            String runStatus,
            int stepIndex,
            int stepTotal,
            SopRun.RunStep currentStep,
            PrevStep prevStep,
            List<DocInject> injectedDocs,
            boolean allStepsDone) {
    }

    /** 上一步摘要(供下一步衔接)。 */
    public record PrevStep(String key, String title, String status, String note) {
    }

    /** 注入的一篇文档。必读类带 content 全文;检索类只带 description(两阶段,LLM 再 fetch_doc 取全文)。 */
    public record DocInject(String category, String ref, String title, String description, String content) {
    }
}
