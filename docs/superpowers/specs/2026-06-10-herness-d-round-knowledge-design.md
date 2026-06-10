# D 轮:技术方案 + 知识沉淀 + Herness 契约 —— 设计文档(spec)

> 状态:已与用户逐节对齐、待评审。日期:2026-06-10
> 定位:第一切片「灵魂闭环」的 **D 轴(AI 驱动闭环深化)**。在「进度树做深」之上补齐"文档先行 + 知识沉淀 + 契约驾驭"。

---

## 1. 背景与目标

进度树做深(A/C/B)+ GitHub commit 闭环已落地。D 轴补齐三块,核心思想来自 `docs/参考/` 三篇文章的「文档先行 / 最大化复用 / 工作流沉淀」:

1. **文档先行**:AI 实现前先产出技术方案(含引用文档),回存并关联到进度树节点。
2. **知识沉淀**:项目中沉淀的经验(如某个 toast 的实现)进入知识库,打标签/归路径,**下次相似场景直接复用**(经 `search_knowledge` 检索)。
3. **契约驾驭**:用 `CLAUDE.md` 约定 Herness 工作流,让接入本仓库的 Claude Code 自动步步为营。

**关键设计决策**:技术方案与沉淀知识**都复用知识库 Wiki**(不另造实体),用**路径 + 标签**区分临时方案(`/tech-proposals/…` + `tmp`)与 durable 知识(`/vue/toast` + `toast`)。这既最大化复用(渲染/搜索现成),又让"知识沉淀→复用"成为平台一等能力。

## 2. 范围

| 项 | 内容 |
|----|------|
| `write_knowledge` | 通用:AI/开发经 MCP upsert 一篇 wiki 页(知识沉淀) |
| `write_tech_proposal` | 特化:建技术方案 wiki 页 + 关联节点 + 可选翻 in_progress |
| 权限 | `wiki/edit` 扩到 `development, product` |
| 契约 prompt | 仓库根 `CLAUDE.md` + 知识库页 `/agent/herness-contract` |
| 前端 | 节点详情抽屉展示技术方案链接 |

### 非目标(本轮明确不做,留后续)
- ❌ 技术方案独立实体/集合/CRUD/独立前端页(复用 Wiki 替代)。
- ❌ 平台"一键生成 Claude Code 配置"功能(手写 CLAUDE.md + 接入说明替代)。
- ❌ RAG 向量检索(沿用现有 MongoDB 文本搜索)。

## 3. 设计

### 3.1 `write_knowledge`(通用知识沉淀)
- **后端 `WikiService.upsertByPath(path, title, content, tags, userId)`**:`repository.findByPath(path)` 命中则走 `update` 逻辑(version++、updatedBy/updatedAt),否则走 `create`。复用现有方法体,避免重复。
- **端点 `POST /api/v1/wiki/pages/upsert`**(`WikiController`),body `{path, title, content, tags?, parentPath?}`,权限 `wiki/edit`。返回 `WikiPage`。
- **MCP 工具 `write_knowledge`**:入参 `{path, title, content, tags?}`,透传到 upsert 端点。描述里引导 AI:"把可复用经验沉淀进知识库,选好路径与标签,便于日后 `search_knowledge` 检索复用"。
- 权限职能:所有认证用户?否 —— 用 `wiki/edit`(development+product,见 3.3)。

### 3.2 `write_tech_proposal`(文档先行 + 关联节点)
- **新 `TechProposalService`**(注入 `WikiService` + `DevPlanService`),方法 `create(reqId, nodeId, title, content, tags, markInProgress, actor, userId)`:
  1. `path = "/tech-proposals/" + reqId + "/" + nodeId`(每节点一份;重复调用即更新)。
  2. `tags` 合并默认 `["tech-proposal", "tmp"]`(去重)。
  3. `wikiService.upsertByPath(path, title, content, mergedTags, userId)`。
  4. 构造 `DevPlan.Artifacts`,只设 `techProposalId = path`;`status = markInProgress ? "in_progress" : null`;调 `devPlanService.updateNode(reqId, nodeId, new UpdateNodeRequest(status, artifacts, "生成技术方案", null, null, null, null), actor)` —— 写入 `tech_proposal_id`、可选翻状态、并追加一条工作日志。
  5. 返回 `{ proposal_path, node }`。
- **端点 `POST /api/v1/requirements/{reqId}/tech-proposals`**(新 `TechProposalController`),body `{node_id, title, content, tags?, mark_in_progress?}`,权限 `dev_plan/update`;actor 由认证渠道推断(复用 `DevPlanController.actorOf` 同款逻辑 —— 抽到一处共用,见 §3.6)。
- **MCP 工具 `write_tech_proposal`**:入参 `{requirement_id, node_id, title, content, tags?, mark_in_progress?}`,透传到上述端点。

### 3.3 权限改动
- `DataSeeder.seedPermissionRules`:`rule("wiki", "edit", "product")` → `rule("wiki", "edit", "development", "product")`。
- 影响:网页 wiki 编辑器 + upsert 端点 + tech-proposal 端点对 development 开放;AI(API Key 对应 development)可写。
- 生效需 reseed(清空 `permission_rules` 重启,或在已有库手动加 `development`)。

