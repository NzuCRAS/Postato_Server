// 逻辑层:admin 用户管理(列表 / 增删改职能 / 重置密码)+ 职能字典(受控下拉候选)
import { useCallback, useEffect, useState } from 'react'
import { listUsers, createUser, updateUserFunctions, resetUserPassword, deleteUser } from '../api/user'
import { listDefs } from '../api/permission'
import type { UserAdminItem, PermissionDefItem } from '../types'

export function useUsers() {
  const [users, setUsers] = useState<UserAdminItem[]>([])
  const [functions, setFunctions] = useState<PermissionDefItem[]>([])
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      // 用户列表人人可调(非 admin 只返自己);职能字典仅 admin 有权,故单独容错
      setUsers(await listUsers())
      try {
        setFunctions(await listDefs('functions'))
      } catch {
        setFunctions([])
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  return {
    users,
    functions,
    loading,
    reload,
    createUser,
    updateUserFunctions,
    resetUserPassword,
    deleteUser,
  }
}
