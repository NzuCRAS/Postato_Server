// 逻辑层:全局鉴权状态。视图通过 useAuth() 消费,不接触 token/接口细节。
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { clearToken, getToken, setToken } from '../api/client'
import { login as loginApi } from '../api/auth'
import { getMe } from '../api/user'
import type { User } from '../types'

interface AuthContextValue {
  user: User | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = async (): Promise<void> => {
    if (!getToken()) {
      setUser(null)
      return
    }
    try {
      setUser(await getMe())
    } catch {
      clearToken()
      setUser(null)
    }
  }

  useEffect(() => {
    refresh().finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = async (username: string, password: string): Promise<void> => {
    const res = await loginApi(username, password)
    setToken(res.token)
    await refresh()
  }

  const logout = (): void => {
    clearToken()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth 必须在 AuthProvider 内使用')
  return ctx
}
