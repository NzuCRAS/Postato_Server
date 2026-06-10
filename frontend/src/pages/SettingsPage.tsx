// 视图层:设置页 / API Key 管理。逻辑全在 useAuth + useApiKeys。
import { useState } from 'react'
import {
  Button, Card, Descriptions, Input, List, Modal, Popconfirm, Space, Tag, Typography, message,
} from 'antd'
import { useAuth } from '../auth/AuthContext'
import { useApiKeys } from '../features/useApiKeys'

const { Text, Paragraph } = Typography

export default function SettingsPage() {
  const { user } = useAuth()
  const { apiKeys, create, remove, creating, newKey, clearNewKey } = useApiKeys()
  const [name, setName] = useState('')

  const onCreate = async () => {
    try {
      await create(name.trim() || 'default')
      setName('')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '生成失败')
    }
  }

  const onRemove = async (id: string) => {
    try {
      await remove(id)
      message.success('已删除')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败')
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card title="我的信息">
        <Descriptions column={1}>
          <Descriptions.Item label="用户名">{user?.username}</Descriptions.Item>
          <Descriptions.Item label="职能">
            {user?.functions.map((f) => <Tag key={f}>{f}</Tag>)}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="API Key(用于 Claude Code MCP 接入)">
        <Space.Compact style={{ marginBottom: 16, width: '100%' }}>
          <Input
            placeholder="给这个 Key 起个名字,如 my-claude-code"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onPressEnter={onCreate}
          />
          <Button type="primary" loading={creating} onClick={onCreate}>生成新 Key</Button>
        </Space.Compact>
        <List
          dataSource={apiKeys}
          locale={{ emptyText: '还没有 API Key' }}
          renderItem={(k) => (
            <List.Item
              actions={[
                <Popconfirm key="del" title="确认删除该 Key?" onConfirm={() => onRemove(k.id)}>
                  <Button danger size="small">删除</Button>
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta title={k.name} description={<Text code>{k.keyPreview}</Text>} />
            </List.Item>
          )}
        />
      </Card>

      <Modal
        open={!!newKey}
        title="新 API Key(仅显示这一次,请立即复制)"
        onOk={clearNewKey}
        onCancel={clearNewKey}
        okText="我已复制"
        cancelButtonProps={{ style: { display: 'none' } }}
      >
        <Paragraph copyable={{ text: newKey?.key }}>
          <Text code>{newKey?.key}</Text>
        </Paragraph>
      </Modal>
    </Space>
  )
}
