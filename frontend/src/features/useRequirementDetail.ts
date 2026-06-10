// 逻辑层:需求详情 + 状态流转
import { useCallback, useEffect, useState } from 'react'
import { getRequirement, updateRequirementStatus } from '../api/requirement'
import type { Requirement } from '../types'

export function useRequirementDetail(id: string) {
  const [data, setData] = useState<Requirement | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(
    async (silent = false) => {
      if (!id) return
      if (!silent) setLoading(true)
      setError(null)
      try {
        setData(await getRequirement(id))
      } catch (e) {
        setError(e instanceof Error ? e.message : '加载失败')
      } finally {
        if (!silent) setLoading(false)
      }
    },
    [id],
  )

  useEffect(() => {
    load()
  }, [load])

  // 静默刷新:不触发整页 loading,避免进度 Tab / 树展开态被重置
  const reload = useCallback(() => load(true), [load])

  const changeStatus = async (status: string): Promise<void> => {
    const updated = await updateRequirementStatus(id, status)
    setData(updated)
  }

  return { data, loading, error, reload, changeStatus }
}
