// 视图层:项目列表 + 新建
import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, List, Modal, Space, Typography, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useProjects } from '../features/ProjectContext'
import { createProject } from '../api/project'

export default function ProjectsPage() {
  const { projects, reload, setCurrentId } = useProjects()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    void reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onCreate = async () => {
    const v = await form.validateFields()
    try {
      const p = await createProject(v.name, v.descriptionMd)
      await reload()
      setCurrentId(p.id)
      setOpen(false)
      form.resetFields()
      message.success('已创建项目')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '创建失败')
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>项目</Typography.Title>
        <Button type="primary" onClick={() => setOpen(true)}>+ 新建项目</Button>
      </div>
      <List
        grid={{ gutter: 16, column: 3 }}
        dataSource={projects}
        renderItem={(p) => (
          <List.Item>
            <Card hoverable title={p.name} onClick={() => navigate(`/projects/${p.id}`)}>
              <Typography.Paragraph type="secondary" ellipsis={{ rows: 2 }}>
                {p.descriptionMd || '(无描述)'}
              </Typography.Paragraph>
              <Typography.Text type="secondary">{p.repos?.length ?? 0} 个仓库 · {p.docLinks?.length ?? 0} 篇文档</Typography.Text>
            </Card>
          </List.Item>
        )}
      />
      <Modal open={open} title="新建项目" okText="创建" onOk={onCreate} onCancel={() => setOpen(false)}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="项目名" rules={[{ required: true, message: '必填' }]}>
            <Input placeholder="如:Potato 平台" />
          </Form.Item>
          <Form.Item name="descriptionMd" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
