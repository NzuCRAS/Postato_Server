# Herness 进度树操作细节

## 建树 `create_dev_plan`
- `nodes`:按软件模块分解,每个 `{title, description?, module_ref?(关联需求模块名,继承其验收), acceptance_criteria?[文本], related_docs?[wiki path], children?}`。
- `repo`:`{url, provider?, default_branch?}`,commit 链接由 `repo.url + sha` 拼。
- 已存在计划会 409 → 改用 `add_dev_plan_nodes` 增量加。

## 推进节点 `update_dev_plan_node`
- 开工:`status="in_progress"`,`log_detail` 写**为什么这么做**(决策依据)。
- done:同时带
  - `commit={sha, message?, files?}`(url 可省,平台拼);
  - `verifications=[{kind: compile|typecheck|test|lint|manual|e2e, command, result: pass|fail, summary, covers?}]` —— **done 前必跑、必报**,否则软警告;
  - `acceptance_criteria=[{text, checked}]` —— 取当前列表,把满足的 `checked` 改 true 再整列表回传。
- blocked:`status="blocked"` + `blocked_reason`(硬校验,必填)。
- **一节点 ≈ 一 commit**;后续修 bug 再记一条带 commit 的日志。

## actor
平台按认证渠道推断:API Key(MCP)→ `ai`;JWT(Web)→ `human`。无需手动传。

## 软警告(平台会顶回,对应 SKILL.md 第 8 步)
done 时若:无 commit/产物、无 pass 验证、验收未勾、子节点未完 → 返回 `warnings`。必须补齐或 `log_detail` 说明豁免。
