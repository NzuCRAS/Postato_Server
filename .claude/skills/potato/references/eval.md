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
