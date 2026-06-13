# `potato` 开发 SOP skill 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建 `.claude/skills/potato/` 开发 SOP skill —— SKILL.md(一轮闭环显式九步 + 自查)+ 6 个 references,把 Herness 流程与对应 MCP 能力编排清楚;CLAUDE.md 瘦身为指针;知识库 `/agent/herness-contract` 指向 skill。

**Architecture:** Claude Code skill,三层渐进式披露。SKILL.md = L1(触发 + 九步流程 + 自查),`references/` = L2(按需加载),v1 无 `scripts/`。skill 是「教」层,咬合平台「管」层(`computeWarnings`/blocked),eval「验」(真跑延后至 PATCH 401 修复)。

**Tech Stack:** Markdown(Agent Skills 标准:`SKILL.md` frontmatter + 正文);MCP `write_knowledge` 更新知识库;git。

**spec:** `docs/superpowers/specs/2026-06-13-potato-skill-design.md`

**这是文档撰写计划(非 TDD):** 验证 = 文件/结构检查 + frontmatter 合法 + skill 可被发现 + 人工走查;无单元测试。提交信息末尾保留 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

---

## 文件结构(决策锁定)

- Create: `.claude/skills/potato/SKILL.md` — L1:frontmatter + 核心规则 + 九步流程 + 执行后自查 + references 索引
- Create: `.claude/skills/potato/references/mcp-toolbox.md` — 15 个 MCP 工具分组清单 + 何时用
- Create: `.claude/skills/potato/references/board-injection.md` — 四 category 板块注入时机与方式
- Create: `.claude/skills/potato/references/herness-flow.md` — 进度树各 MCP 工具参数细节 + 一节点一 commit + actor
- Create: `.claude/skills/potato/references/promotion.md` — tmp→experience 晋升(去 tmp+设 category+改 path)
- Create: `.claude/skills/potato/references/corrections.md` — open_corrections 读取与处理
- Create: `.claude/skills/potato/references/templates.md` — 技术方案 / 经验沉淀 模板
- Modify: `CLAUDE.md` — 瘦身为指针 + 不可协商底线 + MCP 接入配置
- (知识库,经 MCP)`/agent/herness-contract` → 指向 skill

---

## Task 1: SKILL.md(L1 核心)

**Files:**
- Create: `.claude/skills/potato/SKILL.md`

- [ ] **Step 1: 写 SKILL.md(完整内容如下)**

````markdown
---
name: potato
description: 在 Potato 平台上做开发任务时遵循的标准作业流程(SOP)。当用户要「实现/开发某需求」、要走 Herness 流程、或项目已连 Potato 平台 MCP(potato 工具如 get_requirement_detail/create_dev_plan/update_dev_plan_node)时使用。把「读需求→摸代码→注规范→查资产→检经验→规划→逐节点实现验证→处理警告→沉淀回标」这条闭环和对应 MCP 能力编排清楚。
---

# Potato 开发 SOP

你是遵循 Potato 平台 Herness 流程的 AI 开发者。**每个开发任务都走下面这一轮闭环**。平台会用**软警告 + blocked 硬校验**顶回不合规的步骤——别绕过,顺着流程走。

完整可调用的 MCP 能力清单见 `references/mcp-toolbox.md`;各步细节按需查对应 reference。

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

## 执行后自查(完成前对照,逐条过)

- [ ] 每个 done 节点都有 **pass 的 verification**?(否则平台软警告)
- [ ] 验收点都勾了,未勾的在 log_detail 说明了?
- [ ] 一节点≈一 commit,commit 已挂到节点?
- [ ] 可复用经验**沉淀**了(write_knowledge category=experience)?
- [ ] 需求完成**回标**结构树叶子 impl_status 了(relate_requirement_arch)?
- [ ] 第 3 步注入的规范真遵守了 / 第 4 步资产真复用了?

## 不可协商(平台约定)

- 领域字段(`structured`/`dev_plan`)对 MCP 用 **snake_case**。
- 前端**视图/逻辑分离**(api/ 纯 HTTP、features/useXxx 逻辑、pages|components 只渲染)。
- 容器内验证:`docker compose exec -T backend mvn ...`、前端/MCP `npx tsc --noEmit`;改后端 `docker compose restart backend`。
- 非交互推送 `GIT_TERMINAL_PROMPT=0 git push`;commit 末尾保留 `Co-Authored-By`。
````

- [ ] **Step 2: 验证 frontmatter 与结构**

Run: `docker compose exec -T frontend node -e "const m=require('fs').readFileSync('/dev/stdin','utf8')" < .claude/skills/potato/SKILL.md` 不适用;改为人工检查:
- 文件以 `---` frontmatter 开头且含 `name: potato` 与 `description:`;
- 正文含「一轮闭环(九步)」「执行后自查」「不可协商」三节;
- 九步每步都点名了至少一个 MCP 工具或 reference。

