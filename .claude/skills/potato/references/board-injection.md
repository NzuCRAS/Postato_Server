# 知识库板块(category)注入约定

知识库按 `category` 分四板块,各有不同消费方式:

| category | 装什么 | 何时、怎么用 |
|----------|--------|--------------|
| `standard` | 代码规范(接口契约 / 数据模型 / 代码风格) | **第 3 步开工必读**:`search_knowledge(category="standard")`,全量注入,编码全程遵守 |
| `asset` | 可复用代码 / 组件(可挂 demo/资产 URL) | **第 4 步**:`search_knowledge(category="asset")` 先取清单,命中需要的再取详情,先复用不重造 |
| `experience` | 先验经验(场景处理手段、bug 解法) | **第 5 步触发式**:拿不准时 `search_knowledge(category="experience", q="具体问题")` |
| `doc` | 通用说明(`/agent/*` 协议、架构说明) | 兜底,按需查 |

约定:
- `tmp` 是正交于 category 的 tag,标「未晋升草稿」(开发伴生的技术方案),检索默认排除;转正见 `promotion.md`。
- 写沉淀时务必带对 `category`,否则将来按板块检索会漏。
