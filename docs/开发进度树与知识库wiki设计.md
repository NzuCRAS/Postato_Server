模块 C（Herness）和模块 D（知识库 Wiki）确实紧密关联：进度树的节点会引用知识库文档（比如“查看环境配置文档”、“遵循某条代码规范”），而知识库又为开发过程提供规范、模板和最佳实践。

---

# 模块 C：Herness 开发进度树

## 1. 核心定位
**Herness 是一棵与需求强绑定的、由 AI 生成、人可干预的“模块分解与进度追踪树”。**
它不是甘特图，不按时间排期，而是按**软件逻辑模块分解**，用于驾驭 AI 逐步开发，确保每一步都清晰、可追溯、质量可控。

## 2. 数据结构
在需求文档（Requirement）内嵌入 `dev_plan` 字段，作为树形结构直接存储（MongoDB 天然支持嵌套文档）。

```javascript
// 需求文档中的 dev_plan 字段
dev_plan: {
  created_at: ISODate,
  updated_at: ISODate,
  root: {                         // 根节点，通常是需求标题
    id: "node_root",
    title: "员工请假功能",
    description: "整体开发计划",
    status: "in_progress",        // todo | in_progress | done | blocked
    artifacts: {                  // 关联的产物
      tech_proposal_id: "tp_001",
      repo_branch: "feat/leave",
      related_docs: ["/wiki/env-setup", "/wiki/code-style/react"]  // 关联知识库
    },
    children: [
      {
        id: "node_1",
        title: "请假表单组件",
        description: "包含日期选择、假期类型下拉、原因输入，需支持校验",
        status: "done",
        artifacts: {
          pr_number: 12,
          commit_sha: "abc123",
          code_files: ["src/components/LeaveForm.jsx"],
          tests_added: true,
          related_docs: ["/wiki/component-patterns/form"]
        },
        log: [                        // 操作日志
          { timestamp: ISODate, action: "created", detail: "初始分解" },
          { timestamp: ISODate, action: "status_change", from: "in_progress", to: "done", summary: "完成表单 UI 和校验逻辑" }
        ],
        children: []                  // 可继续细分
      },
      {
        id: "node_2",
        title: "请假列表页",
        description: "展示我的申请记录，支持筛选",
        status: "todo",
        artifacts: {},
        log: [],
        children: []
      },
      {
        id: "node_3",
        title: "审批流程 API",
        description: "后端接口：提交申请、审批操作、状态查询",
        status: "blocked",
        blocked_reason: "依赖权限系统设计完成",
        artifacts: {},
        log: [],
        children: [
          {
            id: "node_3_1",
            title: "POST /api/leave/submit",
            status: "todo",
            ...
          }
        ]
      }
    ]
  }
}
```

**关键字段说明**：
- `id`：唯一标识，便于更新。
- `status`：节点状态，严格按照 `todo → in_progress → done` 或 `todo → blocked` 流转。
- `artifacts`：存放实际产出物引用（PR、Commit、文件路径、关联文档），这是“可追溯质量”的关键。
- `log`：记录节点每次状态变更和主要操作，方便回顾“这个模块到底经历了什么”。
- `blocked_reason`：阻塞原因，帮助开发者快速识别卡点。

## 3. 生命周期与操作

### 3.1 创建开发计划
- **触发**：需求状态变为 `confirmed` 后，开发者在平台上手动点击“生成开发计划”（或未来 AI 通过 MCP 调用 `create_dev_plan` 工具自动生成）。
- **生成过程**（初期人工 + AI 辅助）：
  - 平台根据需求的 `structured.modules` 字段预生成一棵初始树（每个模块作为一个子节点）。
  - 开发者可以在树形编辑器上拖拽调整层次、增删节点、补充描述。
  - 保存后，`dev_plan` 存入需求文档。
- **设计原则**：鼓励粒度适中，一个节点最好是“一个 PR 或一个提交能完成的功能点”。

### 3.2 更新进度
- **手动更新**：开发者在需求详情页的“进度树”视图中，点击节点修改状态、添加日志、填写 artifacts（如 PR 链接）。适合不依赖 AI 的传统开发。
- **AI 驱动更新（未来 MCP）**：Claude Code 在每轮开发结束后，调用 `update_node_status`，平台自动更新节点状态并追加 log。这部分我们后面 MCP 设计时会无缝植入。

### 3.3 进度树视图（前端设计）
在需求详情页新增一个 Tab：“开发进度”。
- 展示为**交互式树状图**（可使用 `react-tree-graph` 或自定义递归组件）。
- 每个节点用颜色标记状态（灰色 todo、蓝色 in_progress、绿色 done、红色 blocked）。
- 点击节点弹出详情侧边栏：展示描述、artifacts、日志、关联知识库链接。
- 允许开发者直接在此处修改状态、添加日志（需权限：`development` 职能）。

**权限控制**：仅 `development` 和 `admin` 职能可编辑树，其他人只读（需求模块 A 的权限矩阵已覆盖）。

