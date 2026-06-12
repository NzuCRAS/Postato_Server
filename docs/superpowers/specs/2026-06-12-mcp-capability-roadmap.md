# Potato MCP 能力 Roadmap —— 补齐「写代码构建项目」的全部能力

> 2026-06-12 与用户对齐的能力补全规划。目标:让 MCP 能力覆盖完整开发闭环(提需求 → 规划 → 实现 → 检验 → 收尾),全程文档先行 / 可回溯 / 可查证 / 有沉淀。

## 背景

平台已具备完整的「流程骨架」:结构化需求、Herness 进度树、技术方案先行(write_tech_proposal)、中间态日志、验证闭环(verifications)、纠偏(corrections)、知识沉淀(write_knowledge)、需求↔结构树回标(relate_requirement_arch)。这套机制独立印证了业界(淘天等团队)总结的核心实践。

但对照「写代码构建项目」的完整闭环,仍有两类空洞:**②规划阶段的显式化** 与 **③实现阶段的复用弹药**(即「从代码反向抽出规范/组件喂给 AI」)。本 roadmap 补这两类。

## 原则

- **MCP = 能力原子,Skill = SOP 编排**(后续)。Skill 只能调用 MCP 已有能力,所以先备齐原子。
- 不依赖 OSS / 外部 MCP 的能力优先;依赖存储改造或外部工具的后置。
- 每个能力 = 一个平台需求,走完整 Herness。

## 五阶段能力全景(现有 ✓ / 缺口 🔲)

| 阶段 | 已有 ✓ | 缺口 🔲 |
|---|---|---|
| ① 提需求 | create_requirement(用户故事/验收/interaction_flow/模块/doc_links) | 参考 demo / 视觉素材存储(OSS) |
| ② 规划 | write_tech_proposal(绑节点)、create_dev_plan、get_architecture | 需求级「规划产出」无专门承载(技术选型/架构散在节点技术方案) |
| ③ 实现 | update_dev_plan_node、search_knowledge、project_doc_links、节点 log | 可复用资产/组件库(无)、代码规范结构化(散落)、过程文档(无专门概念) |
| ④ 检验 | verifications、acceptance_criteria、warnings | 视觉/页面感知、验收证据(截图)存储 |
| ⑤ 收尾 | write_knowledge(必做)、relate_requirement_arch | 较完整 |

## 波次清单

### 波次 1 —— 复用底座(③ 实现,最高杠杆,零外部依赖)

- **B1 可复用资产库** ⭐ 最高杠杆:让 AI 查询「项目有哪些现成组件/模块/工具库 + 接口契约 + 用法」。文档实证:私有包+调用规范可把出码率从 70% 拉到 95%。候选形态(实现前 brainstorm 选型):
  - (a) 复用结构树 L4 节点 + 强化 related_code + 挂「用法/接口契约」文档;
  - (b) 知识库开「资产」分区;
  - (c) 新建 assets 实体 + search_assets / get_asset 工具。
- **B2 代码规范结构化**:把显式代码规范从 CLAUDE.md/wiki 提为项目级可按场景检索的资产。
- **E1 需求分级 → 流程自适应**(搭车,轻量):需求/节点标「严苛等级」(需求驱动型 DO WHAT / 工程主导型 HOW TO DO),据此决定是否强制技术方案 + 人工审查。

### 波次 2 —— 规划显式化(② 规划 + ③ 过程文档,零外部依赖)

- **A1 需求级规划产出**:技术选型 + 架构决策 + 模块清单 → 一份需求级「实现规划/计划清单」。候选:扩展 write_tech_proposal 支持需求级(node 可选),或新增 create_implementation_plan。
- **B3 过程文档(活文档)**:实现中持续迭代的页面/组件说明的承载(节点/需求级,持续 upsert),区别于一次性技术方案。

### 波次 3 —— 视觉与素材(① demo + ④ 视觉,阻塞于 OSS 切片)

- **D1 视觉素材 / 参考 demo 存储**(依赖 OSS)。
- **C2 验收证据 / 截图存储**(依赖 OSS)。
- **C1 页面感知 / 功能测试**(约定接外部 Playwright 类 MCP + 平台承载证据)。

### 可选

- **E2 技术方案评审 / 打分**(给 write_tech_proposal 加评审 Agent)。

## 依赖与节奏

- 波次 1、2 相互独立,均可立即开始;波次 3 必须先做 OSS 存储切片。
- 推荐顺序:波次 1(先 B1)→ 波次 2 → OSS 切片 → 波次 3。

## 落地方式

- 本 roadmap 落 spec(本文件)+ 知识库 /agent/mcp-roadmap(可查)+ 平台需求群(自举)。
- 每个能力单独建需求,走完整 Herness(技术方案先行 → 拆节点 → 实现 → 验证 → 沉淀),完成后 relate_requirement_arch 回标结构树。
