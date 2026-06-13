// 逻辑层:知识库列表 / 搜索 / 筛选 / 选中(编辑保存逻辑见 useWikiEditor)
import { useCallback, useEffect, useState } from 'react'
import { listWiki, searchWiki } from '../api/wiki'
import type { WikiPageItem } from '../types'

export function useWiki() {
  const [pages, setPages] = useState<WikiPageItem[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<string | undefined>(undefined)

  const run = useCallback(async (q: string, cat?: string) => {
    setLoading(true)
    try {
      if (!q.trim() && !cat) {
        setPages(await listWiki())
      } else {
        setPages(await searchWiki(q, { category: cat }))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    run(query, category)
  }, [run, query, category])

  const search = (q: string) => setQuery(q)
  const filterCategory = (cat?: string) => setCategory(cat)

  const selected = pages.find((p) => p.id === selectedId) ?? null

  return { pages, loading, selected, selectedId, setSelectedId, search, category, filterCategory, reload: () => run(query, category) }
}
