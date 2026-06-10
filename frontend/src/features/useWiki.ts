// 逻辑层:知识库列表 / 搜索 / 选中(编辑保存逻辑见 useWikiEditor)
import { useCallback, useEffect, useState } from 'react'
import { listWiki, searchWiki } from '../api/wiki'
import type { WikiPageItem } from '../types'

export function useWiki() {
  const [pages, setPages] = useState<WikiPageItem[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setPages(await listWiki())
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const search = async (q: string): Promise<void> => {
    setLoading(true)
    try {
      setPages(q.trim() ? await searchWiki(q) : await listWiki())
    } finally {
      setLoading(false)
    }
  }

  const selected = pages.find((p) => p.id === selectedId) ?? null

  return { pages, loading, selected, selectedId, setSelectedId, search, reload: load }
}
