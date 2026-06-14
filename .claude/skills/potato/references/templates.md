# 模板

> 执行文档(run log)模板单列在 `references/run-log.md`(贯穿全程边走边记 + 回写)。

## 技术方案(write_tech_proposal 的 content)

> 页 path 由后端自动用可读 title 生成(`/tech-proposals/<title>`,不含编号);**关联的 requirement_id / node_id 写进正文**,别塞进路径。
```
## 关联
(requirement_id / node_id —— 编号写文档内,不入路径)
## 引用的知识库文档
(列出第 3/4/5 步注入/检索到的 standard/asset/experience 页 path)
## 实现方案
(架构决策、关键步骤、涉及模块)
## 问题预警
(已知风险、边界、依赖)
```

## 经验沉淀(write_knowledge category=experience 的 content)
```
## 场景
(什么问题 / 什么时候会用到)
## 方案
(怎么解决,关键步骤)
## 关键坑
(踩过的坑 + 怎么绕)
## 适用边界
(什么情况适用 / 不适用)
```

## 可复用资产页(write_knowledge category=asset 的 content)
```
## 名称 / 类型
## 接口契约
(签名、入参出参)
## 用法
(最小示例)
## related_code
(代码 glob 路径)
```
