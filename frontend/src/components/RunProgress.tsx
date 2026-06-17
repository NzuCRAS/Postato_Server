// 视图层:SOP 执行工作流进度(只读)——按步展示状态/执行结果/忽略原因/注入文档
import { useEffect, useState } from 'react'
import { Card, Empty, Space, Spin, Steps, Tag, Typography } from 'antd'
import { CheckCircleOutlined, MinusCircleOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { getRun } from '../api/run'
import type { SopRunItem, RunStepItem } from '../types'

const { Text } = Typography

const RUN_STATUS: Record<string, { color: string; label: string }> = {
  running: { color: 'processing', label: '进行中' },
  finished: { color: 'success', label: '已完成' },
  aborted: { color: 'default', label: '已中止' },
}

function StepDesc({ s, onOpenDoc }: { s: RunStepItem; onOpenDoc: (path: string) => void }) {
  return (
    <div style={{ fontSize: 12 }}>
      {s.note && <div style={{ whiteSpace: 'pre-wrap' }}>{s.note}</div>}
      {s.skip_reason && <div style={{ color: '#cf1322' }}>忽略原因:{s.skip_reason}</div>}
      {s.injected_docs && s.injected_docs.length > 0 && (
        <div style={{ marginTop: 4 }}>
          <Text type="secondary">注入文档:</Text>
          {s.injected_docs.map((d) =>
            // 路径类(以 / 开头)可跳到对应文档;非路径(如需求 id)保持纯文本
            d.startsWith('/') ? (
              <Tag
                key={d}
                color="blue"
                style={{ marginInlineStart: 4, cursor: 'pointer' }}
                onClick={() => onOpenDoc(d)}
              >
                {d}
              </Tag>
            ) : (
              <Tag key={d} style={{ marginInlineStart: 4 }}>{d}</Tag>
            ),
          )}
        </div>
      )}
    </div>
  )
}

export default function RunProgress({ reqId }: { reqId: string }) {
  const navigate = useNavigate()
  const [run, setRun] = useState<SopRunItem | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getRun(reqId)
      .then((r) => { if (!cancelled) setRun(r ?? null) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [reqId])

  if (loading) return <Spin style={{ display: 'block', marginTop: 40 }} />
  if (!run) return <Empty description="尚未走过 SOP 工作流(需求开发开始后由 advance_run 自动创建)" />

  const total = run.steps.length
  const tag = RUN_STATUS[run.status] ?? { color: 'default', label: run.status }
  const doneCount = run.steps.filter((s) => s.status === 'done' || s.status === 'skipped').length

  const items = run.steps.map((s, i) => {
    const stepStatus =
      s.status === 'done' || s.status === 'skipped' ? 'finish'
        : run.status === 'running' && i === run.current_step_index ? 'process'
          : 'wait'
    return {
      title: s.title + (s.status === 'skipped' ? '(已跳过)' : ''),
      status: stepStatus as 'finish' | 'process' | 'wait',
      icon: s.status === 'skipped' ? <MinusCircleOutlined style={{ color: '#bfbfbf' }} />
        : s.status === 'done' ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : undefined,
      description: <StepDesc s={s} onOpenDoc={(path) => navigate(`/wiki?path=${encodeURIComponent(path)}`)} />,
    }
  })

  return (
    <Card size="small">
      <Space wrap style={{ marginBottom: 12 }}>
        <Tag>tier: {run.tier ?? '—'}</Tag>
        <Tag color={tag.color}>{tag.label}</Tag>
        <Text type="secondary">进度 {doneCount}/{total}</Text>
        {run.runlog_path && <Text type="secondary">runlog: <Text code>{run.runlog_path}</Text></Text>}
      </Space>
      <Steps direction="vertical" size="small" current={run.current_step_index} items={items} />
    </Card>
  )
}
