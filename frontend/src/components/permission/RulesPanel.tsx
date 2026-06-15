// 视图层:权限规则子页——资源×动作→职能 矩阵 + 在线编排(资源/动作/职能均从字典下拉)
import { useState } from 'react'
import { Alert, Button, Form, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { createRule, updateRule, deleteRule } from '../../api/permission'
import type { PermissionRuleItem, PermissionDefItem } from '../../types'

const { Text } = Typography

interface Props {
  rules: PermissionRuleItem[]
  functions: PermissionDefItem[]
  resources: PermissionDefItem[]
  actions: PermissionDefItem[]
  loading: boolean
  onChanged: () => void
}

const opt = (d: PermissionDefItem) => ({ value: d.key, label: `${d.label} (${d.key})` })

export default function RulesPanel({ rules, functions, resources, actions, loading, onChanged }: Props) {
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm()
  const [editTarget, setEditTarget] = useState<PermissionRuleItem | null>(null)
  const [editValue, setEditValue] = useState<string[]>([])

  const fnOptions = functions.map(opt)

  const doCreate = async () => {
    try {
      const v = await createForm.validateFields()
      await createRule({ resource: v.resource, action: v.action, requiredFunctions: v.requiredFunctions ?? [] })
      message.success('已新增规则')
      setCreateOpen(false)
      createForm.resetFields()
      onChanged()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    }
  }

  const doEdit = async () => {
    if (!editTarget) return
    try {
      await updateRule(editTarget.id, editValue)
      message.success('已更新')
      setEditTarget(null)
      onChanged()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '更新失败')
    }
  }

  const doDelete = async (id: string) => {
    try {
      await deleteRule(id)
      message.success('已删除规则')
      onChanged()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  const columns: ColumnsType<PermissionRuleItem> = [
    {
      title: '资源',
      dataIndex: 'resource',
      key: 'resource',
      render: (v: string) => <Text strong>{v}</Text>,
      onCell: (_, index) => {
        const i = index ?? 0
        if (i > 0 && rules[i - 1].resource === rules[i].resource) return { rowSpan: 0 }
        let span = 1
        for (let j = i + 1; j < rules.length && rules[j].resource === rules[i].resource; j++) span++
        return { rowSpan: span }
      },
    },
    { title: '动作', dataIndex: 'action', key: 'action' },
    {
      title: '所需职能(任一即通过)',
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

  return (
    <>
      <Alert
        style={{ marginBottom: 12 }}
        type="info"
        showIcon
        message="判定:admin 豁免一切;否则按 (资源,动作) 取所需职能,用户职能与之有交集即通过;无规则默认拒绝。资源/动作/职能均须先在对应字典注册。"
      />
      <div style={{ marginBottom: 12, textAlign: 'right' }}>
        <Button type="primary" onClick={() => { createForm.resetFields(); setCreateOpen(true) }}>新增规则</Button>
      </div>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rules} pagination={false} size="middle" bordered />

      <Modal title="新增规则" open={createOpen} onOk={doCreate} onCancel={() => setCreateOpen(false)} okText="创建">
        <Form form={createForm} layout="vertical">
          <Form.Item name="resource" label="资源" rules={[{ required: true, message: '必选' }]}>
            <Select options={resources.map(opt)} placeholder="选择已注册资源" showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="action" label="动作" rules={[{ required: true, message: '必选' }]}>
            <Select options={actions.map(opt)} placeholder="选择已注册动作" showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="requiredFunctions" label="所需职能">
            <Select mode="multiple" options={fnOptions} placeholder="选择允许的职能" showSearch optionFilterProp="label" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`改职能 — ${editTarget?.resource ?? ''}/${editTarget?.action ?? ''}`} open={!!editTarget} onOk={doEdit} onCancel={() => setEditTarget(null)} okText="保存">
        <Select mode="multiple" style={{ width: '100%' }} options={fnOptions} value={editValue} onChange={setEditValue} placeholder="选择允许的职能" showSearch optionFilterProp="label" />
      </Modal>
    </>
  )
}
