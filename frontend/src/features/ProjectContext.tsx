// 逻辑层:项目上下文 —— 当前项目、项目列表、切换(localStorage 持久化)。
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { listProjects } from '../api/project'
import { useAuth } from '../auth/AuthContext'
import type { Project } from '../types'

interface ProjectCtxValue {
  projects: Project[]
  current: Project | null
  currentId: string | null
  setCurrentId: (id: string) => void
  reload: () => Promise<void>
}

const Ctx = createContext<ProjectCtxValue | null>(null)
const LS_KEY = 'potato.currentProjectId'

export function ProjectProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [projects, setProjects] = useState<Project[]>([])
  const [currentId, setCurrentIdState] = useState<string | null>(() => localStorage.getItem(LS_KEY))

  const reload = useCallback(async () => {
    let list: Project[] = []
    try {
      list = await listProjects()
    } catch {
      list = []
    }
    setProjects(list)
    setCurrentIdState((cur) => {
      if (cur && list.some((p) => p.id === cur)) return cur
      const next = list[0]?.id ?? null
      if (next) localStorage.setItem(LS_KEY, next)
      return next
    })
  }, [])

  useEffect(() => {
    if (user) void reload()
  }, [user, reload])

  const setCurrentId = (id: string) => {
    localStorage.setItem(LS_KEY, id)
    setCurrentIdState(id)
  }

  const current = projects.find((p) => p.id === currentId) ?? null

  return (
    <Ctx.Provider value={{ projects, current, currentId, setCurrentId, reload }}>{children}</Ctx.Provider>
  )
}

export function useProjects(): ProjectCtxValue {
  const c = useContext(Ctx)
  if (!c) throw new Error('useProjects must be used within ProjectProvider')
  return c
}