Expected: 三节齐全、九步 MCP 映射完整。

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/potato/SKILL.md
git commit -m "feat(skill): potato dev-SOP SKILL.md (9-step closed-loop + self-check)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: references/mcp-toolbox.md

**Files:**
- Create: `.claude/skills/potato/references/mcp-toolbox.md`

- [ ] **Step 1: 写 mcp-toolbox.md(完整内容如下)**

````markdown
# 可调用的 MCP 能力(potato,15 工具)

> 这是你的全部「手」。按下方分组在流程各步取用;参数细节查 `herness-flow.md`。

## 需求 / 知识
- `get_requirement_detail(requirement_id)` — 需求结构化详情 + dev_plan 摘要 + open_corrections + 项目级/需求级 doc_links。**第 1 步必调**。
- `create_requirement(title, structured?, status?, project_id?, doc_links?)` — 建需求(单一事实来源)。
- `search_knowledge(query, category?, match_mode?, include_tmp?, limit?)` — 检索知识库;`category` ∈ doc/asset/standard/experience。**第 3/4/5 步用**。
- `write_knowledge(path, title, content, category?, tags?, parent_path?)` — 按 path upsert 沉淀页。**第 9 步用**。
- `write_tech_proposal(requirement_id, node_id, title, content, mark_in_progress?)` — 文档先行,落临时区并关联节点。**第 6 步(复杂)用**。

## 进度树(Herness)
- `create_dev_plan(requirement_id, nodes, repo?, root_title?)` — 建树。**第 6 步用**。
- `update_dev_plan_node(requirement_id, node_id, status?, commit?, verifications?, acceptance_criteria?, log_message?, log_detail?, blocked_reason?, artifacts?)` — 推进节点。**第 7 步用**。
- `add_dev_plan_nodes(requirement_id, parent_node_id, nodes)` — 父节点下加子任务。
- `set_dev_plan_repo(requirement_id, url, provider?, default_branch?)` — 设/改关联仓库。
- `reset_dev_plan(requirement_id, reason?)` — 重置入档(慎用)。

## 项目 / 结构树
- `get_project_detail(project_id)` — 仓库 + docLinks + 结构树(L0–L4)+ 需求摘要。**第 2 步用**。
- `get_architecture(project_id, tag?, layer?)` — 结构树,可按 tag/layer 过滤。**第 2 步用**。
- `relate_requirement_arch(requirement_id, links[{arch_path, impl_status?}])` — 需求完成回标结构树叶子。**第 9 步用**。
- `upsert_arch_layer(project_id, parent_path?, nodes)` — 逐层共建结构树(走《arch-coauthoring》协议)。
- `sync_project_modules(project_id, repo_id, modules)` — .project.yaml 同步工程树 L3+。
````

- [ ] **Step 2: 验证**

人工检查:15 个工具全部列出、分三组、每个有「做什么 + 第几步用/何时用」。与 `mcp-server/src/tools.ts` 实际注册的工具名逐一对照,无遗漏无臆造。

Expected: 15 工具齐、工具名与 tools.ts 一致。

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/potato/references/mcp-toolbox.md
git commit -m "feat(skill): potato references/mcp-toolbox (15 MCP tools catalog)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: references/board-injection.md

**Files:**
- Create: `.claude/skills/potato/references/board-injection.md`

- [ ] **Step 1: 写 board-injection.md(完整内容如下)**

````markdown
# 知识库板块(category)注入约定

知识库按 `category` 分四板块,各有不同消费方式:

| category | 装什么 | 何时、怎么用 |
|----------|--------|--------------|
| `standard` | 代码规范(接口契约 / 数据模型 / 代码风格) | **第 3 步开工必读**:`search_knowledge(category="standard")`,全量注入,编码全程遵守 |
| `asset` | 可复用代码 / 组件(可挂 demo/资产 URL) | **第 4 步**:`search_knowledge(category="asset")` 先取清单,命中需要的再取详情,先复用不重造 |
| `experience` | 先验经验(场景处理手段、bug 解法) | **第 5 步触发式**:拿不准时 `search_knowledge(category="experience", q="具体问题")` |
| `doc` | 通用说明(`/agent/*` 协议、架构说明) | 兜底,按需查 |

约定:
- `tmp` 是正交于 category 的 tag,标「未晋升草稿」(开发伴生的技术方案),检索默认排除;转正见 `promotion.md`。
- 写沉淀时务必带对 `category`,否则将来按板块检索会漏。
````

- [ ] **Step 2: 验证**

人工检查:四 category 齐、各自「装什么 + 何时怎么用」明确、与 SKILL.md 第 3/4/5 步一致。

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/potato/references/board-injection.md
git commit -m "feat(skill): potato references/board-injection (4 category boards)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: references/herness-flow.md

