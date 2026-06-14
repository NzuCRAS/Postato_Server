// 视图层:权限规则页(admin)——资源×动作 规则可视化 + 在线编辑/增删
import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useAuth } from '../auth/AuthContext'
import { usePermissions } from '../features/usePermissions'
import type { PermissionRuleItem } from '../types'

const { Title, Text } = Typography

export default function PermissionsPage() {
  const { user } = useAuth()
  const isAdmin = user?.functions.includes('admin') ?? false
  const { rules, loading, knownFunctions, reload, createRule, updateRule, deleteRule } = usePermissions()

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm()
  const [editTarget, setEditTarget] = useState<PermissionRuleItem | null>(null)
  const [editValue, setEditValue] = useState<string[]>([])

  const fnOptions = knownFunctions.map((f) => ({ value: f, label: f }))

  const doCreate = async () => {
    try {
      const v = await createForm.validateFields()
      await createRule({ resource: v.resource, action: v.action, requiredFunctions: v.requiredFunctions ?? [] })
      message.success('已新增规则')
      setCreateOpen(false)
      createForm.resetFields()
      reload()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    }
  }

  const doUpdate = async () => {
    if (!editTarget) return
    try {
      await updateRule(editTarget.id, editValue)
      message.success('已更新')
      setEditTarget(null)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '更新失败')
    }
  }

  const doDelete = async (id: string) => {
    try {
      await deleteRule(id)
      message.success('已删除规则')
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  const columns: ColumnsType<PermissionRuleItem> = [
    {
      title: '资源 (resource)',
      dataIndex: 'resource',
      key: 'resource',
      render: (v: string) => <Text strong>{v}</Text>,
      onCell: (row, index) => {
        // 同 resource 合并单元格,呈现分组矩阵感
        const arr = rules
        const i = index ?? 0
        if (i > 0 && arr[i - 1].resource === row.resource) return { rowSpan: 0 }
        let span = 1
        for (let j = i + 1; j < arr.length && arr[j].resource === row.resource; j++) span++
        return { rowSpan: span }
      },
    },
    { title: '动作 (action)', dataIndex: 'action', key: 'action' },
    {
      title: '所需职能 (任一即通过)',
      dataIndex: 'requiredFunctions',
      key: 'requiredFunctions',
      render: (fns: string[]) =>
        fns?.length ? fns.map((f) => <Tag key={f} color={f === 'admin' ? 'red' : 'blue'}>{f}</Tag>) : <Tag>无(默认拒绝)</Tag>,
    },
    {
      title: '操作',
      key: 'op',
      width: 160,
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => { setEditTarget(r); setEditValue(r.requiredFunctions ?? []) }}>改职能</Button>
          <Popconfirm title="删除此规则?" description="删除后该动作将默认拒绝(admin 仍豁免)。" okText="删除" okButtonProps={{ danger: true }} cancelText="取消" onConfirm={() => doDelete(r.id)}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  if (!isAdmin) {
    return <Alert type="warning" showIcon message="无权访问" description="权限规则管理仅管理员(admin)可用。" />
  }

  return (
    <Card
      title={<Title level={4} style={{ margin: 0 }}>权限规则</Title>}
      extra={<Button type="primary" onClick={() => { createForm.resetFields(); setCreateOpen(true) }}>新增规则</Button>}
    >
      <Alert
        style={{ marginBottom: 12 }}
        type="info"
        showIcon
        message="判定:admin 职能豁免一切;否则按 (资源,动作) 取所需职能,用户职能与之有交集即通过;无规则默认拒绝。"
      />
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rules} pagination={false} size="middle" bordered />

      <Modal title="新增规则" open={createOpen} onOk={doCreate} onCancel={() => setCreateOpen(false)} okText="创建">
        <Form form={createForm} layout="vertical">
          <Form.Item name="resource" label="资源 (resource)" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="如 report、user" />
          </Form.Item>
          <Form.Item name="action" label="动作 (action)" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="如 view、create" />
          </Form.Item>
          <Form.Item name="requiredFunctions" label="所需职能(可输入新职能)">
            <Select mode="tags" options={fnOptions} placeholder="如 product、development" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`改职能 — ${editTarget?.resource ?? ''}/${editTarget?.action ?? ''}`} open={!!editTarget} onOk={doUpdate} onCancel={() => setEditTarget(null)} okText="保存">
        <Select mode="tags" style={{ width: '100%' }} options={fnOptions} value={editValue} onChange={setEditValue} placeholder="所需职能(可输入新职能)" />
      </Modal>
    </Card>
  )
}