## 4. 与需求的关系
- `dev_plan` 是需求文档的一个子对象，生命周期跟随需求。
- 当需求发生变更（需要重新开启 `clarifying`），开发计划树可以被标记为“过时”，需要重新生成或调整。
- 进度树的完成状态可作为“需求可验收”的参考：当所有子节点均为 `done`，系统可提示需求已开发完成。

---

# 模块 D：知识库 Wiki（树状文档 + 向量检索预留）

## 1. 核心定位
为团队提供**结构化、可检索、可关联的内部文档库**，包含但不限于：
- 环境配置指南
- 代码规范
- 组件/模块使用文档
- Agent Skills 说明（如何写好需求、如何写技术方案等）
- 可复用的代码质量治理文档

它既是开发者手动查阅的 Wiki，也是未来 AI 检索的“知识基座”。

## 2. 数据模型
MongoDB 集合 `wiki_pages`，采用**树状结构（邻接列表 + 物化路径）**，方便高性能查询。

```javascript
{
  _id: ObjectId,
  title: "React 代码风格指南",
  path: "/development/code-style/react",    // 物化路径，方便前缀查询子树
  parentPath: "/development/code-style",    // 父级路径，可空
  content: "## 组件命名\n...",              // Markdown 内容
  tags: ["react", "code-style", "frontend"],
  status: "published",                     // draft | published | archived
  version: 3,
  createdBy: ObjectId,
  updatedBy: ObjectId,
  createdAt: ISODate,
  updatedAt: ISODate,
  // 预留 RAG 相关字段
  chunkIds: [ObjectId],                    // 关联的向量库 chunk ID（未来）
  embeddingModel: "text-embedding-3-small"
}
```

**说明**：
- `path`：全路径唯一索引，如 `/development/env-setup/local`。
- `parentPath`：指向父级路径，构建树易如反掌。
- `tags`：多标签，用于过滤搜索。
- 版本控制：每次编辑生成新版本，旧版本归档（可选，MVP 可省略）。
- 向量预留：未来接入向量数据库（如 Milvus、Pinecone 或 MongoDB Atlas Vector Search）时，每个文档会被切分成多个 chunk，`chunkIds` 记录这些 chunk，AI 检索时可返回最相关片段。

## 3. 页面功能设计

### 3.1 知识库首页
- 左侧：树状目录（根据 `path` 渲染），可无限嵌套。
- 右侧：文档内容区，Markdown 渲染（可用 `react-markdown` + 代码高亮插件）。
- 支持搜索：输入关键词，后端查询 `tags` 和 `title` 并返回结果列表（初期用 MongoDB 文本搜索）。

### 3.2 文档编辑页
- 权限：仅 `admin` 和 `product`（针对产品文档区域）可编辑，具体由权限矩阵控制。`development` 可提出修改建议（未来可做评论功能，MVP 跳过）。
- 编辑器：Markdown 编辑器（复用需求模块的组件）。
- 元数据编辑：标题、路径、标签、父级路径（移动页面）。
- 保存时，自动更新 `updatedAt` 和版本号。

### 3.3 文档关联与引用
- 支持在文档中通过特殊语法 `[[path/to/page]]` 内部链接（Wiki 链接），前端渲染时自动转为可点击链接。
- 需求模块、开发进度树节点中均可引用知识库路径，方便跳转。

## 4. 与开发进度树的集成
- 进度树的 `artifacts.related_docs` 字段存储知识库路径数组，前端展示为超链接。
- 开发者在处理某个节点时，可以一键跳转到相关规范文档。
- 未来 AI 调用 `get_code_guideline` 时，后端可以从需求关联的模块节点中获取相关的知识库文档，更精准地推荐。

## 5. RAG 检索的预留设计
虽然暂时不搭建向量数据库，但文档模型已为未来铺路：
- 当需要时，可以写一个脚本遍历 `wiki_pages`，将 `content` 按 Markdown 标题分段，调用 embedding API 生成向量，存入向量数据库，并回写 `chunkIds`。
- 检索 Tool 的实现：MCP 或内部搜索 API 优先走向量相似度搜索，返回最相关的片段和来源文档路径。

---

# 模块 A + C + D 联动全景图

至此，平台三大核心模块已清晰：
```
需求模块(A) ──┐
              ├─ structured (用户故事、模块、验收条件)
              ├─ dev_plan (开发进度树 C)
              └─ artifacts.related_docs ──→ 知识库 Wiki (D)
知识库(D) ←── 被进度树引用，同时提供独立浏览和搜索
```

**一个完整的开发流**：
1. 老板提交需求（A），状态变为 `confirmed`。
2. 开发人员在该需求下创建开发计划树（C），分解模块，关联知识库文档（D）。
3. 开发人员（或 AI）逐节点实现，更新状态和 artifacts，遇到疑问可随时查阅关联的知识库。
4. 所有活动都沉淀在平台中，成为后续 AI 学习和复用的数据基础。
