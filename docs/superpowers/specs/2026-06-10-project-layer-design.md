# 项目(Project)层 + 工程化「项目结构树」 —— 设计 spec

> 状态:已实现并落地(2026-06-10)。本文记录最终设计;过程方法论见 `docs/项目树形架构设计.md`,实现计划见 `docs/superpowers/plans/2026-06-09-...`(同会话生成)。

## 背景与目标
最大粒度由「需求」提升为「项目」,补齐工程化顶层组织。一个项目 = 多代码仓库 + 业务域驱动的 L0–L4 结构树 + 关联文档(设计/规范/效果参考,与知识库建索引)+ 若干需求。

## 关键决策
- 项目层「一次做全」:数据模型 + 迁移 + CRUD + MCP + 完整前端 + 需求/wiki 归属。
- 结构树**主维度 = 业务域**(L0系统→L1领域→L2子域/限界上下文→L3模块→L4组件);其余维度用节点 **type + tags**,不建多棵树。
- **L0–L2 管理树**(手动、可评审);**L3–L4 工程树**(`.project.yaml` 声明 + 平台同步自动生成)。
- 三阶段一次做全:骨架(手动 L0–L2 + 挂资源 + 生命周期)、自动连接(`.project.yaml` 同步)、动态视图(标签跨切面)。
- 避坑:别用组织架构代替业务架构、别过早拆物理服务、树不作权限唯一依据、节点有生命周期(归档/移动留痕)。
- OSS 存储改造:本轮不做,另立切片。

## 数据模型
- `projects`:`{ _id, name, descriptionMd, repos:[{id,name,url,provider,default_branch}], docLinks:[{type,title,path}], createdBy, createdAt, updatedAt }`。默认项目 `_id="default"`(seed,含真实仓库),接住存量 requirements/wiki(其 `projectId` 本就为 "default")。
- `arch_nodes`(独立集合,物化路径):`{ _id, projectId, parentId, path(项目内唯一), layer(L0-L4), type, title, description, tags[], related_docs[], related_code[], related_requirements[], source(manual|sync), repoId, status(active|archived), order, createdAt, updatedAt }`。
- `Requirement`:`projectId` 升真实 id(create 默认 "default");新增 `docLinks[]`。
- `WikiPage`:`projectId`(本轮前端 wiki 暂未按项目过滤 —— 见"未尽事项")。

## 后端
- `project` 包:Project 实体/Repository/Service/Controller(`/api/v1/projects` CRUD + repos 增删 + doc-links 增删)。
- `archnode` 包:ArchNode 实体/Repository/Service/Controller(`/api/v1/projects/{pid}/arch`):`GET`(tag/layer 过滤→命中+祖先链)、`POST /nodes`、`PATCH /nodes/{id}`、`POST /nodes/{id}/archive`(子树)、`POST /nodes/{id}/move`(重算物化路径)、`POST /arch/sync`(幂等 reconcile L3+;消失归档;不覆盖手动)。
- `common/DocLink`(项目/需求共用)。`DevPlanService` 小修:`in_progress` 仅当节点 `todo` 才翻转(防回退)。
- 权限 seed:`project/view`(all)、`project/create|edit`(product)、`arch/edit_management`(development,product)、`arch/sync`(development)。
- 单测:Project(2)、ArchNode(6,含路径/标签视图/归档/移动/sync)、DevPlan in_progress 不回退;全量 26 通过。

## MCP(9→12)
- `get_project_detail`(repos/docLinks/结构树精简/需求摘要)、`get_architecture`(tag/layer 过滤)、`sync_project_modules`(`.project.yaml` 推送)。`get_requirement_detail` 附 `project_id`/`doc_links`。

## 前端
- `ProjectContext`(当前项目/列表/切换,localStorage)+ 顶栏项目选择器 + 「项目」菜单。
- `ProjectsPage`(列表/新建);`ProjectDetailPage`(Tabs:概览/仓库/结构树/文档/需求);`ArchitectureTree`(L0-L4 树 + 节点详情抽屉 + 新建/归档 + 标签跨切面过滤;sync 节点只读)。
- 需求列表/创建按当前项目作用域;需求详情可关联设计/效果文档(docLinks)。

## 验证
- 后端 26/26;前端/MCP tsc 通过;运行期:默认项目 seed(含真实仓库)、arch 建 L0-L2 + sync L3 + 标签视图含祖先,均 REST 验证通过。

## 未尽事项 / 后续
- 前端 **wiki 按项目过滤**未做(后端 WikiService.list/search 暂未加 projectId 过滤;知识跨项目复用本身有价值,留待评估)。
- 节点 `related_requirements` 双向同步、节点 rename 联动 path(当前 path 为稳定标识)。
- `.project.yaml` 由 CI 自动推送(当前靠 AI/CI 手动经 MCP);平台直连 GitHub 扫描。
- OSS 存储切片;多项目权限隔离;向量检索。
