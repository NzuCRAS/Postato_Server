// 视图层:主框架布局(侧边菜单 + 顶栏[项目选择器] + 内容区)。
import { Button, Layout, Menu, Select, Space, Typography } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useProjects } from '../features/ProjectContext'

const { Header, Sider, Content } = Layout
const { Text } = Typography

export default function AppLayout() {
  const { user, logout } = useAuth()
  const { projects, currentId, setCurrentId } = useProjects()
  const navigate = useNavigate()
  const location = useLocation()
  const isAdmin = user?.functions.includes('admin') ?? false

  const selectedKey = location.pathname.startsWith('/requirements')
    ? '/requirements'
    : location.pathname.startsWith('/projects')
      ? '/projects'
      : location.pathname

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth="0">
        <div style={{ color: '#fff', padding: 16, fontSize: 18, fontWeight: 'bold' }}>🥔 Potato</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          onClick={(e) => navigate(e.key)}
          items={[
            { key: '/projects', label: '项目' },
            { key: '/requirements', label: '需求' },
            { key: '/wiki', label: '知识库' },
            { key: '/assets', label: '资产库' },
            { key: '/users', label: '用户管理' },
            ...(isAdmin ? [{ key: '/permissions', label: '权限管理' }] : []),
            { key: '/settings', label: '设置 / API Key' },
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
          <Space>
            <Text type="secondary">当前项目</Text>
            <Select
              style={{ minWidth: 200 }}
              value={currentId ?? undefined}
              placeholder="选择项目"
              onChange={(v) => setCurrentId(v)}
              options={projects.map((p) => ({ value: p.id, label: p.name }))}
            />
          </Space>
          <Space>
            <Text>{user?.username}</Text>
            <Button onClick={logout}>登出</Button>
          </Space>
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
