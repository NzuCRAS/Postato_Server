// 逻辑层:admin 用户管理(列表 / 增删改职能 / 重置密码)+ 已知职能聚合
import { useCallback, useEffect, useMemo, useState } from 'react'
import { listUsers, createUser, updateUserFunctions, resetUserPassword, deleteUser } from '../api/user'
import type { UserAdminItem } from '../types'

const BASE_FUNCTIONS = ['admin', 'product', 'development', 'testing']

export function useUsers() {
  const [users, setUsers] = useState<UserAdminItem[]>([])
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setUsers(await listUsers())
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  // 职能开放可扩展:候选 = 基础职能 ∪ 现有用户已用职能
  const knownFunctions = useMemo(() => {
    const set = new Set<string>(BASE_FUNCTIONS)
    users.forEach((u) => u.functions?.forEach((f) => set.add(f)))
    return Array.from(set)
  }, [users])

  return {
    users,
    loading,
    knownFunctions,
    reload,
    createUser,
    updateUserFunctions,
    resetUserPassword,
    deleteUser,
  }
}
