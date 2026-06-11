// 视图层:项目结构树(L0-L4 业务域)。可视化层级树(配色卡片 + 连接线)+ 节点详情 + 新建/归档 + 标签跨切面过滤。
import { useEffect, useState } from 'react'
import { Button, Card, Drawer, Empty, Form, Input, Select, Space, Tag, Typography, message } from 'antd'
import { archiveArchNode, createArchNode, listArch } from '../api/archNode'
import type { ArchNode } from '../types'

const { Text, Link, Paragraph } = Typography
const LAYERS = ['L0', 'L1', 'L2', 'L3', 'L4']

// 各层配色(卡片左边框 + 层级徽标)
const LAYER_COLOR: Record<string, string> = {
  L0: '#531dab',
  L1: '#1677ff',
  L2: '#13c2c2',
  L3: '#52c41a',
  L4: '#8c8c8c',
}
const layerColor = (l?: string) => LAYER_COLOR[l ?? ''] ?? '#8c8c8c'

// 实现状态:配色 + 标签(antd Tag color)。蓝图含规划,一眼分清落地与否。
const IMPL_STATUS: Record<string, { color: string; label: string }> = {
  planned: { color: 'default', label: '规划中' },
  in_progress: { color: 'processing', label: '实现中' },
  done: { color: 'success', label: '已完成' },
}

type ChildrenMap = Record<string, ArchNode[]>

function buildChildrenMap(nodes: ArchNode[]): { roots: ArchNode[]; childrenMap: ChildrenMap } {
  const byId: Record<string, ArchNode> = {}
  const childrenMap: ChildrenMap = {}
  const roots: ArchNode[] = []
  nodes.forEach((n) => (byId[n.id] = n))
  nodes.forEach((n) => {
    if (n.parentId && byId[n.parentId]) (childrenMap[n.parentId] ??= []).push(n)
    else roots.push(n)
  })
  return { roots, childrenMap }
}

function NodeCard({
  node,
  selected,
  onOpen,
  onAddChild,
}: {
  node: ArchNode
  selected: boolean
  onOpen: () => void
  onAddChild: () => void
}) {
  const color = layerColor(node.layer)
  return (
    <div
      onClick={onOpen}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        padding: '6px 12px',
        background: selected ? '#e6f4ff' : '#fff',
        border: `1px solid ${selected ? '#1677ff' : '#e8e8e8'}`,
        borderLeft: `4px solid ${color}`,
        borderRadius: 8,
        boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
        cursor: 'pointer',
        maxWidth: 520,
      }}
    >
      {node.layer && (
        <span style={{ background: color, color: '#fff', borderRadius: 4, fontSize: 11, padding: '1px 6px', fontWeight: 600 }}>
          {node.layer}
        </span>
      )}
      <Text strong>{node.title}</Text>
      {node.impl_status && IMPL_STATUS[node.impl_status] && (
        <Tag color={IMPL_STATUS[node.impl_status].color} style={{ marginInlineEnd: 0 }}>
          {IMPL_STATUS[node.impl_status].label}
        </Tag>
      )}
      {node.type && <Text type="secondary" style={{ fontSize: 12 }}>{node.type}</Text>}
      {node.source === 'sync' && <Tag color="geekblue" style={{ marginInlineEnd: 0 }}>sync</Tag>}
      {(node.tags ?? []).slice(0, 4).map((t) => (
        <Tag key={t} style={{ marginInlineEnd: 0 }}>{t}</Tag>
      ))}
      <Button
        type="text"
        size="small"
        onClick={(e) => {
          e.stopPropagation()
          onAddChild()
        }}
        style={{ marginLeft: 4 }}
      >
        ＋
      </Button>
    </div>
  )
}

