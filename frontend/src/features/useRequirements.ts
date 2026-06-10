// 逻辑层:需求列表
import { useCallback, useEffect, useState } from 'react'
import { listRequirements } from '../api/requirement'
import type { RequirementSummary } from '../types'

export function useRequirements(status?: string, projectId?: string) {
  const [items, setItems] = useState<RequirementSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setItems(await listRequirements(status, projectId))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [status, projectId])

  useEffect(() => {
    load()
  }, [load])

  return { items, loading, error, reload: load }
}