**Files:**
- Create: `.claude/skills/potato/references/herness-flow.md`

- [ ] **Step 1: 写 herness-flow.md(完整内容如下)**

````markdown
# Herness 进度树操作细节

## 建树 `create_dev_plan`
- `nodes`:按软件模块分解,每个 `{title, description?, module_ref?(关联需求模块名,继承其验收), acceptance_criteria?[文本], related_docs?[wiki path], children?}`。
- `repo`:`{url, provider?, default_branch?}`,commit 链接由 `repo.url + sha` 拼。
- 已存在计划会 409 → 改用 `add_dev_plan_nodes` 增量加。

## 推进节点 `update_dev_plan_node`
- 开工:`status="in_progress"`,`log_detail` 写**为什么这么做**(决策依据)。
- done:同时带
  - `commit={sha, message?, files?}`(url 可省,平台拼);
  - `verifications=[{kind: compile|typecheck|test|lint|manual|e2e, command, result: pass|fail, summary, covers?}]` —— **done 前必跑、必报**,否则软警告;
  - `acceptance_criteria=[{text, checked}]` —— 取当前列表,把满足的 `checked` 改 true 再整列表回传。
- blocked:`status="blocked"` + `blocked_reason`(硬校验,必填)。
- **一节点 ≈ 一 commit**;后续修 bug 再记一条带 commit 的日志。

## actor
平台按认证渠道推断:API Key(MCP)→ `ai`;JWT(Web)→ `human`。无需手动传。

## 软警告(平台会顶回,对应 SKILL.md 第 8 步)
done 时若:无 commit/产物、无 pass 验证、验收未勾、子节点未完 → 返回 `warnings`。必须补齐或 `log_detail` 说明豁免。
````

- [ ] **Step 2: 验证**

人工检查:`update_dev_plan_node` 的 verifications/acceptance_criteria/blocked 三处与后端实际行为一致(对照 `backend/.../devplan/DevPlanService.java` 的 computeWarnings 与字段);kind 取值集正确。

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/potato/references/herness-flow.md
git commit -m "feat(skill): potato references/herness-flow (dev-plan MCP params + warnings)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: references/promotion.md + corrections.md

**Files:**
- Create: `.claude/skills/potato/references/promotion.md`
- Create: `.claude/skills/potato/references/corrections.md`

- [ ] **Step 1: 写 promotion.md(完整内容如下)**

````markdown
# 晋升:tmp 技术方案 → 先验经验

开发伴生的技术方案落在临时区 `/tech-proposals/{reqId}/{nodeId}`、带 `tmp` 标签,检索默认排除。当它沉淀为通用经验时**晋升**:

- **动作**:去掉 `tmp` 标签 + 设 `category="experience"` + **改 path** 到经验板块 `/experience/<有意义的名>`。
- **怎么做**:`write_knowledge(path="/experience/…", title, content, category="experience", tags=[…不含 tmp…])`(按新 path upsert);或在前端知识库页点「晋升」。
- 改 path 是固有动作:技术方案统一在临时区,先验经验有独立板块,晋升即「搬板块」。
````

- [ ] **Step 2: 写 corrections.md(完整内容如下)**

````markdown
# 纠偏(corrections)处理

人可在进度节点上留**自然语言纠偏指令**。

- 每次 `get_requirement_detail` 都看返回里的 `open_corrections`(未解决纠偏)。
- **据其调整**当前做法,不要无视。
- 处理完请人在界面把该纠偏标「已解决」(AI 不自行标记)。
````

- [ ] **Step 3: 验证 + Commit**

人工检查两文件内容与 SKILL.md 第 1/9 步、spec 一致。
```bash
git add .claude/skills/potato/references/promotion.md .claude/skills/potato/references/corrections.md
git commit -m "feat(skill): potato references/promotion + corrections" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: references/templates.md

**Files:**
- Create: `.claude/skills/potato/references/templates.md`

- [ ] **Step 1: 写 templates.md(完整内容如下)**

````markdown
# 模板

## 技术方案(write_tech_proposal 的 content)
```
## 引用的知识库文档
(列出第 3/4/5 步注入/检索到的 standard/asset/experience 页 path)
## 实现方案
(架构决策、关键步骤、涉及模块)
## 问题预警
(已知风险、边界、依赖)
```

## 经验沉淀(write_knowledge category=experience 的 content)
```
## 场景
(什么问题 / 什么时候会用到)
## 方案
(怎么解决,关键步骤)
## 关键坑
(踩过的坑 + 怎么绕)
## 适用边界
(什么情况适用 / 不适用)
```

## 可复用资产页(write_knowledge category=asset 的 content)
```
## 名称 / 类型
## 接口契约
(签名、入参出参)
## 用法
(最小示例)
## related_code
(代码 glob 路径)
```
````

- [ ] **Step 2: 验证 + Commit**

人工检查三模板齐(技术方案 / 经验 / 资产),与 spec §templates 一致。
```bash
git add .claude/skills/potato/references/templates.md
git commit -m "feat(skill): potato references/templates (tech-proposal / experience / asset)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: CLAUDE.md 瘦身为指针

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 重写 CLAUDE.md**

