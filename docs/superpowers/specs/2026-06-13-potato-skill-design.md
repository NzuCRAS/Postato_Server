# `potato` 开发 SOP skill — 设计 spec

> 状态:设计已对齐(2026-06-13),待实施。把平台开发 SOP 从 CLAUDE.md 的「散文契约」升级为一个**可触发、结构化、渐进式披露**的 Claude Code skill,作为「教 LLM 用 MCP + 遵守 SOP」的核心载体。skill 名 **`potato`**(对齐平台名,致敬育碧传奇土豆服务器)。

## 背景与目标
平台要回答的核心问题:**怎么让 LLM 学会用我们的 MCP、并遵守我们的 SOP?** 答案是三层叠加:
- **教(被动告知)**:MCP 工具 `description` + CLAUDE.md + **本 skill**——把流程编码成 AI 主动遵循的操作手册。
- **管(主动强制)**:平台服务端护栏(软警告 / blocked 硬校验 / actor 渠道推断),不依赖 LLM 自觉。**已建。**
- **验(确认有效)**:eval——同任务挂/不挂 skill 跑两遍对比。

本 spec 做「教」这一层,并让它**咬合**已有的「管」。依据 roadmap 原则「MCP=能力原子,Skill=SOP 编排」与《Agent skill 迭代式编写实战》的方法论(三层渐进式披露 / 决策树 / 负向约束配替代 / 执行后自查 / eval)。现状:CLAUDE.md 已是一份散文版 SOP,但被动全量加载、无结构化编排;项目无 `.claude/skills/`。

## 已对齐的决策
- **定位 = 方案 A**:Potato **自用**强化 SOP skill(dogfood 优先);产品级分发(存知识库 + skills.sh 式安装)与需求分级自适应(E1)**留后续**。
- **名 `potato`**,落 `.claude/skills/potato/`。
- **三层渐进式披露**:`SKILL.md`(L1)+ `references/`(L2);**v1 不做 `scripts/`**(暂用不上;高频确定性步骤待 SOP 跑顺、环境稳后再下沉)。
- skill 是「教」层,**咬合**平台「管」层(`computeWarnings`/blocked),用 eval「验」。
- **CLAUDE.md 瘦身**为指向 skill 的指针 + 不可协商底线。

## 文件结构
```
.claude/skills/potato/
├── SKILL.md                 # L1 始终轻量:frontmatter + 一轮闭环显式流程 + 执行后自查
└── references/              # L2 按需加载
    ├── mcp-toolbox.md       # 可调用的 MCP 能力全清单(15 工具,按需求/知识/进度树/项目结构树分组 + 何时用)
    ├── board-injection.md   # category 四板块(standard/asset/experience/doc)注入时机与方式
    ├── herness-flow.md      # 各步 MCP 工具参数细节 + 一节点一 commit + actor 渠道
    ├── promotion.md         # tmp 技术方案 → experience 晋升(去 tmp + 设 category + 改 path)
    ├── corrections.md       # open_corrections 读取与处理、处理后请人确认
    └── templates.md         # 技术方案 / 经验沉淀 模板
```
(v1 无 `scripts/`。)

## SKILL.md (L1)
**frontmatter**:`name: potato`;`description` 写明触发场景 + 词(如「实现需求 / 开发任务 / 走 Herness 流程 / 连了平台 MCP 后开发」)——触发靠 agent 对 description 的语义识别,需含具体触发词。

**正文(总-分):**
- **总(核心规则)**:你是遵循平台 Herness 流程的 AI 开发者;每个开发任务走下面的回路;**平台会用软警告 + blocked 硬校验顶回不合规的步骤,别绕**。
- **一轮闭环的显式流程**(每步:动作 → 调用的 MCP 能力 → 对应《关于一次完整的闭环设计》支柱;参数细节下沉 references。**这条流程就是八支柱的可执行化**):
  1. **弄清需求** → `get_requirement_detail`(structured/验收/dev_plan/`open_corrections`/项目级+需求级 doc_links);有未解决纠偏先据其调整(→`corrections.md`)。〔业务需求〕
  2. **弄清代码基础与架构约束** → `get_project_detail` / `get_architecture`(读业务域结构树、`related_code` glob、impl_status),据此打开实际代码摸清现状与既有模式。〔项目架构与技术约束〕
  3. **注入代码规范** → `search_knowledge(category="standard")` 必读(接口契约 / 数据模型 / 代码风格)。〔代码规范〕
  4. **查可复用资产** → `search_knowledge(category="asset")` 取清单,需要时取详情(含 demo / 资产 URL)。〔可复用代码〕
  5. **(拿不准时)检索先验经验** → `search_knowledge(category="experience", q=...)` 触发式。〔隐式经验〕
  6. **制定实现计划** → 复杂 / 需求驱动先 `write_tech_proposal(mark_in_progress)` 文档先行;再 `create_dev_plan`(module_ref/验收/related_docs/repo)/`add_dev_plan_nodes` 拆树(粒度≈一个 commit)。〔任务拆分〕
  7. **逐叶子实现** → `update_dev_plan_node(in_progress, log_detail=为什么)` → 编码(遵第 3 步规范、复用第 4 步资产)→ 一个 commit → done 前本地验证(编译/测试/lint)→ `update_dev_plan_node(done, commit, verifications, 勾验收)`;遇阻 `blocked` + 原因。〔验收标准 + 短反馈循环〕
  8. **处理 warnings** → 平台软警告顶回:补产物 / 验证 / 勾验收,或 `log_detail` 说明豁免。〔协作纠偏〕
  9. **收尾沉淀 + 回标** → `write_knowledge(category=experience)` 落 `/experience/` 沉淀经验;`relate_requirement_arch` 回标结构树叶子 impl_status。〔知识沉淀〕
