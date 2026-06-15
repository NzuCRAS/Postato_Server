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
      // 职能受控于职能字典(权限管理中心维护),用户管理只从中选
      const [u, f] = await Promise.all([listUsers(), listDefs('functions')])
      setUsers(u)
      setFunctions(f)
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
