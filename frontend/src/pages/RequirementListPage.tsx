// 视图层:需求列表
import { Alert, Button, Space, Table, Tag, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useRequirements } from '../features/useRequirements'
import { statusColor, statusLabel } from '../features/requirementStatus'
import type { RequirementSummary } from '../types'

const { Title } = Typography

export default function RequirementListPage() {
  const navigate = useNavigate()
  const { items, loading, error } = useRequirements()

  const columns = [
    { title: '标题', dataIndex: 'title', key: 'title' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (s: string) => <Tag color={statusColor(s)}>{statusLabel(s)}</Tag>,
    },
    { title: '版本', dataIndex: 'version', key: 'version', width: 80 },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 200,
      render: (t: string) => new Date(t).toLocaleString(),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: unknown, r: RequirementSummary) => (
        <Button type="link" onClick={(e) => { e.stopPropagation(); navigate(`/requirements/${r.id}`) }}>
          查看
        </Button>
      ),
    },
  ]

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={3} style={{ margin: 0 }}>需求</Title>
        <Button type="primary" onClick={() => navigate('/requirements/new')}>+ 新建需求</Button>
      </div>
      {error && <Alert type="error" message={error} />}
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={items}
        onRow={(r) => ({
          onClick: () => navigate(`/requirements/${r.id}`),
          style: { cursor: 'pointer' },
        })}
      />
    </Space>
  )
}
