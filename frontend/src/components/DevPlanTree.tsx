// 视图层:开发进度树。顶部总览条(状态计数/阻塞高亮/仓库) + antd Tree(点节点回调选中)。
import { Card, Space, Tag, Tree, Typography } from 'antd'
import type { DataNode } from 'antd/es/tree'
import type { DevPlan, DevPlanNode } from '../types'

const STATUS_COLOR: Record<string, string> = {
  todo: 'default',
  in_progress: 'processing',
  done: 'success',
  blocked: 'error',
}
const STATUS_LABEL: Record<string, string> = {
  todo: '待办',
  in_progress: '进行中',
  done: '完成',
  blocked: '阻塞',
}

function collectStats(node: DevPlanNode, acc: Record<string, number>, leaves: { done: number; total: number }) {
  acc[node.status] = (acc[node.status] ?? 0) + 1
  const isLeaf = !node.children?.length
  if (isLeaf) {
    leaves.total += 1
    if (node.status === 'done') leaves.done += 1
  }
  node.children?.forEach((c) => collectStats(c, acc, leaves))
}

function nodeTitle(node: DevPlanNode) {
  const hasCommit = node.log?.some((e) => e.commit)
  const openCorrections = (node.corrections ?? []).filter((c) => !c.resolved).length
  return (
    <Space size={6}>
      <Tag color={STATUS_COLOR[node.status] ?? 'default'}>{STATUS_LABEL[node.status] ?? node.status}</Tag>
      <Typography.Text strong>{node.title}</Typography.Text>
      {hasCommit && <Tag color="blue">commit</Tag>}
      {node.artifacts?.pr_number != null && <Tag color="geekblue">PR #{node.artifacts.pr_number}</Tag>}
      {openCorrections > 0 && <Tag color="warning">纠偏 {openCorrections}</Tag>}
    </Space>
  )
}

function toTreeData(node: DevPlanNode): DataNode {
  return {
    key: node.id,
    title: nodeTitle(node),
    children: node.children?.length ? node.children.map(toTreeData) : undefined,
  }
}

export default function DevPlanTree({
  plan,
  onSelectNode,
}: {
  plan: DevPlan
  onSelectNode: (nodeId: string) => void
}) {
  const counts: Record<string, number> = {}
  const leaves = { done: 0, total: 0 }
  collectStats(plan.root, counts, leaves)
  const pct = leaves.total ? Math.round((leaves.done / leaves.total) * 100) : 0

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card size="small">
        <Space wrap split="·">
          <span>进度 {pct}%(叶子 {leaves.done}/{leaves.total})</span>
          <Space size={4}>
            <Tag>{STATUS_LABEL.todo} {counts.todo ?? 0}</Tag>
            <Tag color="processing">{STATUS_LABEL.in_progress} {counts.in_progress ?? 0}</Tag>
            <Tag color="success">{STATUS_LABEL.done} {counts.done ?? 0}</Tag>
            <Tag color="error">{STATUS_LABEL.blocked} {counts.blocked ?? 0}</Tag>
          </Space>
          {plan.repo?.url && (
            <Typography.Link href={plan.repo.url} target="_blank">🔗 {plan.repo.url.replace(/^https?:\/\//, '')}</Typography.Link>
          )}
        </Space>
      </Card>
      <Card size="small">
        <Tree
          treeData={[toTreeData(plan.root)]}
          defaultExpandAll
          selectable
          onSelect={(keys) => keys[0] && onSelectNode(String(keys[0]))}
        />
      </Card>
    </Space>
  )
}
