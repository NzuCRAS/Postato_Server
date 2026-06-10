好的，现在我们把焦点对准 MCP Server——这个连接 Claude Code 与平台“逻辑中枢”的关键桥梁。基于之前设计的模块 A、C、D、E，MCP 将不仅仅是三个孤立的 Tool，而是**覆盖开发全流程的 Tool 集合**，让 AI 能真正“驾驭”需求→计划→编码→反馈的闭环。

---

# MCP Server 详细设计

## 1. 定位与边界
- **定位**：MCP Server 是平台对 AI 暴露的统一接口层，将平台的业务能力封装成 Claude Code 可调用的工具，同时强制权限管控和契约遵守。
- **边界**：
  - MCP Server **不直接操作数据库**，所有数据操作通过调用后端 Java API 完成。
  - MCP Server 负责：协议转换、API Key 鉴权、权限判断、请求转发、响应格式化。
  - 所有业务逻辑（如需求结构化解析、进度树操作、知识库检索）由后端 API 提供。

## 2. 整体通信流
```
Claude Code               MCP Server (TS)              Backend API (Java)         MongoDB
    │                          │                              │                       │
    │── Tool Call Request ────>│                              │                       │
    │  (含 Authorization头)    │── 鉴权：查用户+权限         │                       │
    │                          │── hasPermission(user, ...)  │                       │
    │                          │── 调用对应API ────────────>│                       │
    │                          │                              │── CRUD/搜索 ────────>│
    │                          │                              │<──── 结果 ───────────│
    │                          │<── 格式化Tool响应 ─────────│                       │
    │<── Tool Call Response ──│                              │                       │
```

## 3. MCP Tool 列表（MVP，覆盖开发闭环）

| Tool 名称 | 功能 | 对应模块 | 权限职能要求 |
|-----------|------|----------|--------------|
| `get_requirement_detail` | 获取需求结构化详情（含 user stories, modules, interaction flow） | A | `development`, `testing`, `product` |
| `search_knowledge` | 按主题/标签搜索知识库文档片段 | D | 所有认证用户 |
| `write_tech_proposal` | 创建技术方案文档并关联需求，可选更新进度树 | C (含A) | `development` |
| `create_dev_plan` | 为需求创建初始开发计划树 | C | `development` |
| `update_dev_plan_node` | 更新某个进度节点的状态、artifacts、日志 | C | `development` |
| `get_ticket` | 获取指定工单详情 | E | `development`, `testing`, `product` |
| `create_ticket` | 创建新工单/Bug | E | 所有认证用户 |
| `get_code_guideline` | 获取特定技术栈/主题的代码规范（其实是 `search_knowledge` 的别名，增加语义） | D | 所有认证用户 |

**说明**：
- `get_code_guideline` 本质上调用与 `search_knowledge` 相同 API，但通过参数 `scope="code_guideline"` 限制范围，便于 Claude Code 理解意图。
- `get_ticket` 和 `create_ticket` 让 AI 可以参与 Bug 反馈循环：开发者处理需求遇到依赖问题时，可让 AI 提工单跟踪。
- 为什么需要 `create_dev_plan` 和 `update_dev_plan_node`？因为这是 Herness 驾驭机制的核心：AI 必须自己生成计划并逐节点推进。

## 4. 核心 Tool 详细 Schema

### 4.1 `get_requirement_detail`
**输入**：
```json
{
  "requirement_id": "req_1a2b3c"
}
```
**处理**：
- 鉴权：`hasPermission(user, 'requirement', 'view')`（需 `development`, `testing`, `product` 之一）
- 调用后端 `GET /api/v1/requirements/{requirement_id}`，后端返回需求 JSON。
- MCP 返回格式化版本，移除不必要的 `_id`, `createdBy` 等字段，突出 `structured` 和 `dev_plan` 摘要。

**输出**：
```json
{
  "title": "员工请假功能",
  "status": "confirmed",
  "structured": {
    "user_stories": ["..."],
    "modules": [
      {
        "name": "请假表单",
        "description": "...",
        "acceptance_criteria": ["..."]
      }
    ],
    "interaction_flow": "..."
  },
  "dev_plan_summary": {   // 仅返回进度树根节点及一级子节点状态
    "root_status": "in_progress",
    "nodes": [
      { "id": "node_1", "title": "请假表单组件", "status": "done" },
      { "id": "node_2", "title": "请假列表页", "status": "todo" }
    ]
  }
}
```
**契约要求**：AI 收到需求后，必须先调用此 Tool，理解目标。

