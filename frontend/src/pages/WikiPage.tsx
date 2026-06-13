// 视图层:知识库页(左目录树 + 搜索 + 分类筛选 + 右文档查看/资产/晋升)
import { useState } from 'react'
import { Breadcrumb, Button, Card, Empty, Input, Modal, Popconfirm, Select, Space, Tag, Typography, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useWiki } from '../features/useWiki'
import { moveDir, updateWiki, deleteWiki } from '../api/wiki'
import WikiTree from '../components/WikiTree'
import MarkdownView from '../components/MarkdownView'

const { Title, Text } = Typography
const { Search } = Input

const CATEGORY_OPTIONS = [
  { value: '', label: '全部分类' },
  { value: 'doc', label: 'doc 通用' },
  { value: 'asset', label: 'asset 可复用' },
  { value: 'standard', label: 'standard 规范' },
  { value: 'experience', label: 'experience 经验' },
]

export default function WikiPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { pages, loading, selected, selectedId, setSelectedId, search, category, filterCategory, reload } = useWiki()
  const canEdit = user?.functions.some((f) => f === 'admin' || f === 'product') ?? false
  const [promoting, setPromoting] = useState(false)
  const [promoteCat, setPromoteCat] = useState('experience')
  const [promotePath, setPromotePath] = useState('')
  const [moveTarget, setMoveTarget] = useState<{ isDir: boolean; path: string; id?: string; name: string } | null>(null)
  const [movePath, setMovePath] = useState('')

  const isTmp = selected?.tags?.includes('tmp') ?? false

  const openPromote = () => {
    if (!selected) return
    const seg = selected.path.split('/').filter(Boolean).pop() ?? 'untitled'
    setPromotePath(`/experience/${seg}`)
    setPromoting(true)
  }

  const doPromote = async () => {
    if (!selected) return
    try {
      await updateWiki(selected.id, {
        category: promoteCat,
        path: promotePath || undefined,
        tags: (selected.tags ?? []).filter((t) => t !== 'tmp'),
      })
      message.success('已晋升')
      setPromoting(false)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '晋升失败')
    }
  }

  const doDelete = async () => {
    if (!selected) return
    try {
      await deleteWiki(selected.id)
      message.success('已删除')
      setSelectedId(null)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  const openMove = (node: { isDir: boolean; path: string; id?: string; name: string }) => {
    setMoveTarget(node)
    setMovePath(node.path)
  }

  const doMove = async () => {
    if (!moveTarget || !movePath.trim()) return
    try {
      if (moveTarget.isDir) {
        await moveDir(moveTarget.path, movePath.trim())
      } else if (moveTarget.id) {
        await updateWiki(moveTarget.id, { path: movePath.trim() })
      }
      message.success('已移动')
      setMoveTarget(null)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '移动失败')
    }
  }

  const newInDir = (dirPath: string) => {
    navigate(`/wiki/new?path=${encodeURIComponent(dirPath + '/')}`)
  }

  return (
    <div style={{ display: 'flex', gap: 16, height: 'calc(100vh - 140px)' }}>
      <Card
        size="small"
        title="知识库"
        style={{ width: 280, overflow: 'auto', flexShrink: 0 }}
        extra={canEdit ? <Button size="small" type="primary" onClick={() => navigate('/wiki/new')}>新建</Button> : null}
      >
        <Search placeholder="搜索标题/内容/标签" onSearch={search} allowClear style={{ marginBottom: 8 }} />
        <Select
          value={category ?? ''}
          onChange={(v: string) => filterCategory(v || undefined)}
          options={CATEGORY_OPTIONS}
          style={{ width: '100%', marginBottom: 12 }}
        />
        <WikiTree pages={pages} selectedId={selectedId} onSelect={setSelectedId} canEdit={canEdit} onMoveNode={openMove} onNewInDir={newInDir} />
      </Card>

      <Card style={{ flex: 1, overflow: 'auto' }}>
        {selected ? (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Title level={3} style={{ margin: 0 }}>{selected.title}</Title>
              <Space>
                {canEdit && isTmp && <Button onClick={openPromote}>晋升</Button>}
                {canEdit && <Button onClick={() => navigate(`/wiki/${selected.id}/edit`)}>编辑</Button>}
                {canEdit && (
                  <Popconfirm
                    title="删除此文档?"
                    description="将一并删除其挂载的资产,不可恢复。"
                    okText="删除"
                    okButtonProps={{ danger: true }}
                    cancelText="取消"
                    onConfirm={doDelete}
                  >
                    <Button danger>删除</Button>
                  </Popconfirm>
                )}
              </Space>
            </div>
            <Breadcrumb
              style={{ margin: '8px 0' }}
              items={selected.path.split('/').filter(Boolean).map((seg) => ({ title: seg }))}
            />
            <Space style={{ marginBottom: 8 }} wrap>
              {selected.category && <Tag color="blue">{selected.category}</Tag>}
              {selected.tags.map((t) => <Tag key={t}>{t}</Tag>)}
            </Space>
            {(selected.assets?.length ?? 0) > 0 && (
              <Space style={{ marginBottom: 8 }} wrap>
                <Text type="secondary">资产:</Text>
                {selected.assets!.map((a) => (
                  <a key={a.objectKey} href={a.url} target="_blank" rel="noreferrer">{a.name}</a>
                ))}
              </Space>
            )}
            <MarkdownView content={selected.content} />
          </>
        ) : (
          <Empty description={loading ? '加载中…' : '选择左侧文档查看,或点击「新建」'} />
        )}
      </Card>

      <Modal title="晋升为正式知识" open={promoting} onOk={doPromote} onCancel={() => setPromoting(false)} okText="晋升">
        <p>去掉 <Tag>tmp</Tag> 标签、归入正式分类,并把文档从临时区搬到正式板块路径:</p>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            value={promoteCat}
            onChange={setPromoteCat}
            style={{ width: '100%' }}
            options={[
              { value: 'experience', label: 'experience 先验经验' },
              { value: 'standard', label: 'standard 代码规范' },
              { value: 'asset', label: 'asset 可复用代码' },
              { value: 'doc', label: 'doc 通用文档' },
            ]}
          />
          <Input
            placeholder="目标 path,如 /experience/cache-avalanche"
            value={promotePath}
            onChange={(e) => setPromotePath(e.target.value)}
          />
        </Space>
      </Modal>

      <Modal
        title={moveTarget?.isDir ? '重命名 / 移动目录' : '重命名 / 移动文档'}
        open={!!moveTarget}
        onOk={doMove}
        onCancel={() => setMoveTarget(null)}
        okText="确定"
      >
        <p>
          {moveTarget?.isDir
            ? '输入目录的新路径,其下所有文档会一起移动:'
            : '输入文档的新路径(改末段=重命名,改父目录=移动):'}
        </p>
        <Input value={movePath} onChange={(e) => setMovePath(e.target.value)} placeholder="如 /development/code-style/react" />
      </Modal>
    </div>
  )
}
