// 视图层:知识库页(左目录树 + 搜索 + 右文档查看;编辑/新建跳转到独立编辑页)
import { Button, Card, Empty, Input, Space, Tag, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useWiki } from '../features/useWiki'
import WikiTree from '../components/WikiTree'
import MarkdownView from '../components/MarkdownView'

const { Title, Text } = Typography
const { Search } = Input

export default function WikiPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { pages, loading, selected, selectedId, setSelectedId, search } = useWiki()
  const canEdit = user?.functions.some((f) => f === 'admin' || f === 'product') ?? false

  return (
    <div style={{ display: 'flex', gap: 16, height: 'calc(100vh - 140px)' }}>
      <Card
        size="small"
        title="知识库"
        style={{ width: 280, overflow: 'auto', flexShrink: 0 }}
        extra={canEdit ? <Button size="small" type="primary" onClick={() => navigate('/wiki/new')}>新建</Button> : null}
      >
        <Search placeholder="搜索标题/内容/标签" onSearch={search} allowClear style={{ marginBottom: 12 }} />
        <WikiTree pages={pages} selectedId={selectedId} onSelect={setSelectedId} />
      </Card>

      <Card style={{ flex: 1, overflow: 'auto' }}>
        {selected ? (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Title level={3} style={{ margin: 0 }}>{selected.title}</Title>
              {canEdit && <Button onClick={() => navigate(`/wiki/${selected.id}/edit`)}>编辑</Button>}
            </div>
            <Space style={{ margin: '8px 0' }} wrap>
              <Text type="secondary" code>{selected.path}</Text>
              {selected.tags.map((t) => <Tag key={t}>{t}</Tag>)}
            </Space>
            <MarkdownView content={selected.content} />
          </>
        ) : (
          <Empty description={loading ? '加载中…' : '选择左侧文档查看,或点击「新建」'} />
        )}
      </Card>
    </div>
  )
}
