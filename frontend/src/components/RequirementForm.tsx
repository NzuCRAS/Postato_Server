// 视图层:需求表单(创建/编辑共用)—— 独立整页 + 编辑/分屏/预览
import { useState } from 'react'
import { Button, Input, Segmented, Typography, message } from 'antd'
import MarkdownView from './MarkdownView'
import { handleMarkdownTabIndent } from '../features/markdown'
import StructuredView from './StructuredView'
import type { Structured } from '../types'

const { TextArea } = Input
const { Text } = Typography

type ViewMode = '编辑' | '分屏' | '预览'

const STRUCTURED_TEMPLATE = JSON.stringify(
  {
    user_stories: ['作为用户,我希望……'],
    modules: [{ name: '模块名', description: '', acceptance_criteria: [] }],
    interaction_flow: '',
    ambiguous_points: [],
  },
  null,
  2,
)

export interface RequirementFormValues {
  title: string
  descriptionMd: string
  structured: Structured
}

interface Props {
  mode: 'create' | 'edit'
  initial?: { title?: string; descriptionMd?: string; structured?: Structured }
  submitting: boolean
  onSubmit: (values: RequirementFormValues, status?: string) => void
  onCancel: () => void
}

export default function RequirementForm({ mode, initial, submitting, onSubmit, onCancel }: Props) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [descriptionMd, setDescriptionMd] = useState(initial?.descriptionMd ?? '')
  const [structuredText, setStructuredText] = useState(
    initial?.structured ? JSON.stringify(initial.structured, null, 2) : STRUCTURED_TEMPLATE,
  )
  const [view, setView] = useState<ViewMode>('分屏')

  let parsedStructured: Structured | null = null
  let parseError = ''
  try {
    parsedStructured = JSON.parse(structuredText)
  } catch {
    parseError = 'JSON 格式有误'
  }

  const handle = (status?: string) => {
    if (!title.trim()) {
      message.error('请输入标题')
      return
    }
    if (!parsedStructured) {
      message.error('结构化内容 ' + parseError)
      return
    }
    onSubmit({ title: title.trim(), descriptionMd, structured: parsedStructured }, status)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 112px)' }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <Input
          size="large"
          placeholder="需求标题"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          style={{ flex: 1 }}
        />
        <Segmented<ViewMode> options={['编辑', '分屏', '预览']} value={view} onChange={setView} />
        {mode === 'create' ? (
          <>
            <Button loading={submitting} onClick={() => handle('draft')}>存草稿</Button>
            <Button type="primary" loading={submitting} onClick={() => handle('clarifying')}>提交澄清</Button>
          </>
        ) : (
          <Button type="primary" loading={submitting} onClick={() => handle()}>保存</Button>
        )}
        <Button onClick={onCancel}>取消</Button>
      </div>

      <div style={{ display: 'flex', gap: 12, flex: 1, minHeight: 0, marginTop: 12 }}>
        {view !== '预览' && (
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6, minHeight: 0 }}>
            <Text type="secondary">描述(Markdown)</Text>
            <TextArea
              value={descriptionMd}
              onChange={(e) => setDescriptionMd(e.target.value)}
              onKeyDown={(e) => handleMarkdownTabIndent(e, descriptionMd, setDescriptionMd)}
              placeholder="原始需求描述"
              style={{ height: 140, fontFamily: 'monospace' }}
            />
            <Text type="secondary">
              结构化需求(JSON)
              {parseError && <Text type="danger"> · {parseError}</Text>}
            </Text>
            <TextArea
              value={structuredText}
              onChange={(e) => setStructuredText(e.target.value)}
              style={{ flex: 1, minHeight: 200, fontFamily: 'monospace' }}
            />
          </div>
        )}
        {view !== '编辑' && (
          <div style={{ flex: 1, overflow: 'auto', border: '1px solid #f0f0f0', borderRadius: 8, padding: '12px 20px' }}>
            {descriptionMd && (
              <div style={{ marginBottom: 16 }}>
                <Text type="secondary">描述预览</Text>
                <MarkdownView content={descriptionMd} />
              </div>
            )}
            <Text type="secondary">结构化预览</Text>
            {parsedStructured ? (
              <StructuredView structured={parsedStructured} />
            ) : (
              <Text type="danger">{parseError}</Text>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
