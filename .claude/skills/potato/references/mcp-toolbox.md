# 可调用的 MCP 能力(potato,15 工具)

> 这是你的全部「手」。按下方分组在流程各步取用;参数细节查 `herness-flow.md`。

## 需求 / 知识
- `get_requirement_detail(requirement_id)` — 需求结构化详情 + dev_plan 摘要 + open_corrections + 项目级/需求级 doc_links。**第 1 步必调**。
- `create_requirement(title, structured?, status?, project_id?, doc_links?)` — 建需求(单一事实来源)。
- `search_knowledge(query, category?, match_mode?, include_tmp?, limit?)` — 检索知识库;`category` ∈ doc/asset/standard/experience。**第 3/4/5 步用**。
- `write_knowledge(path, title, content, category?, tags?, parent_path?)` — 按 path upsert 沉淀页。**第 9 步用**。`category` ∈ doc/asset/standard/experience/**runlog**(runlog=执行文档轨迹,默认检索排除;跑完/中断回写执行文档时用,落 `/runs/<可读需求简名>-run<N>`,需求编号写文档内、不入路径)。
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
