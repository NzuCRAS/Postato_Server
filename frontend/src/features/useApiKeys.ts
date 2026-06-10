// 逻辑层:API Key 管理。封装生成/删除/刷新,视图只调方法、读状态。
import { useState } from 'react'
import { createApiKey, deleteApiKey } from '../api/user'
import { useAuth } from '../auth/AuthContext'
import type { CreatedApiKey } from '../types'

export function useApiKeys() {
  const { user, refresh } = useAuth()
  const [creating, setCreating] = useState(false)
  const [newKey, setNewKey] = useState<CreatedApiKey | null>(null)

  const create = async (name: string): Promise<void> => {
    setCreating(true)
    try {
      const key = await createApiKey(name)
      setNewKey(key) // 明文 key 仅此一次,交给视图弹窗展示
      await refresh()
    } finally {
      setCreating(false)
    }
  }

  const remove = async (id: string): Promise<void> => {
    await deleteApiKey(id)
    await refresh()
  }

  return {
    apiKeys: user?.apiKeys ?? [],
    creating,
    newKey,
    clearNewKey: () => setNewKey(null),
    create,
    remove,
  }
}
