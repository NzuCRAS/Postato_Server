// 视图层:需求详情 + 状态流转 + 编辑入口 + 开发进度树
import { useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Input, Space, Spin, Tabs, Tag, Typography, message } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { useRequirementDetail } from '../features/useRequirementDetail'
import { useDevPlan } from '../features/useDevPlan'
import { statusColor, statusLabel } from '../features/requirementStatus'
import StructuredView from '../components/StructuredView'
import DevPlanTree from '../components/DevPlanTree'
import NodeDetailDrawer from '../components/NodeDetailDrawer'
import { updateDevPlanNode } from '../api/devplan'
import type { AcceptanceItem, DevPlanNode } from '../types'

const { Title, Paragraph, Text } = Typography

export default function RequirementDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { data, loading, error, reload, changeStatus } = useRequirementDetail(id)
  const dp = useDevPlan(id, reload)
  const [activeTab, setActiveTab] = useState('doc')
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [repoUrl, setRepoUrl] = useState('')

  // 从最新树里按 id 找选中节点(刷新后保持引用最新)
  const findNode = (n: DevPlanNode | undefined, target: string): DevPlanNode | null => {
    if (!n) return null
    if (n.id === target) return n
    for (const c of n.children ?? []) {
      const f = findNode(c, target)
      if (f) return f
    }
    return null
  }
  const selectedNode = useMemo(
    () => (data?.devPlan && selectedNodeId ? findNode(data.devPlan.root, selectedNodeId) : null),
    [data, selectedNodeId],
  )

  // 改状态:blocked 走弹窗收原因,统一带 blocked_reason 调后端。
  // 成功返回 warnings(可能为空),失败/取消返回 undefined(调用方据此不弹"成功")。
  const changeNodeStatus = async (nodeId: string, status: string): Promise<string[] | undefined> => {
    let blocked_reason: string | undefined
    if (status === 'blocked') {
      const r = window.prompt('请填写阻塞原因(blocked 必填):') ?? ''
      if (!r.trim()) {
        message.info('已取消(blocked 需要原因)')
        return undefined
      }
      blocked_reason = r.trim()
    }
    return dp.updateNode(nodeId, { status, blocked_reason, log_message: `手动改为 ${status}` })
  }

  const toggleAcceptance = async (nodeId: string, items: AcceptanceItem[]): Promise<void> => {
    try {
      await updateDevPlanNode(id, nodeId, { acceptance_criteria: items })
      await reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '更新验收失败')
    }
  }

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} />
  if (error) return <Alert type="error" message={error} />
  if (!data) return null

  const onChangeStatus = async (status: string) => {
    try {
      await changeStatus(status)
      message.success('状态已更新')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '更新失败')
    }
  }

  const statusActions = () => {
    switch (data.status) {
      case 'draft':
        return <Button type="primary" onClick={() => onChangeStatus('clarifying')}>提交开始澄清</Button>
      case 'clarifying':
        return (
          <Space>
            <Button type="primary" onClick={() => onChangeStatus('confirmed')}>标记已确认</Button>
            <Button onClick={() => onChangeStatus('draft')}>退回草稿</Button>
          </Space>
        )
      case 'confirmed':
        return <Button onClick={() => onChangeStatus('clarifying')}>重新澄清</Button>
      default:
        return null
    }
  }

  const devPlanTab = data.devPlan ? (
    <>
      <DevPlanTree plan={data.devPlan} onSelectNode={setSelectedNodeId} />
      <NodeDetailDrawer
        node={selectedNode}
        repo={data.devPlan.repo}
        open={!!selectedNode}
        onClose={() => setSelectedNodeId(null)}
        onChangeStatus={changeNodeStatus}
        onToggleAcceptance={toggleAcceptance}
        onLeaveCorrection={dp.leaveCorrection}
        onResolveCorrection={dp.markCorrectionResolved}
      />
    </>
  ) : (
    <Space direction="vertical" style={{ width: '100%' }} align="center">
      <Empty description="还没有开发计划" />
      <Input
        style={{ maxWidth: 420 }}
        placeholder="(可选)GitHub 仓库地址,如 https://github.com/org/repo"
        value={repoUrl}
        onChange={(e) => setRepoUrl(e.target.value)}
      />
      <Button
        type="primary"
        loading={dp.busy}
        onClick={async () => {
          await dp.generateFromModules(
            data.structured.modules ?? [],
            repoUrl.trim() ? { url: repoUrl.trim(), provider: 'github', default_branch: 'main' } : undefined,
          )
          message.success('已生成开发计划')
        }}
      >
        基于模块生成开发计划
      </Button>
      <Typography.Text type="secondary">(根据「需求文档」里的模块生成初始树,并继承验收标准)</Typography.Text>
    </Space>
  )

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Space wrap>
          <Title level={3} style={{ margin: 0 }}>{data.title}</Title>
          <Tag color={statusColor(data.status)}>{statusLabel(data.status)}</Tag>
          <Tag>v{data.version}</Tag>
          <Text copyable={{ text: data.id }} type="secondary" style={{ fontSize: 12 }}>ID: {data.id}</Text>
        </Space>
        <Space>
          <Button onClick={() => navigate(`/requirements/${id}/edit`)}>编辑</Button>
          {statusActions()}
        </Space>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'doc',
            label: '需求文档',
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size="large">
                {data.descriptionMd && (
                  <Card size="small" title="描述">
                    <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{data.descriptionMd}</Paragraph>
                  </Card>
                )}
                <StructuredView structured={data.structured} />
              </Space>
            ),
          },
          {
            key: 'devplan',
            label: '开发进度',
            children: devPlanTab,
          },
        ]}
      />
    </Space>
  )
}