把详细 7 步 SOP / 结构树共建小节 / MCP 工具清单**移除**(它们现在在 `potato` skill 里),改为精简指针 + 不可协商底线 + MCP 接入。保留这些段落:
- 标题 + 「你是谁」(一段);
- **新增「开发流程」段**:「做任何开发任务时,遵循 `potato` skill(`.claude/skills/potato/SKILL.md`)——它编排了从读需求到收尾沉淀的完整 Herness 闭环与对应 MCP 能力。本文件只保留不可协商底线;流程细节以 skill 为单一事实来源。」
- 「项目约定(务必遵守)」段(前端视图逻辑分离 / snake_case / 验证方式 / 每阶段更新文档 / 非交互推送)——**原样保留**;
- 「接入平台 MCP(`.mcp.json`)」段——**原样保留**;
- 「可用 MCP 工具」段**删除**(移到 skill 的 mcp-toolbox.md),换一行「工具清单见 `potato` skill 的 `references/mcp-toolbox.md`」。

- [ ] **Step 2: 验证**

Run: `grep -E "snake_case|视图|docker compose exec|GIT_TERMINAL_PROMPT|.mcp.json|potato" CLAUDE.md`
Expected: 不可协商底线(snake_case / 视图分离 / 容器验证 / 非交互推送)、MCP 接入、以及指向 `potato` skill 的指针都在;详细 7 步 SOP 已不在。

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(claude-md): slim Herness contract to a pointer at potato skill" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: 知识库 `/agent/herness-contract` 指向 skill

**Files:**
- (知识库,经 MCP `write_knowledge`)

- [ ] **Step 1: 更新知识库契约页为指针**

调 `write_knowledge`:
- `path="/agent/herness-contract"`,`title="Herness 开发契约(指向 potato skill)"`,`category="standard"`,
- `content`:简述「平台开发 SOP 现以 Claude Code skill `potato` 为单一事实来源(`.claude/skills/potato/`),含九步闭环 + 按板块注入 + 自查;本页只做指针」+ 列九步标题。

- [ ] **Step 2: 验证**

`search_knowledge(query="herness 契约", category="standard")` 能命中该页且内容是指针版。

- [ ] **Step 3:**(无 commit,知识库在平台侧)记一句日志即可。

---

## Task 9: eval 设计(真跑延后)

**Files:**
- Create: `.claude/skills/potato/references/eval.md`

- [ ] **Step 1: 写 eval.md(完整内容如下)**

````markdown
# potato skill 的 eval(验)

> 真跑延后至 `update_dev_plan_node` PATCH 401 修复(否则第 7 步回填跑不完)。本文件先定好用例与断言。

## 测试用例(同任务挂/不挂 skill 各跑一遍)
1. 「帮我在 Potato 平台实现需求 <id>:给 wiki 加导出功能」(should-trigger)
2. 「优化一下这个已有需求的某个节点」(should-trigger)
3. 「解释一下 WikiService 这段代码」(should-NOT-trigger)

## 断言(客观)
- 是否调了 `get_requirement_detail` + `get_architecture`(第 1/2 步)?
- 是否 `search_knowledge(category="standard")` 注入规范(第 3 步)?
- 进度树是否有完整 node 流转(in_progress→done)+ 每个 done 带 pass verification?
- 是否 `write_knowledge(category=experience)` 沉淀 + `relate_requirement_arch` 回标(第 9 步)?

## 触发率
should-trigger 3 条命中、should-not-trigger 1 条不误触发。

## 迭代
每轮从具体失败抽通用规律改 SKILL.md,别只针对单个用例打补丁。
````

- [ ] **Step 2: 验证 + Commit**

人工检查 eval 用例 + 断言覆盖九步关键动作。
```bash
git add .claude/skills/potato/references/eval.md
git commit -m "feat(skill): potato references/eval (test cases + assertions; run deferred on PATCH 401)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 收尾(全部 task 后)

- [ ] **重载验证 skill 可被发现**:重开会话 / 重连后,`potato` 出现在可用 skill 列表,`description` 能在"实现需求"类 prompt 下触发。
- [ ] **更新开发文档**:`docs/平台开发说明.md` 加一段「potato SOP skill(2026-06-13):.claude/skills/potato/,九步闭环 + 按板块注入 + 自查;CLAUDE.md 瘦身为指针」。
- [ ] **真跑 eval**:待 PATCH 401 修复后,按 `references/eval.md` 跑 with/without 对比,据结果迭代 SKILL.md。
