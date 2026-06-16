// 逻辑层:架构图谱加载 + mermaid 拼接(纯函数)
import { useCallback, useEffect, useState } from 'react'
import { getArchGraph } from '../api/archGraph'
import type { ArchGraph } from '../types'

export function useArchGraph(projectId: string) {
  const [graph, setGraph] = useState<ArchGraph | null>(null)
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setGraph(await getArchGraph(projectId))
    } finally {
      setLoading(false)
    }
  }, [projectId])

  useEffect(() => {
    reload()
  }, [reload])

  return { graph, loading, reload }
}

/** mermaid 节点 id:模块 key 清洗为合法标识。 */
function mid(key: string): string {
  return 'm_' + key.replace(/[^a-zA-Z0-9_]/g, '_')
}

/** 把图(模块 + 边)拼成 mermaid flowchart,按 group 聚成 subgraph。 */
export function archToMermaid(g: ArchGraph | null): string {
  const modules = g?.modules ?? []
  const edges = g?.edges ?? []
  if (modules.length === 0) return 'flowchart TD\n  empty["(暂无模块,去用 MCP upsert_arch_module 声明)"]'
  const lines: string[] = ['flowchart TD']
  const groups = new Map<string, typeof modules>()
  for (const m of modules) {
    const grp = m.group || '未分组'
    if (!groups.has(grp)) groups.set(grp, [])
    groups.get(grp)!.push(m)
  }
  let gi = 0
  for (const [grp, mods] of groups) {
    lines.push(`  subgraph g${gi}["${grp}"]`)
    for (const m of mods) lines.push(`    ${mid(m.key)}["${m.title ?? m.key}"]`)
    lines.push('  end')
    gi++
  }
  for (const e of edges) {
    const lbl = e.label || e.kind || ''
    lines.push(lbl ? `  ${mid(e.from)} -->|${lbl}| ${mid(e.to)}` : `  ${mid(e.from)} --> ${mid(e.to)}`)
  }
  return lines.join('\n')
}
