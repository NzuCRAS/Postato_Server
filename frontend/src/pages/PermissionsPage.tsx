// 视图层:权限管理中心(admin)——Tabs 四子页:职能 / 资源 / 动作 / 规则
import { Alert, Card, Spin, Tabs, Typography } from 'antd'
import { useAuth } from '../auth/AuthContext'
import { usePermissionCenter } from '../features/usePermissionCenter'
import DictPanel from '../components/permission/DictPanel'
import RulesPanel from '../components/permission/RulesPanel'

const { Title } = Typography

export default function PermissionsPage() {
  const { user } = useAuth()
  const isAdmin = user?.functions.includes('admin') ?? false
  const { rules, functions, resources, actions, loading, reload } = usePermissionCenter()

  if (!isAdmin) {
    return <Alert type="warning" showIcon message="无权访问" description="权限管理仅管理员(admin)可用。" />
  }

  const items = [
    {
      key: 'rules',
      label: '规则管理',
      children: <RulesPanel rules={rules} functions={functions} resources={resources} actions={actions} loading={loading} onChanged={reload} />,
    },
    {
      key: 'functions',
      label: '职能管理',
      children: <DictPanel type="functions" noun="职能" defs={functions} loading={loading} onChanged={reload} />,
    },
    {
      key: 'resources',
      label: '资源管理',
      children: <DictPanel type="resources" noun="资源" defs={resources} loading={loading} onChanged={reload} />,
    },
    {
      key: 'actions',
      label: '动作管理',
      children: <DictPanel type="actions" noun="动作" defs={actions} loading={loading} onChanged={reload} />,
    },
  ]

  return (
    <Card title={<Title level={4} style={{ margin: 0 }}>权限管理</Title>}>
      {loading && rules.length === 0 ? <Spin style={{ display: 'block', marginTop: 60 }} /> : <Tabs defaultActiveKey="rules" items={items} />}
    </Card>
  )
}
