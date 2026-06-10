# 做深 Herness 进度树 —— 设计文档(spec)

> 状态:已与用户逐节对齐、待评审。
> 日期:2026-06-09
> 范围定位:**第一切片「灵魂闭环」内的进度树增强**。先搭骨架,细节留到平台「自举」(用本平台开发本平台)过程中迭代。

---

## 1. 背景与目标

当前 Herness 进度树过于浅薄:节点只有 `id/title/description/status/blocked_reason/artifacts(自由 Map)/log(一句 message)/children`;状态机只校验合法值;建树时把需求模块的 `acceptance_criteria` 等信息全丢了;前端只能"改状态";`artifacts` 无结构、前端只认 `pr_number`。

**目标**:把进度树做成「**信息完备、可验收、可追溯、可干预**的开发驾驭树」,并借助一个 **GitHub 闭环**把"逻辑(平台)"与"代码(GitHub)"钉在一起。

设计大量借鉴 `docs/参考/` 三篇文章的核心思想:
- **中间态日志(Intermediate Log)**:每步记录"做了什么 / 为什么这么做",可审计可回溯。
- **任务拆成可验收的小黑盒**:叶子节点 ≈ 一个 commit。
- **文档先行 / 列出参考文档防偏离**:节点关联知识库文档。
- **可观测看板 + 低成本自然语言二次修正**:看得见、插得进手。
- **够用就行 / 人机协同 > 硬拦截**:克制设计,留人工介入空间。

## 2. 范围

用户确定的优先级:**A 节点信息深度 > C 可观测可干预 > B 过程契约 > D AI 驱动闭环**。本轮:

| 轴 | 力度 | 内容 |
|----|------|------|
| **A 节点信息深度** | 做透 | 验收标准(混合)、关联知识库文档、结构化产物、N 条带 commit 的工作日志、`DevPlan` 顶层 repo、**GitHub 闭环** |
| **C 可观测可干预** | 做出体验 | 树为主 + 详情抽屉 + 轻量总览条;人对 AI 自然语言纠偏(`corrections`) |
| **B 过程契约** | 轻量 | 一条硬规则(blocked 必填原因)+ 若干软警告(防幻觉)+ 工作日志自动留痕、actor 自动判定 |
| **D AI 闭环** | 暂缓 | 见 §9 非目标 |

### 非目标(本轮明确不做,留后续轮)
- ❌ 新增 `write_tech_proposal` 工具与技术方案实体(`tech_proposal_id` 字段预留即可)。
- ❌ Herness 契约 prompt 系统性重写 / 一键生成 Claude Code 配置。
- ❌ 接 GitHub API / webhook 自动同步 commit、commit 真实性校验。
- ❌ 节点间依赖关系建模(`blocked` 仅状态 + 文本原因)。
- ❌ 强制状态流转方向、硬拦截 `done`、Kanban 主视图。

## 3. 数据模型(定稿)

沿用 snake_case(对 AI/MCP 暴露的领域契约,与 `structured`、设计文档一致)。后端 `com.potato.devplan.DevPlan` 据此重构。

```jsonc
DevPlan {
  "created_at": "...", "updated_at": "...",
  "repo": {                                  // 仓库记在计划顶层
    "url": "https://github.com/org/repo",
    "provider": "github",                    // 预留多 provider
    "default_branch": "main"
  },
  "root": Node
}

Node {
  "id": "node_1",
  "title": "请假表单组件",
  "description": "包含日期选择、假期类型下拉、原因输入,需校验",
  "status": "in_progress",                   // todo | in_progress | done | blocked
  "blocked_reason": null,                    // status=blocked 时必填

  // —— 验收(混合)——
  "module_ref": "请假表单",                   // 关联需求 structured.modules[].name(可空)
                                              // 继承该模块 acceptance_criteria 作只读参考
  "acceptance_criteria": [                    // 节点自带、可勾选的细化验收点
    { "text": "日期不可选过去", "checked": true },
    { "text": "未填原因禁止提交", "checked": false }
  ],

  // —— 关联知识库 ——
  "related_docs": ["/development/code-style/react"],  // wiki path 数组,前端渲染为可跳转链接

  // —— 节点级产物(commit 不在此,见 log)——
  "artifacts": {
    "branch": "feat/leave-form",
    "pr_number": 12, "pr_url": "https://...",
    "tests_added": true,
    "tech_proposal_id": null                 // 预留 D 轮
  },

  // —— 工作日志(N 条,commit 挂在日志条目上)——
  "log": [
    { "timestamp": "...", "actor": "ai", "action": "status_change",
      "summary": "完成表单 UI 与校验", "detail": "用 antd Form rules 做必填+日期约束,复用内置校验避免重复造轮子",
      "from": "in_progress", "to": "done",
      "commit": { "sha": "abc123", "url": "https://github.com/org/repo/commit/abc123",
                  "message": "feat: 请假表单校验", "files": ["src/components/LeaveForm.tsx"] } },
    { "timestamp": "...", "actor": "human", "action": "note",
      "summary": "修复日期边界 bug",
      "commit": { "sha": "def456", "url": "...", "message": "fix: 边界日期", "files": ["..."] } }
  ],

  // —— 人对 AI 的纠偏指令(独立于 log,有生命周期)——
  "corrections": [
    { "id": "c1", "timestamp": "...", "by": "alice", "message": "校验要加手机号格式", "resolved": false }
  ],

  "children": [ /* 同结构,深度任意 */ ]
}
```