### 4.2 `search_knowledge`
**输入**：
```json
{
  "query": "React 组件规范",
  "tags": ["react", "code-style"],   // 可选
  "scope": "all"                      // all | code_guideline | env_setup | agent_skill
}
```
**处理**：
- 鉴权：`hasPermission(user, 'wiki', 'read')`（所有用户）
- 调用后端 `GET /api/v1/wiki/search?q=...&tags=...&scope=...`，后端使用 MongoDB 文本搜索或未来向量检索。
- 返回最相关的 3 个文档片段和路径。

**输出**：
```json
{
  "results": [
    {
      "title": "React 组件开发规范",
      "path": "/development/code-style/react",
      "snippet": "## 组件命名 使用 PascalCase...",
      "relevance": 0.95
    }
  ]
}
```

### 4.3 `write_tech_proposal`
**输入**：
```json
{
  "requirement_id": "req_1a2b3c",
  "title": "请假模块技术方案",
  "content": "## 架构设计...",
  "linked_nodes": ["node_1", "node_2"],   // 关联的进度树节点
  "update_dev_plan": true                 // 是否自动将关联节点状态从 todo -> in_progress（表示进入技术设计阶段）
}
```
**处理**：
- 鉴权：`hasPermission(user, 'tech_proposal', 'create')`（`development`）
- 调用后端 `POST /api/v1/tech-proposals`，传递上述 JSON，后端创建技术方案文档，并关联需求。
- 如果 `update_dev_plan` 为 true，后端额外调用进度树更新，将 `linked_nodes` 中状态为 `todo` 的节点改为 `in_progress`，并追加日志：“AI 生成技术方案，开始实现”。
- 返回创建的方案 ID 和 URL。

**输出**：
```json
{
  "tech_proposal_id": "tp_001",
  "url": "https://platform.example.com/tech-proposals/tp_001"
}
```

### 4.4 `create_dev_plan`
**输入**：
```json
{
  "requirement_id": "req_1a2b3c",
  "plan_tree": {                     // AI 生成的树状计划
    "nodes": [
      {
        "title": "请假表单组件",
        "description": "实现表单UI及校验",
        "children": [
          { "title": "日期选择器", "description": "..." },
          { "title": "假期类型下拉", "description": "..." }
        ]
      },
      {
        "title": "请假列表页",
        "description": "展示申请记录列表",
        "children": []
      }
    ]
  }
}
```
**处理**：
- 鉴权：`hasPermission(user, 'dev_plan', 'create')`（`development`）
- 调用后端 `POST /api/v1/requirements/{id}/dev-plan`，后端根据 `plan_tree.nodes` 构建完整的 `dev_plan` 树（自动分配 ID、设置状态为 `todo`），并保存到需求文档。
- 如果已存在计划，返回错误并给出已存在的计划概要，让 AI 调用 `update_dev_plan_node` 进行增量修改。

**输出**：
```json
{
  "dev_plan_id": "dp_001",
  "root_id": "node_root",
  "nodes_count": 5
}
```

### 4.5 `update_dev_plan_node`
**输入**：
```json
{
  "requirement_id": "req_1a2b3c",
  "node_id": "node_1",
  "updates": {
    "status": "done",                // 新状态（todo/in_progress/done/blocked）
    "artifacts": {
      "pr_number": 12,
      "commit_sha": "abc123",
      "code_files": ["src/components/LeaveForm.jsx"]
    },
    "log_message": "完成表单校验，PR已合并",
    "blocked_reason": null           // 若状态为 blocked，则必填
  }
}
```
**处理**：
- 鉴权：`hasPermission(user, 'dev_plan', 'update')`（`development`）
- 调用后端 `PATCH /api/v1/requirements/{id}/dev-plan/nodes/{node_id}`，传递更新字段。
- 后端执行状态变更、追加日志、更新 artifacts。
- 后端检查依赖：如果某个节点标记为 `done`，而其子节点未完成，可返回警告。

**输出**：
```json
{
  "node_id": "node_1",
  "new_status": "done",
  "warnings": []       // 如有依赖问题，返回提示
}
```

### 4.6 `create_ticket`（轻量）
**输入**：
```json
{
  "title": "日期选择器月份切换无效",
  "type": "bug",
  "description": "复现步骤：...",
  "requirement_id": "req_1a2b3c",    // 可选
  "dev_plan_node_id": "node_1",      // 可选
  "priority": "high"
}
```
**处理**：鉴权后调用后端 `POST /api/v1/tickets`，创建工单，返回工单 ID 和链接。

### 4.7 `get_ticket`（可选）
让 AI 查询自己创建的工单状态，实现闭环。

