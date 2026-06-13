// 逻辑层:知识库文档编辑(加载 + 保存 + 资产)
import { useEffect, useState } from 'react'
import { createWiki, deleteAsset, getWiki, updateWiki, uploadAsset, type WikiInput } from '../api/wiki'
import type { WikiAsset, WikiPageItem } from '../types'

const EMPTY: WikiInput = { title: '', path: '', parentPath: undefined, content: '', category: 'doc', tags: [] }

export function useWikiEditor(id?: string, initialPath?: string) {
  const [form, setForm] = useState<WikiInput | null>(id ? null : { ...EMPTY, path: initialPath ?? '' })
  const [assets, setAssets] = useState<WikiAsset[]>([])
  const [loading, setLoading] = useState(!!id)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    getWiki(id)
      .then((p: WikiPageItem) => {
        setForm({
          title: p.title,
          path: p.path,
          parentPath: p.parentPath,
          content: p.content,
          category: p.category ?? 'doc',
          tags: p.tags,
        })
        setAssets(p.assets ?? [])
      })
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

  const upload = async (file: File): Promise<void> => {
    if (!id) throw new Error('请先保存文档,再上传资产')
    const updated = await uploadAsset(id, file)
    setAssets(updated.assets ?? [])
  }

  const removeAsset = async (objectKey: string): Promise<void> => {
    if (!id) return
    const updated = await deleteAsset(id, objectKey)
    setAssets(updated.assets ?? [])
  }

  return { form, setForm, assets, loading, saving, save, upload, removeAsset }
}
