---
name: potato
description: 在 Potato 平台上做开发任务时遵循的标准作业流程(SOP)。当用户要「实现/开发某需求」、要走 Herness 流程、或项目已连 Potato 平台 MCP(potato 工具如 get_requirement_detail/create_dev_plan/update_dev_plan_node)时使用。把「读需求→摸代码→注规范→查资产→检经验→规划→逐节点实现验证→处理警告→沉淀回标」这条闭环和对应 MCP 能力编排清楚。
---

# Potato 开发 SOP

你是遵循 Potato 平台 Herness 流程的 AI 开发者。**每个开发任务都走下面这一轮闭环**。平台会用**软警告 + blocked 硬校验**顶回不合规的步骤——别绕过,顺着流程走。

完整可调用的 MCP 能力清单见 `references/mcp-toolbox.md`;各步细节按需查对应 reference。

## 全程执行文档(边走边记,贯穿始终)

**开工第一件事**:在 `.potato/runs/<requirement_id>-<开工时间>.md` 建一份**执行文档**(模板见 `references/run-log.md`),先写头部 + 开工分流结论。

- 它是本轮执行的**全链路轨迹**:像 COT 一样让你边走边自检,也给用户一份可查证、可优化的交代。**区别于「执行计划」**(那是事前写定要改什么);执行文档记录**实际怎么走的**。
- **全链路粒度逐段追加**:每走完一个 SOP 步骤(下方分流 + 九步)、每完成一个 dev_plan 叶子节点,就把结论**追加进本地文件**——本地文件随时是最新草稿。
- 没把握 / 跳过 / 豁免的步骤**如实写原因**,不补全不美化(自检的价值在于诚实)。
- **跑完 或 中断**(任务结束 / 用户喊停 / 节点 blocked / 会话将尽)→ `write_knowledge(category="runlog", path="/runs/<requirement_id>/<run-id>", content=本地md全文)` **回写平台**,状态栏标「已完成 / 中断(原因)」。runlog 默认不污染知识检索。

## 开工先分流(按 `tier`,仅供参考)

需求带 `tier`(创建时选,`Large`/`Medium`/`Small`,**仅供参考**,可按判断调整),据此定流程强度:
- **Small**:精简——免技术方案、可不拆多节点(单节点或需求级推进),改完直接验证+回填。给小 bug / 小优化。
- **Medium**:标准——按下方九步走(技术方案视情况)。
- **Large**:完整——技术方案先行 + 细拆多节点 + 逐节点,必要时多轮。

两个特殊入口:
- **没有现成需求**(ad-hoc 任务 / 临时 bug)→ **先 `create_requirement`**(标好 `type` 与 `tier`)再走流程。
- **bug 类任务**(`type=bugfix`)→ 实现前**先 root-cause**:稳定复现 + 拿运行时证据 + **禁止没定位就改**(别被表象 / 单个状态码带偏)。

详见 `references/tiering.md`。

## 一轮闭环(九步)

1. **弄清需求** — `get_requirement_detail(requirement_id)`,读 structured(用户故事/模块/验收)、dev_plan 现状、`open_corrections`、项目级 + 需求级 doc_links。**有未解决纠偏先据其调整**(见 `references/corrections.md`)。
2. **弄清代码基础与架构约束** — `get_project_detail` / `get_architecture`,读业务域结构树、节点 `related_code` glob、impl_status;据 `related_code` 打开实际代码,摸清现状与既有模式,别脑补。
3. **注入代码规范** — `search_knowledge(category="standard")` **开工必读**(接口契约 / 数据模型 / 代码风格)。见 `references/board-injection.md`。
4. **查可复用资产** — `search_knowledge(category="asset")` 取清单,需要时取详情(含 demo / 资产 URL),**先复用不重造**。
5. **(拿不准时)检索先验经验** — `search_knowledge(category="experience", q="…")` 触发式,找场景处理手段 / 类似 bug 解法。
6. **制定实现计划** — 复杂 / 需求驱动型:先 `write_tech_proposal(mark_in_progress=true)` 文档先行;然后 `create_dev_plan`(节点带 module_ref / acceptance_criteria / related_docs / repo)或 `add_dev_plan_nodes` 拆子任务。**粒度≈一个节点一个 commit**。见 `references/herness-flow.md`。
7. **逐叶子实现** — 每个叶子节点:
   - `update_dev_plan_node(status="in_progress", log_detail="为什么这么做")`
   - 编码(遵第 3 步规范、复用第 4 步资产)→ 提交一个 commit
   - **done 前本地验证**(编译 / 测试 / lint)
   - `update_dev_plan_node(status="done", commit={sha,message,files}, verifications=[{kind,command,result,summary}], acceptance_criteria=[勾上已满足的])`
   - 遇阻 → `status="blocked"` + `blocked_reason`(必填)
8. **处理 warnings** — 平台返回的软警告(无产物 / 无 pass 验证 / 验收未勾 / 子节点未完)**必须处理**:补齐,或在 `log_detail` 说明为何可豁免。**严禁无产物/无验证谎报 done**。
9. **收尾沉淀 + 回标(必做)** —
   - 可复用经验 → `write_knowledge(category="experience")` 落 `/experience/…`;开发伴生的技术方案要转正,见 `references/promotion.md`。
   - 需求完成 → `relate_requirement_arch` 把需求关联并回标其落地的结构树**叶子**业务模块 impl_status。
   - **回写执行文档** → 把全程记录的 `.potato/runs/…md` 经 `write_knowledge(category="runlog")` 写回平台。

## 执行后自查(完成前对照,逐条过)

- [ ] 每个 done 节点都有 **pass 的 verification**?(否则平台软警告)
- [ ] 验收点都勾了,未勾的在 log_detail 说明了?
- [ ] 一节点≈一 commit,commit 已挂到节点?
- [ ] 可复用经验**沉淀**了(write_knowledge category=experience)?
- [ ] 需求完成**回标**结构树叶子 impl_status 了(relate_requirement_arch)?
- [ ] 第 3 步注入的规范真遵守了 / 第 4 步资产真复用了?
- [ ] **执行文档全程记了、已回写平台**(write_knowledge category=runlog)?

## 不可协商(平台约定)

- 领域字段(`structured`/`dev_plan`)对 MCP 用 **snake_case**。
- 前端**视图/逻辑分离**(api/ 纯 HTTP、features/useXxx 逻辑、pages|components 只渲染)。
- 容器内验证:`docker compose exec -T backend mvn ...`、前端/MCP `npx tsc --noEmit`;改后端 `docker compose restart backend`。
- 非交互推送 `GIT_TERMINAL_PROMPT=0 git push`;commit 末尾保留 `Co-Authored-By`。
- 执行文档落 `.potato/runs/`(**已 gitignore,不进版本库**);回写平台用 `write_knowledge(category="runlog")`,落 `/runs/<requirement_id>/…`。
