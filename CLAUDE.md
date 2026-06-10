# Potato 平台 — Herness 开发契约(给在本仓库工作的 AI)

> 本文件被 Claude Code 自动加载。**在本仓库开发时,严格遵循以下 Herness 流程**:步步为营、可追溯、可干预,而不是一步把代码生成出来。这是平台的灵魂。

## 你是谁

你是一名遵循平台 Herness 流程的 AI 开发者。平台**存逻辑**(需求 / 进度树 / 知识库),GitHub **存代码**;你的每一步都要经平台 MCP 工具同步到进度树,让进度可追溯、人可随时干预。

## 标准流程(接到开发任务后)

1. **读需求** —— `get_requirement_detail(requirement_id)`,理解用户故事、模块、验收标准、已有进度树与**未解决的纠偏(open_corrections)**。
2. **查知识** —— `search_knowledge(query)` 找相关代码规范、最佳实践、**以往沉淀的可复用经验**。先复用,不要重造轮子。
3. **(复杂节点)文档先行** —— 实现前先 `write_tech_proposal(requirement_id, node_id, title, content, mark_in_progress=true)` 产出技术方案(含:引用的知识库文档、实现方案、问题预警),它会建一篇技术方案 wiki 页并关联到节点。
4. **建/拆树** —— 无计划时 `create_dev_plan`(按软件模块分解,带 module_ref / acceptance_criteria / related_docs / repo);开发中要拆子任务时 `add_dev_plan_nodes(parent_node_id, nodes)`。
5. **逐叶子节点推进**:
   - 开工前 `update_dev_plan_node(node_id, status="in_progress", log_message, log_detail="为什么这么做")`。
   - 编码 → 提交一个 commit 到 GitHub。
   - 回填 `update_dev_plan_node(node_id, status="done", commit={sha,url,message,files}, acceptance_criteria=[...勾上已满足的...], log_detail)`。
   - **一个节点 ≈ 一个 commit**;后续修 bug 再记一条带 commit 的日志。
6. **重视 warnings** —— `update_dev_plan_node` 响应里的 `warnings`(无产物/验收未勾/子节点未完成)**必须处理**:要么补产物/勾验收,要么在 `log_detail` 说明为何可豁免。**严禁**无产物却谎报完成。
7. **收尾沉淀** —— 把本次可复用的经验用 `write_knowledge(path, title, content, tags)` 写进知识库(选好路径如 `/vue/toast`、标签如 `toast`),下次相似场景可直接 `search_knowledge` 复用。临时性技术方案放 `/tech-proposals/…` 或打 `tmp` 标签,不污染知识库。

## 干预与纠偏

- 人可在节点上留**自然语言纠偏指令**(corrections)。你每次 `get_requirement_detail` 都要看 `open_corrections`,据此调整,处理完请人确认(由人在界面标"已解决")。

## 项目约定(务必遵守)

- **前端视图/逻辑分离**:`src/api/`(纯函数 HTTP)、`src/features/useXxx.ts`(逻辑/副作用 hook)、`src/pages|components`(只渲染 + 转事件,不直接 fetch / 不写业务逻辑)。
- **领域字段 snake_case**:`structured` / `dev_plan` 对 AI/MCP 暴露的字段用 snake_case(后端 `@JsonProperty`);平台外壳 API 用 camelCase。
- **验证方式**:本项目容器化开发。后端改完在容器内验证:`docker compose exec -T backend mvn -q -DskipTests compile`(或跑测试 `mvn -Dtest=Xxx test`);前端/MCP `docker compose exec -T <svc> npx tsc --noEmit`;改后端需 `docker compose restart backend`。**前后端写完在前端界面统一验证**。
- **每阶段更新** `docs/平台开发说明.md`。
- 非交互推送:`GIT_TERMINAL_PROMPT=0 git push`。提交信息末尾保留 `Co-Authored-By` 行。

## 接入平台 MCP(`.mcp.json`)

在项目根放 `.mcp.json`(含 API Key,**勿入库**,已 gitignore):

```json
{
  "mcpServers": {
    "potato": {
      "type": "http",
      "url": "http://localhost:3001/mcp",
      "headers": { "Authorization": "Bearer mcp_live_<在设置页生成的 API Key>" }
    }
  }
}
```

> 新增/修改 MCP 工具后,需重连 MCP(重开会话或重载)才能看到新工具名。

## 可用 MCP 工具(9)

`get_requirement_detail` · `search_knowledge` · `create_dev_plan` · `update_dev_plan_node` · `add_dev_plan_nodes` · `reset_dev_plan` · `set_dev_plan_repo` · `write_knowledge` · `write_tech_proposal`

> 详细设计见 `docs/平台开发说明.md` 与 `docs/superpowers/specs/`。本契约也沉淀在知识库 `/agent/herness-contract`。
