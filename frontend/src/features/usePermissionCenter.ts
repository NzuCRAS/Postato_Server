// 逻辑层:权限管理中心聚合(规则 + 职能/资源/动作三字典一次加载,供 Tab 四子页共用)
import { useCallback, useEffect, useState } from 'react'
import { listRules, listDefs } from '../api/permission'
import type { PermissionRuleItem, PermissionDefItem } from '../types'

export function usePermissionCenter() {
  const [rules, setRules] = useState<PermissionRuleItem[]>([])
  const [functions, setFunctions] = useState<PermissionDefItem[]>([])
  const [resources, setResources] = useState<PermissionDefItem[]>([])
  const [actions, setActions] = useState<PermissionDefItem[]>([])
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const [r, f, res, act] = await Promise.all([
        listRules(),
        listDefs('functions'),
        listDefs('resources'),
        listDefs('actions'),
      ])
      r.sort((a, b) => a.resource.localeCompare(b.resource) || a.action.localeCompare(b.action))
      setRules(r)
      setFunctions(f)
      setResources(res)
      setActions(act)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  return { rules, functions, resources, actions, loading, reload }
}
