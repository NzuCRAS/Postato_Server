// 视图层:权限字典通用子页(职能/资源/动作 三类复用)——表格 + 新增/编辑/删除
import { useState } from 'react'
import { Button, Form, Input, Modal, Popconfirm, Space, Table, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { createDef, updateDef, deleteDef } from '../../api/permission'
import type { DictType } from '../../api/permission'
import type { PermissionDefItem } from '../../types'

const { Text } = Typography

interface Props {
  type: DictType
  noun: string // 「职能」「资源」「动作」
  defs: PermissionDefItem[]
  loading: boolean
  onChanged: () => void
}

export default function DictPanel({ type, noun, defs, loading, onChanged }: Props) {
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm()
  const [editTarget, setEditTarget] = useState<PermissionDefItem | null>(null)
  const [editForm] = Form.useForm()

  const doCreate = async () => {
    try {
      const v = await createForm.validateFields()
      await createDef(type, { key: v.key, label: v.label, description: v.description })
      message.success(`已新增${noun}`)
      setCreateOpen(false)
      createForm.resetFields()
      onChanged()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    }
  }

  const openEdit = (d: PermissionDefItem) => {
    setEditTarget(d)
    editForm.setFieldsValue({ label: d.label, description: d.description })
  }

  const doEdit = async () => {
    if (!editTarget) return
    try {
      const v = await editForm.validateFields()
      await updateDef(type, editTarget.key, { label: v.label, description: v.description })
      message.success('已更新')
      setEditTarget(null)
      onChanged()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    }
  }

  const doDelete = async (key: string) => {
    try {
      await deleteDef(type, key)
      message.success('已删除')
      onChanged()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败(可能被引用)')
    }
  }

  const columns: ColumnsType<PermissionDefItem> = [
    { title: 'key', dataIndex: 'key', key: 'key', render: (v: string) => <Text code>{v}</Text> },
    { title: '名称', dataIndex: 'label', key: 'label' },
    { title: '说明', dataIndex: 'description', key: 'description', render: (v?: string) => v ?? <Text type="secondary">—</Text> },
    {
      title: '操作',
      key: 'op',
      width: 150,
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => openEdit(r)}>编辑</Button>
          <Popconfirm title={`删除此${noun}?`} description="被规则或用户引用时会被拒绝。" okText="删除" okButtonProps={{ danger: true }} cancelText="取消" onConfirm={() => doDelete(r.key)}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 12, textAlign: 'right' }}>
        <Button type="primary" onClick={() => { createForm.resetFields(); setCreateOpen(true) }}>新增{noun}</Button>
      </div>
      <Table rowKey="key" loading={loading} columns={columns} dataSource={defs} pagination={false} size="middle" />

      <Modal title={`新增${noun}`} open={createOpen} onOk={doCreate} onCancel={() => setCreateOpen(false)} okText="创建">
        <Form form={createForm} layout="vertical">
          <Form.Item name="key" label="key(受控标识,不可改)" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="如 reviewer、report" />
          </Form.Item>
          <Form.Item name="label" label="名称"><Input placeholder="可读名" /></Form.Item>
          <Form.Item name="description" label="说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>

      <Modal title={`编辑${noun} — ${editTarget?.key ?? ''}`} open={!!editTarget} onOk={doEdit} onCancel={() => setEditTarget(null)} okText="保存">
        <Form form={editForm} layout="vertical">
          <Form.Item name="label" label="名称"><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
    </>
  )
}