- **执行后自查清单**(强制反射节点,踩在平台 warnings 上):
  - [ ] 每个 done 节点有 pass 的 verification?
  - [ ] 验收点都勾了 / 未勾的说明了?一节点≈一 commit 且挂上了?
  - [ ] 可复用经验沉淀了?`write_knowledge(category=experience)` 落 `/experience/`。
  - [ ] 需求完成 → `relate_requirement_arch` 回标结构树叶子 impl_status?
  - [ ] 注入的规范真遵守了 / 可复用件真复用了?

## references/ (L2,按需加载)
- **mcp-toolbox.md**:可调用的 **MCP 能力全清单**(15 工具),按 需求/知识 · 进度树 · 项目/结构树 分组,每个写明「做什么 + 何时用」——让 AI 清楚自己有哪些「手」,流程各步按需查这里取准确工具与参数。
- **board-injection.md**:四 category(standard 开工必读全量 / asset 注入清单后按需取详情 / experience 触发式检索 / doc 兜底)+ 注入时机。
- **herness-flow.md**:`create_dev_plan`/`update_dev_plan_node`/`add_dev_plan_nodes` 参数细节、一节点一 commit、`actor` 由认证渠道推断、commit 链接由 repo.url+sha 拼。
- **promotion.md**:晋升 = 去 `tmp` + 设正式 category + 改 path(`/tech-proposals/...` → `/experience/<seg>`);经 `write_knowledge` 按新 path upsert。
- **corrections.md**:每次 `get_requirement_detail` 读 `open_corrections`,据其调整,处理后请人在界面标「已解决」。
- **templates.md**:技术方案模板(引用知识库文档 / 实现方案 / 问题预警);经验沉淀模板(名称 / 类型 / 接口契约 / 用法 / related_code)。

## 手艺(依《Agent skill 迭代式编写实战》)
- **决策树取代模糊判断**:第 4 步(文档先行与否)、第 6 步(done/blocked 分支)写成树,AI 顺树走、不自行推理。
- **负向约束配替代方案**:「❌ 别没验证就报 done → ✅ 先跑编译/测试,结果填进 `verifications`」;「❌ 别一次性灌整树到不可控 → ✅ 按软件模块分解、粒度≈一个 commit」。
- **执行后自查(发散校验)**:从产出多维度回查,补决策树(收敛)的另一半。

## 咬合「管」层(教 ↔ 管对齐)
SKILL.md 的决策树/自查**逐条镜像**平台 `computeWarnings`:done 无 pass 验证、无产物、验收未勾、子节点未完 → 平台软警告;blocked 必填原因 → 平台硬校验。**skill 软教 + 平台硬顶,双保险**——这是本方案区别于纯 skill 的关键(「管」层已建)。

## CLAUDE.md 瘦身
- **保留**:身份 + 一句「做开发任务时遵循/加载 `potato` skill」+ 不可协商底线(`structured` snake_case、前端视图逻辑分离、容器内验证、非交互推送、Co-Authored-By)+ MCP 接入配置。
- **移出**:详细 7 步 SOP / 结构树共建 / 工具清单 → 进 SKILL.md + references(避免两处重复、享渐进式披露)。知识库 `/agent/herness-contract` 改为指向 skill。

## eval(验)
- 选 **1 个真实小任务**(建议「修 `update_dev_plan_node` PATCH 401」或一个小功能),**挂 `potato` skill / 不挂**各跑一遍,对比:是否完整走 Herness 回填、是否注入规范、是否收尾沉淀。断言:进度树有完整 node 流转 + verifications + 一篇沉淀页。
- **description 触发率**:should-trigger(「帮我实现这个需求」)/ should-not-trigger(「解释这段代码」)样本校准,关注误触发/漏触发边界。

## 本期范围 / 非目标
- **做**:`.claude/skills/potato/` 的 SKILL.md(一轮闭环显式九步流程 + 自查)+ 6 个 references(含 mcp-toolbox 工具全清单);CLAUDE.md 瘦身为指针;知识库 `/agent/herness-contract` 指向 skill;一轮 eval 设计。
- **不做(后续)**:`scripts/` 确定性脚本、产品级分发(skills.sh/知识库分发)、需求分级 E1 自适应、把 skill「翻译」成专用 agent。

## 验证
- skill 文件结构完整、`SKILL.md` 决策树与自查可读可走、references 被正确引用(不堆进主文件)。
- CLAUDE.md 瘦身后仍含全部不可协商底线、且正确指向 skill。
- **eval 真跑受 PATCH 401 阻**(见依赖)——先做「文件就绪 + 人工走查决策树」,待 bug 修后补真实 with/without 对比。

## 依赖与风险
- **依赖**:完整 dogfood 第 6 步回填需 **`update_dev_plan_node` PATCH 401 修复**;skill 编写不受阻,但「挂 skill 真跑一遍」的 eval 要等它。
- **风险**:skill 确定性弱于专用 agent(自然语言决策树由模型模拟执行)——靠平台护栏 + 自查兜底;若某流程确定性要求极高,后续可把该段「翻译」成专用 agent。
- **风险**:CLAUDE.md 与 skill 内容若不同步会双源漂移 → 约定 SOP 细节单一事实来源在 skill,CLAUDE.md 只留指针。
