// 逻辑层:权限规则管理(列表 / 增删改)+ 已知职能聚合
import { useCallback, useEffect, useMemo, useState } from 'react'
import { listRules, createRule, updateRule, deleteRule } from '../api/permission'
import type { PermissionRuleItem } from '../types'

const BASE_FUNCTIONS = ['admin', 'product', 'development', 'testing']

export function usePermissions() {
  const [rules, setRules] = useState<PermissionRuleItem[]>([])
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const list = await listRules()
      // 按 resource、action 排序,便于可视化分组
      list.sort((a, b) => a.resource.localeCompare(b.resource) || a.action.localeCompare(b.action))
      setRules(list)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  const knownFunctions = useMemo(() => {
    const set = new Set<string>(BASE_FUNCTIONS)
    rules.forEach((r) => r.requiredFunctions?.forEach((f) => set.add(f)))
    return Array.from(set)
  }, [rules])

  return { rules, loading, knownFunctions, reload, createRule, updateRule, deleteRule }
}
