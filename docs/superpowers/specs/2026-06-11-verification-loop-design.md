# 验证闭环(Verification Loop)— 设计 spec

> 状态:设计已对齐(2026-06-11),待实施。属用户「需求闭环」第 ⑦ 步(改完拿验收做验证);④需求侧规范索引 / ⑩需求→树联动 / ⑪收尾沉淀 另切。

## 背景与目标
AI 改代码最大的可靠性风险是「**没验证就自信地说完成**」。本切片把「验证」做成 Herness 进度树的一等公民:验收要求 → AI 本地跑验证 → 结果经 MCP 上报留痕 → 软规则挡住「无验证的完成」。直接对抗该失败模式。

## 已对齐的决策
- **执行模型**:AI(Claude Code)在本地跑验证命令,结果**经 MCP 上报**;**平台不碰代码、不跑构建**(贴合「平台存逻辑、AI 持代码、GitHub 存代码」)。
- **轻方案**:验收点保持自然语言文本,验证以「**节点级验证记录**」留痕;验收点逐条绑定验证手段留作后续。
- **粒度**:节点级 `verifications` 列表 + 软规则;**不**逐条硬绑验收点。
- **工具**:扩 `update_dev_plan_node`(不新增独立工具),一次上报状态+commit+验证+勾验收。

## 数据模型(`DevPlan.Node`)
新增 `verifications: List<Verification>`(累积,似 log,不替换):
```
Verification {
  kind,       // compile | typecheck | test | lint | manual | e2e(枚举校验)
  command,    // 跑了什么(如 "mvn -Dtest=X test";manual 可空)
  result,     // pass | fail
  summary,    // 结果摘要(如 "10 tests, 0 failed")
  covers?,    // 可选:关联的验收点文本数组
  at          // 时间戳(后端填)
}
```
现有 `artifacts.tests_added` 保留兼容(是否加了测试),`verifications` 是新的真凭据。

## 后端
- `DevPlan.Node` 加 `verifications`(默认空 List);新增 `Verification` 内部类(`@JsonProperty` snake_case)。
- `DevPlanService.updateNode`:入参 `verifications` 非空时,逐条 `setAt(now)` **追加**到节点(保留历史),并加一条 `action=verify` 工作日志(摘要:跑了哪些 kind、各 result)。kind 用受控集合校验,非法报 400。
- `computeWarnings` 增强(done 时):
  - 节点无任何 `result=pass` 的 verification → 警告「标记完成但没有通过的验证记录」。
  - 保留现有:无 commit/产物、验收未勾、子节点未完成。
- 单测:updateNode 追加 verifications + 填 at + 追加 verify 日志;computeWarnings(done 无 pass 验证 → 警告;有 pass → 不警告);非法 kind 报错。

## MCP(工具数不变,扩参数)
- `update_dev_plan_node` 加 `verifications` 参数(数组:`kind/command/result/summary/covers`);改已有工具参数,**重启即生效、无需重连**。
- 工具描述与 CLAUDE.md「标准流程」补:**节点 done 前须本地跑验证(编译/测试/lint),经 `update_dev_plan_node` 上报 `verifications`;done 时无通过验证会软警告**。

## 前端
- `types` `DevPlanNode` 加 `verifications`;`NodeDetailDrawer` 展示验证记录:每条 kind 徽标 + result(通过绿/失败红)+ summary + command(代码体)。

## 验证(本功能自身)
后端 `mvn -Dtest=DevPlanServiceTest test`;MCP/前端 `tsc`;前端节点抽屉目测验证记录。

## 非目标 / 风险
- **非目标**:验收点逐条绑定验证(本轮节点级);平台侧 runner(AI 跑);验收点结构化绑定验证手段(后续);④/⑩/⑪ 另切。
- **风险**:`verifications` 是 AI 自报,依赖 AI 诚实上报真实结果 —— 缓解:软规则 + commit 关联 + 前端展示 command/summary 供人抽查;不做平台侧强制执行(架构所限,平台不持代码)。
