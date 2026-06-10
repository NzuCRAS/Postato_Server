// 视图层:结构化需求的只读渲染
import { Card, Empty, List, Space, Typography } from 'antd'
import type { Structured } from '../types'

const { Paragraph, Text } = Typography

export default function StructuredView({ structured }: { structured: Structured }) {
  const hasContent =
    (structured.user_stories?.length ?? 0) > 0 ||
    (structured.modules?.length ?? 0) > 0 ||
    !!structured.interaction_flow

  if (!hasContent) return <Empty description="尚无结构化内容" />

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      {structured.user_stories?.length ? (
        <Card size="small" title="用户故事">
          <List
            dataSource={structured.user_stories}
            renderItem={(s, i) => <List.Item>{i + 1}. {s}</List.Item>}
          />
        </Card>
      ) : null}

      {structured.modules?.length ? (
        <Card size="small" title="模块">
          <Space direction="vertical" style={{ width: '100%' }}>
            {structured.modules.map((m, i) => (
              <Card key={i} type="inner" title={m.name || `模块 ${i + 1}`}>
                {m.description && <Paragraph>{m.description}</Paragraph>}
                {m.acceptance_criteria?.length ? (
                  <>
                    <Text strong>验收条件:</Text>
                    <List
                      size="small"
                      dataSource={m.acceptance_criteria}
                      renderItem={(c) => <List.Item>☑ {c}</List.Item>}
                    />
                  </>
                ) : null}
              </Card>
            ))}
          </Space>
        </Card>
      ) : null}

      {structured.interaction_flow ? (
        <Card size="small" title="交互流程">
          <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{structured.interaction_flow}</Paragraph>
        </Card>
      ) : null}
    </Space>
  )
}