## 5. 权限集成
MCP Server 使用与 Web 端相同的权限模型和规则配置文件（`permissions.config.ts`）。具体流程：
- 每个 Tool 调用前，MCP 中间件：
  1. 从请求头 `Authorization: Bearer mcp_xxxx` 提取 API Key。
  2. 从内存缓存中查询 Key 对应的用户对象（`{userId, functions}`）。
  3. 调用 `hasPermission(user, resource, action)`（从配置文件加载的规则）。
  4. 通过则转发请求，否则返回 MCP 错误 `"User lacks permission for this tool"`。

**权限规则配置（mcp部分）**：
```javascript
mcp_tool: {
  get_requirement_detail: { requiredFunctions: ['development', 'testing', 'product'] },
  search_knowledge: true,  // 所有认证用户
  write_tech_proposal: { requiredFunctions: ['development'] },
  create_dev_plan: { requiredFunctions: ['development'] },
  update_dev_plan_node: { requiredFunctions: ['development'] },
  create_ticket: true,
  get_ticket: { requiredFunctions: ['development', 'testing', 'product'] }
}
```

## 6. API Key 管理（Web 端功能）
- **生成**：用户设置页面，点击“生成 MCP Key”，后端生成随机字符串 `mcp_live_xxxx`（或 `mcp_test_xxxx`），存入 `users` 集合的 `apiKeys` 数组（可多个）。
- **存储**：`apiKeys: [{ key: "mcp_live_xxxx", name: "我的ClaudeCode", createdAt: ... }]`。
- **MCP Server 缓存**：启动时从 MongoDB 加载所有用户的 `apiKeys` 到内存 Map，并每 5 分钟轮询更新（或通过 Webhook 实时更新）。
- **撤销**：用户删除某个 Key，从数组移除，MCP 下次刷新后立即失效。

## 7. 契约与 Prompt 工程（驾驭 AI）
仅仅有 Tool 还不够，还需要在 Claude Code 的**系统提示或项目配置**中约定规则，确保 AI 遵守 Herness 流程。

**推荐的 Claude Code 配置（`.claude/settings.json` 或项目 prompt）**：
```
你是一名遵循平台 Herness 开发流程的 AI 开发者。在接到开发任务后，必须严格遵守以下步骤：
1. 使用 `get_requirement_detail` 获取完整需求，理解用户故事、模块和验收条件。
2. 使用 `search_knowledge` 获取相关的代码规范和最佳实践。
3. 使用 `create_dev_plan` 为需求创建模块化开发计划树（如无现有计划）。
4. 在计划树的每个叶子节点，按顺序开发。开发前，用 `update_dev_plan_node` 将节点状态标记为 `in_progress`。
5. 完成一个节点后，生成必要的代码、测试，并通过 `update_dev_plan_node` 将状态更新为 `done`，同时附上 artifacts（PR链接、修改的文件等）。
6. 如果遇到问题或依赖，将节点标记为 `blocked`，并说明原因，必要时使用 `create_ticket` 提工单。
7. 所有开发遵循从知识库获取的规范，技术方案可使用 `write_tech_proposal` 回存。
8. 保持进度树状态实时更新，这将是你的“工作日志”。
```

平台甚至可以提供一个“生成 Claude Code 配置”的按钮，自动生成包含上述 prompt 和 MCP 连接信息的配置文件，开发者下载后放入项目即可。

## 8. 实现建议
- **技术栈**：TypeScript，使用官方 `@modelcontextprotocol/sdk`，搭配 Express 或直接使用 SDK 的 Streamable HTTP Transport。
- **项目结构**：
  ```
  mcp-server/
  ├── src/
  │   ├── index.ts          # MCP Server 入口
  │   ├── tools/            # 每个 Tool 的实现
  │   ├── auth.ts           # 鉴权与权限
  │   ├── api-client.ts     # 后端 API 调用封装
  │   └── config.ts         # 权限规则加载
  ├── package.json
  └── tsconfig.json
  ```
- **部署**：与后端 API 部署在同一云服务器，使用不同端口（如 3001），通过 Nginx 反向代理暴露 HTTPS。
- **安全**：强制 HTTPS；API Key 仅通过 Authorization 头传输；限制 MCP 可访问的 API 路径，避免越权。

## 9. 未来扩展
- 为 PM 角色提供 `analyze_requirement_clarity` Tool，调用需求澄清 AI 服务。
- 增加 `run_tests`、`deploy_preview` 等运维类 Tool。
- 支持项目级别的 MCP 配置，不同项目连接不同知识库范围。

---

至此，MCP 设计完整嵌入平台，实现了我们最初的愿景：**通过结构化需求和 Herness 进度树，让 AI 不再“一步生成”，而是步步为营、可追溯可干预，最终向端到端高质量交付迈进**。