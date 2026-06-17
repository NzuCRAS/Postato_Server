// 逻辑层:知识库列表 / 搜索 / 筛选 / 选中(编辑保存逻辑见 useWikiEditor)
import { useCallback, useEffect, useRef, useState } from 'react'
import { listWiki, searchWiki } from '../api/wiki'
import type { WikiPageItem } from '../types'

/** initialPath:外部(如项目文档链接)带 ?path= 进来时,pages 加载后按 path 自动选中(只应用一次)。 */
export function useWiki(initialPath?: string) {
  const [pages, setPages] = useState<WikiPageItem[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<string | undefined>(undefined)
  const appliedPathRef = useRef(false)

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

  // ?path= 自动选中:pages 到位后匹配一次,避免覆盖用户后续手动选择
  useEffect(() => {
    if (appliedPathRef.current || !initialPath || loading) return
    const match = pages.find((p) => p.path === initialPath)
    if (match) {
      setSelectedId(match.id)
      appliedPathRef.current = true
    }
  }, [pages, initialPath, loading])

  const search = (q: string) => setQuery(q)
  const filterCategory = (cat?: string) => setCategory(cat)

  const selected = pages.find((p) => p.id === selectedId) ?? null

  return { pages, loading, selected, selectedId, setSelectedId, search, category, filterCategory, reload: () => run(query, category) }
}