function Branch({
  node,
  childrenMap,
  selId,
  onOpen,
  onAddChild,
}: {
  node: ArchNode
  childrenMap: ChildrenMap
  selId: string | null
  onOpen: (n: ArchNode) => void
  onAddChild: (n: ArchNode) => void
}) {
  const kids = childrenMap[node.id] ?? []
  return (
    <div>
      <NodeCard node={node} selected={selId === node.id} onOpen={() => onOpen(node)} onAddChild={() => onAddChild(node)} />
      {kids.length > 0 && (
        <div
          style={{
            marginLeft: 18,
            paddingLeft: 18,
            borderLeft: '2px solid #e8e8e8',
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
            marginTop: 10,
          }}
        >
          {kids.map((k) => (
            <div key={k.id} style={{ display: 'flex', alignItems: 'flex-start' }}>
              <span style={{ width: 16, height: 18, borderTop: '2px solid #e8e8e8', marginTop: 14, marginLeft: -18, flex: '0 0 auto' }} />
              <Branch node={k} childrenMap={childrenMap} selId={selId} onOpen={onOpen} onAddChild={onAddChild} />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function ArchitectureTree({ pid }: { pid: string }) {
  const [nodes, setNodes] = useState<ArchNode[]>([])
  const [tag, setTag] = useState('')
  const [sel, setSel] = useState<ArchNode | null>(null)
  const [addOpen, setAddOpen] = useState(false)
  const [addParent, setAddParent] = useState<ArchNode | null>(null)
  const [form] = Form.useForm()

  const reload = async () => {
    try {
      setNodes(await listArch(pid, tag.trim() ? { tag: tag.trim() } : undefined))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载结构树失败')
    }
  }
  useEffect(() => {
    void reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pid, tag])

  const openAdd = (parent: ArchNode | null) => {
    setAddParent(parent)
    form.resetFields()
    form.setFieldsValue({ layer: parent ? LAYERS[Math.min(LAYERS.indexOf(parent.layer ?? 'L0') + 1, 4)] : 'L0' })
    setAddOpen(true)
  }

  const onAdd = async () => {
    const v = await form.validateFields()
    try {
      await createArchNode(pid, {
        parent_id: addParent?.id,
        title: v.title,
        layer: v.layer,
        type: v.type,
        description: v.description,
        tags: v.tags,
        related_docs: v.related_docs,
      })
      setAddOpen(false)
      await reload()
      message.success('已新建节点')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '新建失败')
    }
  }

  const onArchive = async (n: ArchNode) => {
    try {
      const r = await archiveArchNode(pid, n.id)
      setSel(null)
      await reload()
      message.success(`已归档 ${r.archived} 个节点(含子树)`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '归档失败')
    }
  }

  const { roots, childrenMap } = buildChildrenMap(nodes)

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card size="small">
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="按标签过滤(跨切面视图,如 安全)"
            style={{ width: 280 }}
            onSearch={(v) => setTag(v)}
            onChange={(e) => { if (!e.target.value) setTag('') }}
          />
          {tag && <Tag color="processing">标签视图:{tag}(含祖先链)</Tag>}
          <Button onClick={() => openAdd(null)}>＋ 新建根节点(L0)</Button>
          <Text type="secondary" style={{ fontSize: 12 }}>
            层级:{LAYERS.map((l) => (
              <Tag key={l} style={{ marginInlineStart: 4 }} color={layerColor(l)}>{l}</Tag>
            ))}
          </Text>
        </Space>
      </Card>

      <Card size="small" style={{ overflowX: 'auto' }}>
        {roots.length ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 4 }}>
            {roots.map((r) => (
              <Branch key={r.id} node={r} childrenMap={childrenMap} selId={sel?.id ?? null} onOpen={setSel} onAddChild={openAdd} />
            ))}
          </div>
        ) : (
          <Empty description={tag ? '该标签下无节点' : '还没有结构节点,先建一个 L0 根节点'} />
        )}
      </Card>

      <Drawer
        width={460}
        open={!!sel}
        onClose={() => setSel(null)}
        title={sel ? `${sel.layer ?? ''} ${sel.title}` : ''}
        extra={
          sel && (
            <Space>
              <Button size="small" onClick={() => openAdd(sel)}>＋ 子节点</Button>
              {sel.source !== 'sync' && (
                <Button size="small" danger onClick={() => onArchive(sel)}>归档</Button>
              )}
            </Space>
          )
        }
      >
        {sel && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text type="secondary">{sel.path}</Text>
            <Space wrap>
              {sel.type && <Tag>{sel.type}</Tag>}
              {sel.impl_status && IMPL_STATUS[sel.impl_status] && (
                <Tag color={IMPL_STATUS[sel.impl_status].color}>{IMPL_STATUS[sel.impl_status].label}</Tag>
              )}
              <Tag color={sel.source === 'sync' ? 'geekblue' : 'green'}>{sel.source}</Tag>
              {(sel.tags ?? []).map((t) => <Tag key={t}>{t}</Tag>)}
            </Space>
            {sel.description && <Paragraph type="secondary">{sel.description}</Paragraph>}
            {(sel.related_docs ?? []).length > 0 && (
              <>
                <Text strong>关联文档</Text>
                {(sel.related_docs ?? []).map((p) => (
                  <Link key={p} href={`/wiki?path=${encodeURIComponent(p)}`} target="_blank">{p}</Link>
                ))}
              </>
            )}
            {(sel.related_code ?? []).length > 0 && (
              <>
                <Text strong>关联代码</Text>
                {(sel.related_code ?? []).map((c) => <Text code key={c}>{c}</Text>)}
              </>
            )}
          </Space>
        )}
      </Drawer>

      <Drawer
        width={420}
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title={addParent ? `在「${addParent.title}」下新建子节点` : '新建根节点'}
        extra={<Button type="primary" onClick={onAdd}>创建</Button>}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="如:认证上下文" />
          </Form.Item>
          <Form.Item name="layer" label="层级">
            <Select options={LAYERS.map((l) => ({ value: l, label: l }))} />
          </Form.Item>
          <Form.Item name="type" label="类型">
            <Input placeholder="domain | context | module | component ..." />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="tags" label="标签">
            <Select mode="tags" placeholder="回车添加,如 安全 / JWT" />
          </Form.Item>
          <Form.Item name="related_docs" label="关联文档(wiki path)">
            <Select mode="tags" placeholder="如 /agent/herness-contract" />
          </Form.Item>
        </Form>
      </Drawer>
    </Space>
  )
}
