// 视图层:项目详情(Tabs:概览/仓库/结构树/文档/需求)
import { useEffect, useState } from 'react'
import { Alert, Button, Card, Form, Input, List, Space, Spin, Table, Tabs, Tag, Typography, message } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import { addDocLink, addRepo, getProject, removeDocLink, removeRepo } from '../api/project'
import { useRequirements } from '../features/useRequirements'
import { statusColor, statusLabel } from '../features/requirementStatus'
import ArchitectureTree from '../components/ArchitectureTree'
import type { Project } from '../types'

const { Title, Text, Link, Paragraph } = Typography

export default function ProjectDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [project, setProject] = useState<Project | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [repoForm] = Form.useForm()
  const [docForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      setProject(await getProject(id))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const reqs = useRequirements(undefined, id)

  if (loading) return <Spin style={{ display: 'block', marginTop: 80 }} />
  if (error) return <Alert type="error" message={error} />
  if (!project) return null

  const onAddRepo = async () => {
    const v = await repoForm.validateFields()
    try {
      setProject(await addRepo(id, { name: v.name, url: v.url, provider: v.provider || 'github', default_branch: v.default_branch || 'main' }))
      repoForm.resetFields()
      message.success('已添加仓库')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '添加失败')
    }
  }
  const onAddDoc = async () => {
    const v = await docForm.validateFields()
    try {
      setProject(await addDocLink(id, { type: v.type, title: v.title, path: v.path }))
      docForm.resetFields()
      message.success('已关联文档')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '关联失败')
    }
  }

  const overviewTab = (
    <Card>
      <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{project.descriptionMd || '(无描述)'}</Paragraph>
      <Text type="secondary" copyable>{project.id}</Text>
    </Card>
  )

  const reposTab = (
    <Space direction="vertical" style={{ width: '100%' }}>
      <List
        bordered
        dataSource={project.repos ?? []}
        locale={{ emptyText: '暂无仓库' }}
        renderItem={(r) => (
          <List.Item actions={[<Button key="d" type="link" danger onClick={async () => setProject(await removeRepo(id, r.id))}>删除</Button>]}>
            <Space direction="vertical" size={0}>
              <Link href={r.url} target="_blank">{r.name || r.url}</Link>
              <Text type="secondary">{r.provider} · {r.default_branch} · {r.id}</Text>
            </Space>
          </List.Item>
        )}
      />
      <Card size="small" title="添加仓库">
        <Form form={repoForm} layout="inline">
          <Form.Item name="name"><Input placeholder="名称" /></Form.Item>
          <Form.Item name="url" rules={[{ required: true, message: 'URL 必填' }]}><Input placeholder="https://github.com/org/repo" style={{ width: 320 }} /></Form.Item>
          <Form.Item name="default_branch"><Input placeholder="main" /></Form.Item>
          <Form.Item><Button onClick={onAddRepo}>添加</Button></Form.Item>
        </Form>
      </Card>
    </Space>
  )

  const docsTab = (
    <Space direction="vertical" style={{ width: '100%' }}>
      <List
        bordered
        dataSource={project.docLinks ?? []}
        locale={{ emptyText: '暂无关联文档' }}
        renderItem={(d) => (
          <List.Item actions={[<Button key="d" type="link" danger onClick={async () => setProject(await removeDocLink(id, d.path))}>删除</Button>]}>
            <Space>
              {d.type && <Tag>{d.type}</Tag>}
              <Link href={`/wiki?path=${encodeURIComponent(d.path)}`} target="_blank">{d.title || d.path}</Link>
              <Text type="secondary">{d.path}</Text>
            </Space>
          </List.Item>
        )}
      />
      <Card size="small" title="关联知识库文档(设计/规范/效果参考)">
        <Form form={docForm} layout="inline">
          <Form.Item name="type" initialValue="design"><Input placeholder="design/standard/reference" /></Form.Item>
          <Form.Item name="title"><Input placeholder="标题" /></Form.Item>
          <Form.Item name="path" rules={[{ required: true, message: 'wiki path 必填' }]}><Input placeholder="/dev/react-style" style={{ width: 260 }} /></Form.Item>
          <Form.Item><Button onClick={onAddDoc}>关联</Button></Form.Item>
        </Form>
      </Card>
    </Space>
  )

  const reqTab = (
    <Table
      rowKey="id"
      loading={reqs.loading}
      dataSource={reqs.items}
      pagination={false}
      onRow={(r) => ({ onClick: () => navigate(`/requirements/${r.id}`), style: { cursor: 'pointer' } })}
      columns={[
        { title: '标题', dataIndex: 'title' },
        { title: '状态', dataIndex: 'status', width: 120, render: (s: string) => <Tag color={statusColor(s)}>{statusLabel(s)}</Tag> },
        { title: '版本', dataIndex: 'version', width: 80 },
      ]}
    />
  )

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={3} style={{ margin: 0 }}>{project.name}</Title>
        <Button onClick={() => navigate('/projects')}>返回项目列表</Button>
      </div>
      <Tabs
        items={[
          { key: 'overview', label: '概览', children: overviewTab },
          { key: 'repos', label: `仓库 (${project.repos?.length ?? 0})`, children: reposTab },
          { key: 'arch', label: '结构树', children: <ArchitectureTree pid={id} /> },
          { key: 'docs', label: `文档 (${project.docLinks?.length ?? 0})`, children: docsTab },
          { key: 'reqs', label: '需求', children: reqTab },
        ]}
      />
    </Space>
  )
}