**字段语义与约定:**
- **节点模型 = 自由树 + 统一可选字段**,深度任意。"叶子≈一个 commit"为**软约定**(写进契约引导,不在数据层强制)。
- **验收标准混合**:`module_ref` 关联需求模块(继承其验收标准作参考,单一事实来源)+ 节点自带可勾选 `acceptance_criteria`。两者都可空。
- **commit 是日志条目的可选子对象**:一个节点 N 条日志、N 个 commit(完成→修 bug→优化的真实轨迹)。`commit.url` 可由 `repo.url + sha` 拼出,也可直接存。
- **`artifacts` 只留节点级**:`branch / pr / tests_added / tech_proposal_id`;**变更文件跟着各自 commit 走**,不在 artifacts 重复。
- **`corrections` 独立于 `log`**:log 是只读历史,corrections 有"未解决/已解决"生命周期、是给 AI 的指令。

### GitHub 闭环
```
需求(structured) → 建树·根节点记 repo → 节点说明(标题/描述/验收/关联文档)
  → AI/人 拿说明实现 → 一个节点产出 commit 推到 GitHub
  → 回填:commit 记进该节点本次 log 条目,状态→done
  → 闭合:节点 repo.url + commit.sha = 可点的 GitHub commit 链接,逻辑与代码再不脱节
```
- **回填方式**:前端手填 / Claude Code 经 `update_dev_plan_node` 主动上报刚提交的 `sha`。**本轮不接 GitHub API/webhook**。

## 4. C 轴:前端进度树视图

**布局 A(树为主 + 详情抽屉 + 顶部总览条)**,替换现有只读 `DevPlanTree.tsx`。

**① 顶部总览条(轻量)**:状态计数(todo/进行中/完成/阻塞)、**阻塞数高亮**(可定位)、**仓库链接**(`repo.url`)。进度% = 已完成叶子 / 总叶子(**可选**,用户对此无强需求)。

**② 树**:沿用 antd `Tree`,节点显示状态 Tag + 标题 + 关键徽标(如有 commit/PR)。点节点 → 滑出详情抽屉。

**③ 节点详情抽屉**(点节点滑出),依次:
- 描述
- 验收标准:`module_ref` 继承的(只读参考)+ 节点自带 `acceptance_criteria`(**可勾选**)
- 关联文档:`related_docs` 渲染为可点链接,跳知识库对应 path
- 产物:branch / PR(可点)/ tests_added
- 工作日志(N 条):actor(人/AI 图标)、summary、detail、from→to;**带 commit 的条目显示可点链接**(→ GitHub)
- 纠偏区:列出 `corrections`,可新增、可标"已解决"

**④ 纠偏机制**:人留自然语言指令 → 存 `corrections` → **AI 下次经 MCP 读该需求时看到未解决纠偏并据此调整** → 人确认后标 `resolved=true`。

## 5. B 轴:状态机软约束 + 工作日志纪律

**硬规则(仅一条)**:`status=blocked` 时 `blocked_reason` 必填,否则 400。

