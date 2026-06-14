# 执行文档(run log)模板

> LLM 走 potato 闭环时**开工即建**本地文件 `.potato/runs/<可读需求简名>-run<N>.md`(用**可读名**,别用需求编号),按本模板**边执行边逐段追加**(全链路粒度:分流 + 九步 + 每个 dev_plan 节点);**跑完或中断**时 `write_knowledge(category="runlog", path="/runs/<可读需求简名>-run<N>")` 回写平台(path 用**可读名**,需求编号写进文档头部、不入路径)。
>
> 它是过程轨迹(COT 自检 + 给人交代),区别于事前写定改动的「执行计划」。**没把握/没做到的步骤如实写「跳过/豁免 + 原因」,不补全不美化。**

```markdown
# 执行文档 — <需求标题> · run<N>

- requirement_id:<id>(编号写文档内,不入 wiki 路径)
- 开工时间:<time>
- 类型 / tier:<feature|improvement|bugfix> / <Large|Medium|Small>
- 关联仓库 / 分支:<repo> / <branch>
- 状态:进行中 | 已完成 | 中断(<原因>)

## 0. 开工分流
- tier 判定与流程强度:<精简/标准/完整>
- 新建需求 / bug 根因:<结论>

## 1. 弄清需求
- user_stories / 模块 / 验收要点:<摘要>
- 未解决纠偏(open_corrections)及据其调整:<有/无 + 怎么调>

## 2. 弄清代码基础与架构约束
- 摸到的现状 / 既有模式 / related_code:<结论,别脑补>

## 3. 注入代码规范(standard)
- 命中页 path:<…>
- 本轮遵守的约束:<接口契约/数据模型/代码风格>

## 4. 查可复用资产(asset)
- 命中 / 复用决策:<复用了什么,或为何不复用>

## 5. 检索先验经验(experience,拿不准时)
- 命中 / 采用的处理手段:<…,或「无需检索」>

## 6. 实现计划
- 技术方案(复杂时):<wiki path 或要点>
- dev_plan 节点分解:<node 列表 + 粒度说明>

## 7. 逐节点实现
### node_<id> <标题>
- 为什么这么做:<决策依据>
- 改动文件 / commit:<files + sha>
- 验证(编译/测试/lint):<命令 + 结果>
- 验收点勾选:<已满足项;未满足说明>
<!-- 每个叶子节点一段,按上格重复 -->

## 8. warnings 处理
- 平台软警告:<列出>
- 处理 / 豁免说明:<逐条>

## 9. 收尾沉淀 + 回标
- write_knowledge(experience) 沉淀:<path,或「无值得沉淀」+原因>
- relate_requirement_arch 回标:<arch_path + impl_status>

## 自检对照(执行后自查逐条结论)
- 每个 done 节点有 pass 验证:<是/否+说明>
- 验收点全勾 / 未勾已说明:<…>
- 一节点一 commit 已挂:<…>
- 经验已沉淀 / 需求已回标:<…>
- 规范真遵守 / 资产真复用:<…>

## 回写记录
- 回写时机:跑完 | 中断
- 平台 path:/runs/<可读需求简名>-run<N>(category=runlog;需求编号见头部,不入路径)
```
