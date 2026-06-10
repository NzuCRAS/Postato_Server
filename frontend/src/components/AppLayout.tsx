// 视图层:主框架布局(侧边菜单 + 顶栏 + 内容区)。
import { Button, Layout, Menu, Typography } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const { Header, Sider, Content } = Layout
const { Text } = Typography

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  // 详情页 /requirements/xxx 也高亮“需求”菜单
  const selectedKey = location.pathname.startsWith('/requirements')
    ? '/requirements'
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
            { key: '/requirements', label: '需求' },
            { key: '/wiki', label: '知识库' },
            { key: '/settings', label: '设置 / API Key' },
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 12 }}>
          <Text>{user?.username}</Text>
          <Button onClick={logout}>登出</Button>
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
