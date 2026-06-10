// 视图层:项目结构树(L0-L4 业务域)。树渲染 + 节点详情 + 新建/归档 + 标签跨切面过滤。
import { useEffect, useState } from 'react'
import { Button, Card, Drawer, Empty, Form, Input, Select, Space, Tag, Tree, Typography, message } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { archiveArchNode, createArchNode, listArch } from '../api/archNode'
import type { ArchNode } from '../types'

const { Text, Link, Paragraph } = Typography
const LAYERS = ['L0', 'L1', 'L2', 'L3', 'L4']

function nodeTitle(n: ArchNode) {
  return (
    <Space size={4} wrap>
      {n.layer && <Tag color="purple">{n.layer}</Tag>}
      <Text strong>{n.title}</Text>
      {n.type && <Text type="secondary">{n.type}</Text>}
      {n.source === 'sync' && <Tag color="geekblue">sync</Tag>}
      {(n.tags ?? []).map((t) => <Tag key={t}>{t}</Tag>)}
    </Space>
  )
}

function buildTree(nodes: ArchNode[]): DataNode[] {
  const byId: Record<string, ArchNode> = {}
  const children: Record<string, ArchNode[]> = {}
  const roots: ArchNode[] = []
  nodes.forEach((n) => (byId[n.id] = n))
  nodes.forEach((n) => {
    if (n.parentId && byId[n.parentId]) (children[n.parentId] ??= []).push(n)
    else roots.push(n)
  })
  const toData = (n: ArchNode): DataNode => ({
    key: n.id,
    title: nodeTitle(n),
    children: (children[n.id] ?? []).map(toData),
  })
  return roots.map(toData)
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

  const byId: Record<string, ArchNode> = {}
  nodes.forEach((n) => (byId[n.id] = n))

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

  const treeData = buildTree(nodes)

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
          <Button onClick={() => openAdd(null)}>+ 新建根节点(L0)</Button>
        </Space>
      </Card>
      <Card size="small">
        {treeData.length ? (
          <Tree
            treeData={treeData}
            defaultExpandAll
            selectable
            onSelect={(keys) => keys[0] && setSel(byId[String(keys[0])] ?? null)}
          />
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
              <Button size="small" onClick={() => openAdd(sel)}>+ 子节点</Button>
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
