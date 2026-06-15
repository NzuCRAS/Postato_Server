// 视图层:用户管理页 —— 人人可见。admin 看全部+全操作;非 admin 只看自己+自助改密。
import { useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useAuth } from '../auth/AuthContext'
import { useUsers } from '../features/useUsers'
import { changeMyPassword } from '../api/user'
import type { UserAdminItem } from '../types'

const { Title } = Typography

/** 生成易读的随机密码(去掉易混字符)。 */
function genPassword(len = 14): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%'
  const arr = new Uint32Array(len)
  crypto.getRandomValues(arr)
  return Array.from(arr, (x) => chars[x % chars.length]).join('')
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch {
    message.warning('复制失败,请手动选择')
  }
}

export default function UsersPage() {
  const { user } = useAuth()
  const isAdmin = user?.functions.includes('admin') ?? false
  const { users, loading, functions, reload, createUser, updateUserFunctions, resetUserPassword, deleteUser } = useUsers()

  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm()
  const [fnTarget, setFnTarget] = useState<UserAdminItem | null>(null)
  const [fnValue, setFnValue] = useState<string[]>([])
  const [pwTarget, setPwTarget] = useState<UserAdminItem | null>(null) // admin 重置他人
  const [pwValue, setPwValue] = useState('')
  const [selfPwOpen, setSelfPwOpen] = useState(false) // 自助改密
  const [selfForm] = Form.useForm()

  const fnOptions = functions.map((f) => ({ value: f.key, label: `${f.label} (${f.key})` }))

  const doCreate = async () => {
    try {
      const v = await createForm.validateFields()
      await createUser({ username: v.username, password: v.password, functions: v.functions ?? [] })
      message.success('已创建用户')
      setCreateOpen(false)
      createForm.resetFields()
      reload()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    }
  }

  const doUpdateFunctions = async () => {
    if (!fnTarget) return
    try {
      await updateUserFunctions(fnTarget.id, fnValue)
      message.success('已更新职能')
      setFnTarget(null)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '更新失败')
    }
  }

  const doResetPassword = async () => {
    if (!pwTarget || !pwValue.trim()) {
      message.error('请输入新密码')
      return
    }
    try {
      await resetUserPassword(pwTarget.id, pwValue)
      message.success('已重置密码')
      setPwTarget(null)
      setPwValue('')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '重置失败')
    }
  }

  const doSelfPassword = async () => {
    try {
      const v = await selfForm.validateFields()
      await changeMyPassword(v.oldPassword, v.newPassword)
      message.success('密码已修改')
      setSelfPwOpen(false)
      selfForm.resetFields()
    } catch (e) {
      if (e instanceof Error) message.error(e.message)
    }
  }

  const doDelete = async (id: string) => {
    try {
      await deleteUser(id)
      message.success('已删除用户')
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  const columns: ColumnsType<UserAdminItem> = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    {
      title: '职能',
      dataIndex: 'functions',
      key: 'functions',
      render: (fns: string[]) => (fns?.length ? fns.map((f) => <Tag key={f} color={f === 'admin' ? 'red' : 'blue'}>{f}</Tag>) : <Tag>无</Tag>),
    },
    { title: 'API Key', dataIndex: 'apiKeyCount', key: 'apiKeyCount', width: 90 },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_, r) => {
        if (r.id === user?.id) {
          // 自己那行:自助改密
          return <Button size="small" onClick={() => { selfForm.resetFields(); setSelfPwOpen(true) }}>修改密码</Button>
        }
        if (!isAdmin) return null
        // admin 操作他人
        return (
          <Space>
            <Button size="small" onClick={() => { setFnTarget(r); setFnValue(r.functions ?? []) }}>改职能</Button>
            <Button size="small" onClick={() => { setPwTarget(r); setPwValue('') }}>重置密码</Button>
            <Popconfirm title="删除此用户?" description="不可恢复。" okText="删除" okButtonProps={{ danger: true }} cancelText="取消" onConfirm={() => doDelete(r.id)}>
              <Button size="small" danger>删除</Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  return (
    <Card
      title={<Title level={4} style={{ margin: 0 }}>用户管理</Title>}
      extra={isAdmin ? <Button type="primary" onClick={() => { createForm.resetFields(); setCreateOpen(true) }}>新建用户</Button> : null}
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={users} pagination={false} size="middle" />

      <Modal title="新建用户" open={createOpen} onOk={doCreate} onCancel={() => setCreateOpen(false)} okText="创建">
        <Form form={createForm} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="登录用户名" />
          </Form.Item>
          <Form.Item name="password" label="初始密码" rules={[{ required: true, message: '必填' }]}>
            <Input.Password placeholder="可用下方按钮生成随机密码" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button onClick={() => createForm.setFieldsValue({ password: genPassword() })}>生成随机密码</Button>
              <Button onClick={() => { const p = createForm.getFieldValue('password'); if (p) copy(p); else message.info('请先生成或输入密码') }}>复制</Button>
            </Space>
          </Form.Item>
          <Form.Item name="functions" label="职能(取自职能字典)">
            <Select mode="multiple" options={fnOptions} placeholder="选择职能(如需新增请去权限管理)" showSearch optionFilterProp="label" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`改职能 — ${fnTarget?.username ?? ''}`} open={!!fnTarget} onOk={doUpdateFunctions} onCancel={() => setFnTarget(null)} okText="保存">
        <Select mode="multiple" style={{ width: '100%' }} options={fnOptions} value={fnValue} onChange={setFnValue} placeholder="选择职能(取自职能字典)" showSearch optionFilterProp="label" />
      </Modal>

      <Modal title={`重置密码 — ${pwTarget?.username ?? ''}`} open={!!pwTarget} onOk={doResetPassword} onCancel={() => { setPwTarget(null); setPwValue('') }} okText="重置">
        <Space.Compact style={{ width: '100%' }}>
          <Input.Password value={pwValue} onChange={(e) => setPwValue(e.target.value)} placeholder="新密码" />
          <Button onClick={() => setPwValue(genPassword())}>生成</Button>
          <Button onClick={() => { if (pwValue) copy(pwValue) }}>复制</Button>
        </Space.Compact>
      </Modal>

      <Modal title="修改密码" open={selfPwOpen} onOk={doSelfPassword} onCancel={() => setSelfPwOpen(false)} okText="确定">
        <Form form={selfForm} layout="vertical">
          <Form.Item name="oldPassword" label="旧密码" rules={[{ required: true, message: '必填' }]}>
            <Input.Password placeholder="当前密码" />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '必填' }]}>
            <Input.Password placeholder="新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
