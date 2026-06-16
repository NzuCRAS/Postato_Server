// 视图层:架构图谱 —— 模块依赖图(mermaid)+ 模块档案(依赖/三类索引/代码/状态)
import { useMemo, useState } from 'react'
import { Card, Col, Empty, Row, Select, Space, Spin, Tag, Typography } from 'antd'
import { useArchGraph, archToMermaid } from '../features/useArchGraph'
import type { ArchDocIndex, ArchModuleItem } from '../types'
import MermaidBlock from './MermaidBlock'

const { Title, Text, Paragraph } = Typography

const IMPL_COLOR: Record<string, string> = { planned: 'default', in_progress: 'processing', done: 'success' }
const DOC_LABEL: Record<string, string> = { requirement: '需求', tech_doc: '技术说明', experience: '经验' }

function DocList({ docs, type }: { docs: ArchDocIndex[]; type: string }) {
  const list = docs.filter((d) => d.type === type)
  if (list.length === 0) return <Text type="secondary">无</Text>
  return (
    <ul style={{ margin: 0, paddingLeft: 18 }}>
      {list.map((d, i) => (
        <li key={`${d.ref}-${i}`}>
          <Text code>{d.ref}</Text>
          {d.title ? ` — ${d.title}` : ''}
          {d.scope && d.scope.length > 1 && (
            <Tag color="purple" style={{ marginLeft: 6 }}>跨 {d.scope.join(' ↔ ')}</Tag>
          )}
        </li>
      ))}
    </ul>
  )
}

function ModuleDossier({ module, inEdges, outEdges }: {
  module: ArchModuleItem
  inEdges: { from: string; kind?: string }[]
  outEdges: { to: string; kind?: string }[]
}) {
  const docs = module.docs ?? []
  return (
    <Card size="small" title={<Space><Text strong>{module.title ?? module.key}</Text><Text code>{module.key}</Text></Space>}>
      <Space wrap style={{ marginBottom: 8 }}>
        {module.group && <Tag>{module.group}</Tag>}
        <Tag color={IMPL_COLOR[module.impl_status ?? 'planned']}>{module.impl_status ?? 'planned'}</Tag>
      </Space>
      {module.description && <Paragraph type="secondary">{module.description}</Paragraph>}

      <Title level={5}>依赖</Title>
      <div>出边(依赖):{outEdges.length ? outEdges.map((e, i) => <Tag key={i}>{e.to}{e.kind ? `(${e.kind})` : ''}</Tag>) : <Text type="secondary">无</Text>}</div>
      <div style={{ marginTop: 4 }}>入边(被依赖):{inEdges.length ? inEdges.map((e, i) => <Tag key={i}>{e.from}{e.kind ? `(${e.kind})` : ''}</Tag>) : <Text type="secondary">无</Text>}</div>

      {(['requirement', 'tech_doc', 'experience'] as const).map((t) => (
        <div key={t} style={{ marginTop: 12 }}>
          <Title level={5} style={{ marginBottom: 4 }}>{DOC_LABEL[t]}</Title>
          <DocList docs={docs} type={t} />
        </div>
      ))}

      <Title level={5} style={{ marginTop: 12 }}>related_code</Title>
      {(module.related_code ?? []).length
        ? <ul style={{ margin: 0, paddingLeft: 18 }}>{module.related_code!.map((c) => <li key={c}><Text code>{c}</Text></li>)}</ul>
        : <Text type="secondary">无</Text>}
    </Card>
  )
}

export default function ArchitectureGraph({ pid }: { pid: string }) {
  const { graph, loading } = useArchGraph(pid)
  const [sel, setSel] = useState<string | null>(null)

  const chart = useMemo(() => archToMermaid(graph), [graph])
  const modules = graph?.modules ?? []
  const edges = graph?.edges ?? []
  const selected = modules.find((m) => m.key === sel) ?? null

  if (loading) return <Spin style={{ display: 'block', marginTop: 60 }} />

  return (
    <Row gutter={16}>
      <Col span={15}>
        <Card size="small" title="模块依赖图" styles={{ body: { overflow: 'auto' } }}>
          {modules.length === 0
            ? <Empty description="暂无模块。用 MCP upsert_arch_module / upsert_arch_edge 声明,或等数据重建。" />
            : <MermaidBlock chart={chart} />}
        </Card>
      </Col>
      <Col span={9}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            style={{ width: '100%' }}
            placeholder="选择模块查看档案"
            showSearch
            optionFilterProp="label"
            value={sel ?? undefined}
            onChange={setSel}
            options={modules.map((m) => ({ value: m.key, label: `${m.title ?? m.key} (${m.key})` }))}
          />
          {selected
            ? <ModuleDossier
                module={selected}
                outEdges={edges.filter((e) => e.from === selected.key)}
                inEdges={edges.filter((e) => e.to === selected.key)}
              />
            : <Empty description="选择左图中的一个模块查看其档案(依赖 / 需求 / 技术 / 经验)" />}
        </Space>
      </Col>
    </Row>
  )
}
