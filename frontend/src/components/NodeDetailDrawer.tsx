// 视图层:节点详情抽屉。展示描述/验收/关联文档/产物/工作日志(带 commit 链接)/纠偏,并把操作转给 hook。
import { useState } from 'react'
import {
  Button,
  Checkbox,
  Divider,
  Drawer,
  Dropdown,
  Empty,
  Input,
  Space,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd'
import type { AcceptanceItem, DevPlanNode, DevPlanRepo } from '../types'

const { Text, Paragraph, Link } = Typography

const STATUS_LABEL: Record<string, string> = {
  todo: '待办',
  in_progress: '进行中',
  done: '完成',
  blocked: '阻塞',
}
const STATUS_COLOR: Record<string, string> = {
  todo: 'default',
  in_progress: 'processing',
  done: 'success',
  blocked: 'error',
}
const STATUSES = ['todo', 'in_progress', 'done', 'blocked']

function commitHref(repo: DevPlanRepo | undefined, sha: string, url?: string): string | undefined {
  if (url) return url
  if (repo?.url) return `${repo.url.replace(/\/$/, '')}/commit/${sha}`
  return undefined
}

export default function NodeDetailDrawer({
  node,
  repo,
  open,
  onClose,
  onChangeStatus,
  onToggleAcceptance,
  onLeaveCorrection,
  onResolveCorrection,
}: {
  node: DevPlanNode | null
  repo?: DevPlanRepo
  open: boolean
  onClose: () => void
  onChangeStatus: (nodeId: string, status: string) => Promise<string[] | undefined>
  onToggleAcceptance: (nodeId: string, items: AcceptanceItem[]) => Promise<void>
  onLeaveCorrection: (nodeId: string, msg: string) => Promise<unknown>
  onResolveCorrection: (nodeId: string, cid: string) => Promise<unknown>
}) {
  const [correction, setCorrection] = useState('')

  if (!node) return null

  const changeStatus = async (status: string) => {
    const warnings = await onChangeStatus(node.id, status)
    if (warnings === undefined) return // 失败或取消,错误/提示已弹过,别再报成功
    if (warnings.length) warnings.forEach((w) => message.warning(w))
    else message.success(`已改为「${STATUS_LABEL[status] ?? status}」`)
  }

  const toggleAcceptance = async (idx: number) => {
    const items = (node.acceptance_criteria ?? []).map((a, i) =>
      i === idx ? { ...a, checked: !a.checked } : a,
    )
    await onToggleAcceptance(node.id, items)
  }

  const submitCorrection = async () => {
    if (!correction.trim()) return
    const created = await onLeaveCorrection(node.id, correction.trim())
    if (!created) return // 失败:错误已由 hook 弹出,别再报"已留下"
    setCorrection('')
    message.success('已留下纠偏指令')
  }

  return (
    <Drawer
      title={
        <Space>
          <Tag color={STATUS_COLOR[node.status] ?? 'default'}>{STATUS_LABEL[node.status] ?? node.status}</Tag>
          <span>{node.title}</span>
        </Space>
      }
      width={460}
      open={open}
      onClose={onClose}
      extra={
        <Dropdown
          menu={{
            items: STATUSES.map((s) => ({ key: s, label: STATUS_LABEL[s] })),
            // 所有状态统一交给 changeStatus → onChangeStatus(页面层负责 blocked 弹窗收原因)
            onClick: ({ key }) => changeStatus(key),
          }}
        >
          <Button size="small">改状态 ▾</Button>
        </Dropdown>
      }
    >
      {node.description && <Paragraph type="secondary">{node.description}</Paragraph>}
      {node.status === 'blocked' && node.blocked_reason && (
        <Paragraph type="danger">阻塞原因:{node.blocked_reason}</Paragraph>
      )}

      <Divider orientation="left" plain>验收标准</Divider>
      {node.module_ref && <Text type="secondary">继承自模块:{node.module_ref}</Text>}
      {(node.acceptance_criteria ?? []).length ? (
        <Space direction="vertical">
          {(node.acceptance_criteria ?? []).map((a, i) => (
            <Checkbox key={i} checked={a.checked} onChange={() => toggleAcceptance(i)}>
              {a.text}
            </Checkbox>
          ))}
        </Space>
      ) : (
        <Text type="secondary">(无细化验收点)</Text>
      )}

      <Divider orientation="left" plain>关联文档</Divider>
      {(node.related_docs ?? []).length ? (
        <Space direction="vertical">
          {(node.related_docs ?? []).map((p) => (
            <Link key={p} href={`/wiki?path=${encodeURIComponent(p)}`} target="_blank">
              {p}
            </Link>
          ))}
        </Space>
      ) : (
        <Text type="secondary">(无)</Text>
      )}

      <Divider orientation="left" plain>产物</Divider>
      <Space direction="vertical" size={2}>
        {node.artifacts?.branch && <Text>分支:{node.artifacts.branch}</Text>}
        {node.artifacts?.pr_number != null && (
          <Text>
            PR:{' '}
            {node.artifacts.pr_url ? (
              <Link href={node.artifacts.pr_url} target="_blank">#{node.artifacts.pr_number}</Link>
            ) : (
              `#${node.artifacts.pr_number}`
            )}
          </Text>
        )}
        {node.artifacts?.tests_added != null && <Text>测试:{node.artifacts.tests_added ? '已加' : '未加'}</Text>}
        {node.artifacts?.tech_proposal_id && (
          <Text>
            技术方案:{' '}
            <Link href={`/wiki?path=${encodeURIComponent(node.artifacts.tech_proposal_id)}`} target="_blank">
              {node.artifacts.tech_proposal_id}
            </Link>
          </Text>
        )}
        {!node.artifacts?.branch && node.artifacts?.pr_number == null && !node.artifacts?.tech_proposal_id && (
          <Text type="secondary">(无)</Text>
        )}
      </Space>

      <Divider orientation="left" plain>工作日志</Divider>
      {(node.log ?? []).length ? (
        <Timeline
          items={(node.log ?? []).map((e) => ({
            color: e.actor === 'ai' ? 'blue' : 'green',
            children: (
              <div>
                <Text strong>
                  {e.actor === 'ai' ? '🤖 AI' : '👤 人'} · {e.summary}
                </Text>
                {e.from && e.to && (
                  <div>
                    <Text type="secondary">{e.from} → {e.to}</Text>
                  </div>
                )}
                {e.detail && <Paragraph type="secondary" style={{ margin: 0 }}>{e.detail}</Paragraph>}
                {e.commit && (
                  <div>
                    {commitHref(repo, e.commit.sha, e.commit.url) ? (
                      <Link href={commitHref(repo, e.commit.sha, e.commit.url)} target="_blank">
                        commit {e.commit.sha.slice(0, 7)}
                      </Link>
                    ) : (
                      <Text code>commit {e.commit.sha.slice(0, 7)}</Text>
                    )}
                    {e.commit.message ? <Text type="secondary"> — {e.commit.message}</Text> : null}
                  </div>
                )}
              </div>
            ),
          }))}
        />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无日志" />
      )}

      <Divider orientation="left" plain>纠偏(给 AI 的指令)</Divider>
      {(node.corrections ?? []).map((c) => (
        <div key={c.id} style={{ marginBottom: 8 }}>
          <Space>
            <Tag color={c.resolved ? 'success' : 'warning'}>{c.resolved ? '已解决' : '未解决'}</Tag>
            <Text>{c.message}</Text>
          </Space>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>by {c.by}</Text>
            {!c.resolved && (
              <Button type="link" size="small" onClick={() => onResolveCorrection(node.id, c.id)}>
                标记已解决
              </Button>
            )}
          </div>
        </div>
      ))}
      <Space.Compact style={{ width: '100%', marginTop: 8 }}>
        <Input
          placeholder="留一条自然语言纠偏指令,AI 下次会读到"
          value={correction}
          onChange={(e) => setCorrection(e.target.value)}
          onPressEnter={submitCorrection}
        />
        <Button type="primary" onClick={submitCorrection}>留言</Button>
      </Space.Compact>
    </Drawer>
  )
}