### 3.4 Herness 契约 prompt(工件)
- **仓库根 `CLAUDE.md`**:面向"在本仓库用 Claude Code + 平台 MCP 开发"的操作契约。内容大纲:
  - 角色与铁律:步步为营、可追溯、可干预;每步用 MCP 同步进度树。
  - 标准流程:`get_requirement_detail` 读需求 → `search_knowledge` 查规范/既有经验 → (复杂时)`write_tech_proposal` 先写方案并关联节点 → `create_dev_plan` / `add_dev_plan_nodes` 建/拆树 → 逐叶子节点:`update_dev_plan_node` 置 in_progress → 编码 → 提交 commit 并经 `update_dev_plan_node` 回填 `commit` + 勾验收(`acceptance_criteria`)→ 置 done。
  - 对 `warnings`:收到必须处理(补产物/勾验收)或在 `log_detail` 说明为何可豁免。
  - 收尾:把可复用经验用 `write_knowledge` 沉淀(选好 path/tags)。
  - 项目约定指针:前端视图/逻辑分离、snake_case、容器内验证、每阶段更新 `docs/平台开发说明.md`。
  - `.mcp.json` 接入说明(Streamable HTTP 指向 `http://localhost:3001/mcp` + `Authorization: Bearer <在设置页生成的 API Key>`;`.mcp.json` 含密钥已 gitignore)。
- **知识库页 `/agent/herness-contract`**(标签 `agent`/`herness`):同内容,经 `write_knowledge` 写入(dogfood 该工具),便于检索与团队查阅。

### 3.5 前端
- `NodeDetailDrawer` 的「产物」区:若 `node.artifacts.tech_proposal_id` 存在,渲染一条"技术方案"可点链接 → `/wiki?path=<tech_proposal_id>`。
- 类型:`DevPlanArtifacts.tech_proposal_id` 已在(string?)。无需新类型。

### 3.6 重构(顺带)
- `DevPlanController.actorOf(Authentication)` 与 `TechProposalController` 都要"按认证渠道判 actor"。抽到一个小工具(如 `com.potato.auth.AuthChannel.actorOf(Authentication)` 静态方法或一个 `@Component`),两处共用,避免重复。

## 4. 数据模型
- **无新集合、无新实体**。技术方案/沉淀知识都是 `wiki_pages` 文档。
- `node.artifacts.tech_proposal_id` 复用现有预留字段,存**wiki path**(可渲染、可跳转、与 `related_docs` 的 path 体系一致)。

## 5. MCP 工具
- 新增 2 个:`write_knowledge`、`write_tech_proposal`。`index.ts` 的 `TOOL_COUNT` 7 → **9**(注释同步)。
- 注意:新增工具名需 Claude Code 重连 MCP 才出现在会话工具列表;本轮验证用 REST 直打端点。

## 6. 测试与验证
- **后端单测**:
  - `WikiServiceTest.upsert_creates_then_updates_by_path`(同 path 两次 upsert:第一次 create version=1,第二次 update version=2、内容更新)。
  - `TechProposalServiceTest`:建技术方案 → wiki 页存在于预期 path、`node.artifacts.tech_proposal_id == path`;`mark_in_progress=true` 时节点 `in_progress` 且有日志。(用 Mockito stub 或真实 WikiService + mock repo;倾向 `@ExtendWith(MockitoExtension)` mock `RequirementRepository` + `WikiPageRepository`。)
- **REST/自举验证**:经 REST(容器内 node)对自举需求调 tech-proposals 端点与 wiki upsert,确认落库与节点关联;`/agent/herness-contract` 用 upsert 写入后 `search_knowledge "herness"` 能搜到。
- 沿用项目约定:前端在界面验证(节点抽屉技术方案链接可点)。

## 7. 涉及文件
- 后端:`wiki/WikiService.java`(+upsertByPath)、`wiki/WikiController.java`(+upsert 端点)、`wiki/WikiDtos.java`(upsert 请求体,可复用 `WikiPageRequest`,需含 path)、新 `techproposal/TechProposalService.java`、`techproposal/TechProposalController.java`、`techproposal/TechProposalDtos.java`、`config/DataSeeder.java`(权限)、`auth/AuthChannel.java`(actor 抽取)+ `devplan/DevPlanController.java`(改用共用 actor)。
- MCP:`mcp-server/src/tools.ts`(+2 工具)、`mcp-server/src/index.ts`(TOOL_COUNT)。
- 前端:`components/NodeDetailDrawer.tsx`(技术方案链接)。
- 工件:仓库根 `CLAUDE.md`;知识库页 `/agent/herness-contract`(运行期经 write_knowledge 写入,非源码文件)。
- 文档:完成后更新 `docs/平台开发说明.md`(§5 MCP 工具表、§2.3 决策)。

## 8. 留给后续 / 自举继续发现
- "一键生成 CC 配置"平台功能(B 方案)。
- 技术方案/知识页的"过期归档"策略(`tmp` 清理)。
- `search_knowledge` 升级为向量检索(RAG)。
- 契约 prompt 随真实使用迭代收紧(参考文章:运行时发现偏离就补约束)。