**软警告(返回 `warnings:[...]`,前端弹提示 / MCP 响应携带,但允许继续)**:
- 标 `done` 但节点无任何 commit/产物 → 警告(防幻觉护栏)。
- 标 `done` 但 `acceptance_criteria` 有未勾选项 → 警告。
- 标 `done` 但有子节点未 `done` → 警告。

**工作日志纪律**:
- **状态每次变更自动追加一条 log**(`actor/from/to/summary`),不依赖调用方。
- 调用方可补 `detail`(为什么)与 `commit`。
- **`actor` 由认证渠道推断**:API Key 调用→`ai`;JWT(网页)→`human`。需要 `AuthTokenFilter` 在 principal 上标记认证渠道。

## 6. 后端 API 与 MCP 改动

### 6.1 平台 API(`com.potato.devplan.DevPlanController`)
- `POST /requirements/{id}/dev-plan` 入参增加 `repo`;节点入参 `NodeInput` 增加 `module_ref / acceptance_criteria / related_docs`。
- `PATCH .../dev-plan/nodes/{nodeId}` 入参增加 `commit:{sha,url?,message?,files?}`、`log_detail`;勾选验收点(`acceptance_criteria` 的 checked 切换)并入此接口;返回体增加 `warnings:[]`。
- 新增 `corrections` 小接口(供前端):
  - `POST .../nodes/{nodeId}/corrections` 新增纠偏 —— 权限:可查看该需求者。
  - `PATCH .../nodes/{nodeId}/corrections/{cid}` 标已解决 —— 权限:`development`。

### 6.2 MCP 工具(`mcp-server/src/tools.ts`,增强现有,不新增)
- `create_dev_plan`:入参加 `repo` 与节点的 `module_ref/acceptance_criteria/related_docs`。
- `update_dev_plan_node`:入参加 `commit`、`log_detail`;响应透传 `warnings`;后端按 API Key 记 `actor=ai`。
- `get_requirement_detail`:dev_plan 摘要带上节点 `acceptance_criteria`(含未勾选)、`related_docs`、**未解决 `corrections`**、`repo`。

## 7. 权限(seed 增量)
- `dev_plan / create / update`:`development`(沿用)。
- 留纠偏(corrections 新增):可查看该需求者(`development/testing/product`)。
- 标纠偏已解决:`development`。
- 由 `DataSeeder` / `permission_rules` seed 补充。

## 8. 兼容与迁移
- 开发期 MongoDB,基本无存量正式数据;`artifacts` 由自由 Map → 结构化对象属破坏性变更,但风险低。
- 旧 `log` 条目(只有 message)读取时容错:`summary` 取旧 `message`,其余字段缺省。
- 后端实体改造后需 `docker compose restart backend` 重新编译验证。

## 9. 留给「自举」阶段细化的点(本轮不锁死)
- 进度% 口径、总览条信息密度。
- `actor` 推断的边界(API Key 也可能是人手动调)。
- 软警告的具体文案与触发阈值。
- 节点详情抽屉的字段顺序与可编辑范围。
- commit 与 PR 并存时的展示优先级。
- 验收点勾选是否需要留痕到 log。

## 10. 测试与验证
- **后端**:`DevPlanService` 单测 —— 建树写入新字段、updateNode 的软警告、blocked 必填校验、状态变更自动 log、actor 推断。
- **前端**:统一在界面验证(沿用项目约定:前后端写完后在前端验证,不单独跑后端冒烟)—— 建树带 repo、节点详情抽屉各区块、勾选验收、留/解决纠偏、commit 链接可点。
- **闭环**:留到后续里程碑 7,用真实 Claude Code 经 MCP 跑「读需求→建树(带repo)→实现→上报commit→人留纠偏→AI据此调整」。

## 11. 涉及的关键文件
- 后端:`com.potato.devplan.{DevPlan,DevPlanService,DevPlanController}`、`requirement.Structured`(module_ref 关联)、`auth.AuthTokenFilter`(actor 渠道标记)、`config.DataSeeder` / `permission` seed。
- 前端:`components/DevPlanTree.tsx`(重构为布局 A)、新增节点详情抽屉组件、`features/useDevPlan.ts`、`api/devplan.ts`、`types`。
- MCP:`mcp-server/src/tools.ts`。
- 文档:完成后同步 `docs/平台开发说明.md`(项目约定:每阶段更新开发文档)。
