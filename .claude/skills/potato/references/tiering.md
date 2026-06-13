# 需求分级与流程分流(`tier` 仅供参考)

需求有两个分类元数据(`create_requirement` 时标注):
- **`type`(描述用)**:`feature` 增量 / `improvement` 修改优化 / `bugfix` 维护与 bug 修复。
- **`tier`(复杂度,创建时选,仅供参考)**:`Large` / `Medium` / `Small` —— **建议**流程强度,**非硬门**,AI 可按实际判断调整。

## tier → 流程强度
- **Small**:小 bug / 小优化。免技术方案;可不建多节点树(单节点或直接在需求上推进);改完本地验证 + 回填即可。
- **Medium**:常规。走完整九步;技术方案视复杂度。
- **Large**:复杂功能 / 大改。技术方案先行(`write_tech_proposal`);细拆多节点 dev-plan;逐节点推进,必要时多轮。

## 特殊入口
- **无现成需求**(ad-hoc / 临时 bug):先 `create_requirement`(标 `type`/`tier`),再按 tier 走流程。
- **bug 类**(`type=bugfix`):实现前先 root-cause —— 稳定复现、拿运行时证据、**禁止没定位就改**。
  - 教训:被保护端点的真错可能被掩盖成别的状态码(别被带偏),见知识库 `/experience/spring-security-error-masking`。
