// 视图层:知识库文档独立编辑页(整页 编辑/分屏/预览)
import { useState } from 'react'
import { Button, Input, Segmented, Select, Space, Spin, Upload, message } from 'antd'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useWikiEditor } from '../features/useWikiEditor'
import MarkdownView from '../components/MarkdownView'
import { handleMarkdownTabIndent } from '../features/markdown'
import type { WikiInput } from '../api/wiki'

const { TextArea } = Input

type ViewMode = '编辑' | '分屏' | '预览'

export default function WikiEditPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { form, setForm, assets, loading, saving, save, upload, removeAsset } = useWikiEditor(id, searchParams.get('path') ?? undefined)
  const [view, setView] = useState<ViewMode>('分屏')

  if (loading || !form) return <Spin style={{ display: 'block', marginTop: 80 }} />

  const set = (patch: Partial<WikiInput>) => setForm({ ...form, ...patch })

  const onSave = async () => {
    if (!form.title.trim() || !form.path.trim()) {
      message.error('标题和路径必填')
      return
    }
    try {
      await save()
      message.success('已保存')
      navigate('/wiki')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 112px)' }}>
      <Space direction="vertical" style={{ width: '100%' }} size="small">
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Input
            size="large"
            placeholder="标题"
            value={form.title}
            onChange={(e) => set({ title: e.target.value })}
            style={{ flex: 1 }}
          />
          <Segmented<ViewMode>
            options={['编辑', '分屏', '预览']}
            value={view}
            onChange={setView}
          />
          <Button onClick={() => navigate('/wiki')}>取消</Button>
          <Button type="primary" loading={saving} onClick={onSave}>保存</Button>
        </div>
        <Space wrap>
          <Input
            placeholder="路径,如 /dev/react-style"
            value={form.path}
            onChange={(e) => set({ path: e.target.value })}
            disabled={!!id}
            style={{ width: 300 }}
          />
          <Select
            mode="tags"
            placeholder="标签(回车添加)"
            value={form.tags}
            onChange={(tags: string[]) => set({ tags })}
            tokenSeparators={[',', '，']}
            style={{ minWidth: 260 }}
          />
          <Select
            value={form.category ?? 'doc'}
            onChange={(category: string) => set({ category })}
            style={{ width: 160 }}
            options={[
              { value: 'doc', label: 'doc 通用' },
              { value: 'asset', label: 'asset 可复用' },
              { value: 'standard', label: 'standard 规范' },
              { value: 'experience', label: 'experience 经验' },
            ]}
          />
        </Space>
        {id && (
          <Space wrap>
            <Upload
              showUploadList={false}
              beforeUpload={(file) => {
                upload(file).then(() => message.success('已上传')).catch((e) => message.error(e.message))
                return false
              }}
            >
              <Button>上传资产</Button>
            </Upload>
            {assets.map((a) => (
              <Space key={a.objectKey} size={4}>
                <a href={a.url} target="_blank" rel="noreferrer">{a.name}</a>
                <Button size="small" type="link" onClick={() => navigator.clipboard.writeText(a.url)}>复制URL</Button>
                <Button size="small" type="link" danger onClick={() => removeAsset(a.objectKey)}>删除</Button>
              </Space>
            ))}
          </Space>
        )}
      </Space>

      <div style={{ display: 'flex', gap: 12, flex: 1, marginTop: 12, minHeight: 0 }}>
        {view !== '预览' && (
          <TextArea
            value={form.content}
            onChange={(e) => set({ content: e.target.value })}
            onKeyDown={(e) => handleMarkdownTabIndent(e, form.content ?? '', (v) => set({ content: v }))}
            placeholder="Markdown 源文本"
            style={{ flex: 1, height: '100%', resize: 'none', fontFamily: 'monospace' }}
          />
        )}
        {view !== '编辑' && (
          <div
            style={{
              flex: 1,
              height: '100%',
              overflow: 'auto',
              border: '1px solid #f0f0f0',
              borderRadius: 8,
              padding: '12px 20px',
              background: '#fff',
            }}
          >
            <MarkdownView content={form.content ?? ''} />
          </div>
        )}
      </div>
    </div>
  )
}
