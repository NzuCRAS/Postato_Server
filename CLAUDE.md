# Potato 平台 — Herness 开发契约(给在本仓库工作的 AI)

> 本文件被 Claude Code 自动加载。**在本仓库开发时,严格遵循以下 Herness 流程**:步步为营、可追溯、可干预,而不是一步把代码生成出来。这是平台的灵魂。

## 你是谁

你是一名遵循平台 Herness 流程的 AI 开发者。平台**存逻辑**(需求 / 进度树 / 知识库),GitHub **存代码**;你的每一步都要经平台 MCP 工具同步到进度树,让进度可追溯、人可随时干预。

## 标准流程(接到开发任务后)

1. **读需求 + 读规范** —— `get_requirement_detail(requirement_id)`,理解用户故事、模块、验收标准、已有进度树与**未解决的纠偏(open_corrections)**;**动手前必读**返回里的 `project_doc_links`(项目级代码/视觉/契约规范)与需求级 `doc_links`,按既有规范写,别等写完才发现违规。
2. **查知识** —— `search_knowledge(query)` 找相关代码规范、最佳实践、**以往沉淀的可复用经验**。先复用,不要重造轮子。
3. **(复杂节点)文档先行** —— 实现前先 `write_tech_proposal(requirement_id, node_id, title, content, mark_in_progress=true)` 产出技术方案(含:引用的知识库文档、实现方案、问题预警),它会建一篇技术方案 wiki 页并关联到节点。
4. **建/拆树** —— 无计划时 `create_dev_plan`(按软件模块分解,带 module_ref / acceptance_criteria / related_docs / repo);开发中要拆子任务时 `add_dev_plan_nodes(parent_node_id, nodes)`。
5. **逐叶子节点推进**:
   - 开工前 `update_dev_plan_node(node_id, status="in_progress", log_message, log_detail="为什么这么做")`。
   - 编码 → 提交一个 commit 到 GitHub。
   - **done 前本地验证**(编译/测试/lint)→ 回填时带 `verifications=[{kind,command,result,summary}]` 上报;**done 时无任何 pass 验证会软警告**。
   - 回填 `update_dev_plan_node(node_id, status="done", commit={sha,url,message,files}, verifications=[...], acceptance_criteria=[...勾上已满足的...], log_detail)`。
   - **一个节点 ≈ 一个 commit**;后续修 bug 再记一条带 commit 的日志。
6. **重视 warnings** —— `update_dev_plan_node` 响应里的 `warnings`(无产物/无通过验证/验收未勾/子节点未完成)**必须处理**:要么补产物/验证/勾验收,要么在 `log_detail` 说明为何可豁免。**严禁**无产物或无验证却谎报完成。
7. **收尾沉淀(必做,非可选)** —— 节点/需求完成后**必须**回顾本轮并显式处理两件事(即使判断「无需沉淀」也要先判断再跳过,不可默认略过):
   - **沉淀经验**:若有可复用经验(代码规范 / 踩坑 / 通用模式),用 `write_knowledge(path, title, content, tags)` 写进知识库(选好路径如 `/vue/toast`、标签如 `toast`),下次 `search_knowledge` 可复用;临时技术方案放 `/tech-proposals/…` 或打 `tmp` 标签,不污染知识库。
   - **回标结构树**:需求开发完成时,用 `relate_requirement_arch(requirement_id, links)` 把需求关联并回标其落地的结构树**叶子**业务模块 `impl_status`(闭环第⑨/⑩步),让蓝图反映真实进度。

## 项目结构树共建(arch tree)

**建/改项目结构树时必须走「逐层共建协议」,禁止一次性生成整棵树。**
1. 先 `search_knowledge("结构树 逐层共建")` 或读知识库 `/agent/arch-coauthoring` 取协议全文。
2. 按 **BFS 逐层**(L0 系统→L1 领域→L2 限界上下文→L3 业务模块)与用户**对话澄清**,每层确认后用 `upsert_arch_layer(project_id, parent_path, nodes)` 写**该层**(无 children,一次一层)。
3. **层级铁律**:L3=业务模块,`XxxService` 等实现单元属 L4(交 `sync_project_modules`);`description` 必须写清**职责 + 边界**,勿同义反复。

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

## 可用 MCP 工具(15)

- **需求/知识**:`get_requirement_detail`(附项目级 `project_doc_links`) · `create_requirement` · `search_knowledge` · `write_knowledge` · `write_tech_proposal`
- **进度树**:`create_dev_plan` · `update_dev_plan_node` · `add_dev_plan_nodes` · `reset_dev_plan` · `set_dev_plan_repo`
- **项目/结构树**:`get_project_detail` · `get_architecture` · `upsert_arch_layer`(逐层共建,见 `/agent/arch-coauthoring`) · `sync_project_modules` · `relate_requirement_arch`(需求完成回标结构树 impl_status)

> 详细设计见 `docs/平台开发说明.md` 与 `docs/superpowers/specs/`。本契约也沉淀在知识库 `/agent/herness-contract`。
