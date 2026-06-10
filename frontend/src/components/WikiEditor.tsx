// 视图层:知识库文档编辑器(元数据 + 左右分栏:Markdown 源码 | 实时预览)
import { Input, Select, Space } from 'antd'
import ReactMarkdown from 'react-markdown'
import type { WikiInput } from '../api/wiki'

const { TextArea } = Input

interface Props {
  value: WikiInput
  onChange: (v: WikiInput) => void
  isEdit: boolean // 编辑已有文档时锁定 path
}

export default function WikiEditor({ value, onChange, isEdit }: Props) {
  const set = (patch: Partial<WikiInput>) => onChange({ ...value, ...patch })

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Input
        placeholder="标题"
        value={value.title}
        onChange={(e) => set({ title: e.target.value })}
        size="large"
      />
      <Space wrap>
        <Input
          placeholder="路径,如 /dev/react-style"
          value={value.path}
          onChange={(e) => set({ path: e.target.value })}
          disabled={isEdit}
          style={{ width: 340 }}
        />
        <Input
          placeholder="父路径(可空,用于目录树)"
          value={value.parentPath}
          onChange={(e) => set({ parentPath: e.target.value })}
          style={{ width: 340 }}
        />
      </Space>
      <Select
        mode="tags"
        placeholder="输入标签后回车添加"
        value={value.tags}
        onChange={(tags: string[]) => set({ tags })}
        style={{ width: '100%' }}
        tokenSeparators={[',', '，']}
      />

      <div style={{ display: 'flex', gap: 12 }}>
        <TextArea
          value={value.content}
          onChange={(e) => set({ content: e.target.value })}
          placeholder="Markdown 源文本"
          style={{ flex: 1, height: 460, fontFamily: 'monospace' }}
        />
        <div
          style={{
            flex: 1,
            height: 460,
            overflow: 'auto',
            border: '1px solid #f0f0f0',
            borderRadius: 8,
            padding: '8px 16px',
            background: '#fafafa',
          }}
        >
          <ReactMarkdown>{value.content || '_(实时预览)_'}</ReactMarkdown>
        </div>
      </div>
    </Space>
  )
}
