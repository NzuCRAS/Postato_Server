// 逻辑层:知识库文档编辑(加载 + 保存)
import { useEffect, useState } from 'react'
import { createWiki, getWiki, updateWiki, type WikiInput } from '../api/wiki'
import type { WikiPageItem } from '../types'

const EMPTY: WikiInput = { title: '', path: '', parentPath: undefined, content: '', tags: [] }

export function useWikiEditor(id?: string) {
  const [form, setForm] = useState<WikiInput | null>(id ? null : { ...EMPTY })
  const [loading, setLoading] = useState(!!id)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    getWiki(id)
      .then((p: WikiPageItem) =>
        setForm({ title: p.title, path: p.path, parentPath: p.parentPath, content: p.content, tags: p.tags }),
      )
      .finally(() => setLoading(false))
  }, [id])

  const save = async (): Promise<WikiPageItem> => {
    if (!form) throw new Error('表单未就绪')
    setSaving(true)
    try {
      return id ? await updateWiki(id, form) : await createWiki(form)
    } finally {
      setSaving(false)
    }
  }

  return { form, setForm, loading, saving, save }
}
