# 结构树写入能力(upsert-tree)+ 平台架构自举 —— 设计 spec

> 状态:已实现并落地(2026-06-11)。需求 id `6a298eb70a05cb693d9a4ea4`(default 项目)。

## 背景
项目结构树(`arch_nodes`,业务域 L0–L4)此前只有两种写入途径:前端/REST 单节点 `POST /arch/nodes`,以及 MCP `sync_project_modules`(仅 reconcile L3+ 工程树、父须已存在、一次挂一层)。**经 MCP 写「管理树 L0–L2」/一次写一棵多层子树**是空白,导致无法纯经 MCP 把完整架构写进平台。本次补齐该能力,并以「用平台开发平台」的方式完成首次自举:把 Potato 平台自身架构写入结构树。

## 能力设计
### 后端 `POST /api/v1/projects/{pid}/arch/upsert-tree`(权限 `arch/edit_management`)
- 入参 `UpsertTreeRequest { parent_path?, nodes: TreeNode[] }`;`TreeNode { title, layer?, type?, description?, tags?, related_docs?, related_code?, children? }`。
- `ArchNodeService.upsertTree`:
  - `parent_path` 空=挂根(建 L0);非空须为已存在路径,否则 400。
  - 递归每个节点:`path = parentPath + "/" + title`,按 `(projectId, path)` 幂等 —— 命中则更新字段并复活(`status=active`)、**保留原 source**;否则新建 `source=manual`。
  - `layer` 显式优先,缺省 `autoLayer(parentLayer)`(根=L0,否则父层 +1)。
  - **逐节点即时保存**,使子节点能取到父节点 id(自动建中间层)。
  - 返回 `{created, updated, paths}`。
- 单测 +2:嵌套创建+自动 layer、幂等更新保留 source(archnode 全套 8 通过)。

### MCP `upsert_architecture`(工具 12→13)
包装上述端点:`project_id` / `parent_path?` / 递归 `nodes`。与 `sync_project_modules` 互补 —— 本工具维护**手动业务域骨架**(`source=manual`),后者 reconcile **工程树**(`source=sync`、消失归档);两者按 `source` 区分、互不覆盖。

## 自举结果
用该能力把 Potato 平台业务域架构写入 `default` 项目:**27 节点 = 6 领域(用户/需求/研发过程/知识/项目/集成)/ 9 上下文 / 11 组件**。已存在的 4 节点(Potato 平台 / 用户域 / 认证上下文 / JWT 实现)幂等更新,JWT 实现保留 `sync` 来源,其余 `source=manual`。`get_architecture` 全量校验层级正确;标签『安全』跨切面视图返回 8 节点(含祖先链 Potato 平台→用户域),验证通过。

取舍:工单域(模块 E,未实现)暂不录入;前端作横切(用 related_code/标签体现),不单列「前端域」;L3 本次手动录入示范,未来工程树可改由各仓库 `.project.yaml` 经 `sync_project_modules` 维护。

## 过程(Herness 自举)
需求 → 进度树(node_1 后端 / node_2 MCP / node_3 录入 / node_4 收尾),每代码节点真实 commit + 验收回填。node_3 产物为平台内架构数据(非代码 commit),无产物软警告已在工作日志豁免说明。

## 已知约束 / 后续
- 新增 MCP 工具名需**重连 MCP** 才在当前会话可见(工具列表启动时固定);故 node_3 首次录入经 REST 端点(同一能力)。
- MCP 暂无 `create_requirement`(建需求经 REST `POST /requirements`);可后补。
- 节点 rename 未联动 path(path 为稳定标识);`.project.yaml` CI 自动推送、OSS 存储仍为既有 backlog。
