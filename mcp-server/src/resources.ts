import { ResourceTemplate } from '@modelcontextprotocol/sdk/server/mcp.js'
import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { backendRequest } from './api-client.js'

/**
 * 注册只读资源。把知识库里"开工必读"的规范/协议(category=standard,含 agent 协议)
 * 暴露成 MCP resources,并支持按 potato://wiki/<path> 读任意 wiki 页。
 * apiKey 来自当前 /mcp 请求,闭包透传给后端(与 tool 同一鉴权链路)。
 *
 * 定位:resources 管"稳定必读只读暴露"(客户端/人 attach),search_knowledge tool 管"按需检索",互补。
 */
const PREFIX = 'potato://wiki/'

/** wiki 物化路径(/a/b)→ 资源 uri(potato://wiki/a/b)。 */
function toUri(path: string): string {
  return PREFIX + String(path ?? '').replace(/^\/+/, '')
}

/** 资源 uri / 模板变量 → wiki 物化路径(补前导 /)。 */
function toPath(uriHref: string, pathVar: string | string[] | undefined): string {
  const raw = Array.isArray(pathVar) ? pathVar[0] : pathVar
  const p = raw ?? uriHref.replace(/^potato:\/\/wiki\//, '')
  return '/' + String(p).replace(/^\/+/, '')
}

export function registerResources(server: McpServer, apiKey: string | undefined): void {
  const template = new ResourceTemplate(PREFIX + '{+path}', {
    // 列出开工必读规范(standard 分类;agent 协议本就是 standard,自动包含)
    list: async () => {
      try {
        const pages = await backendRequest<Array<Record<string, any>>>('/wiki/search?category=standard', apiKey)
        return {
          resources: pages.map((p) => ({
            uri: toUri(p.path),
            name: String(p.title ?? p.path),
            description: `[standard] ${p.path}`,
            mimeType: 'text/markdown',
          })),
        }
      } catch {
        // 列出失败(如未授权)不应让整个 resources/list 报错
        return { resources: [] }
      }
    },
  })

  server.resource(
    'wiki',
    template,
    {
      description:
        '知识库 wiki 页(只读)。列出的是开工必读的 standard 规范/agent 协议;也可按 potato://wiki/<path> 读任意页。内容带 frontmatter(title/path/category/version)。',
      mimeType: 'text/markdown',
    },
    async (uri, variables) => {
      const path = toPath(uri.href, variables.path as string | string[] | undefined)
      const p = await backendRequest<Record<string, any>>(
        `/wiki/by-path?path=${encodeURIComponent(path)}`,
        apiKey,
      )
      const frontmatter = [
        '---',
        `title: ${p.title ?? ''}`,
        `path: ${p.path ?? path}`,
        `category: ${p.category ?? 'doc'}`,
        `version: ${p.version ?? ''}`,
        '---',
        '',
      ].join('\n')
      return {
        contents: [{ uri: uri.href, mimeType: 'text/markdown', text: frontmatter + (p.content ?? '') }],
      }
    },
  )

  // ---- 架构图谱:总览(mermaid 依赖图)+ 模块档案 ----
  server.resource(
    'arch-overview',
    'potato://arch/overview',
    { description: '项目架构总览:模块依赖图(mermaid flowchart,按 group 聚类)。一眼看懂谁依赖谁。', mimeType: 'text/markdown' },
    async (uri) => {
      const g = await backendRequest<Record<string, any>>('/arch-graph?projectId=default', apiKey)
      const text = '# 架构总览(模块依赖图)\n\n```mermaid\n' + archToMermaid(g) + '\n```\n'
      return { contents: [{ uri: uri.href, mimeType: 'text/markdown', text }] }
    },
  )

  server.resource(
    'arch-module',
    new ResourceTemplate('potato://arch/module/{key}', {
      list: async () => {
        try {
          const g = await backendRequest<Record<string, any>>('/arch-graph?projectId=default', apiKey)
          return {
            resources: ((g?.modules ?? []) as Array<Record<string, any>>).map((m) => ({
              uri: `potato://arch/module/${m.key}`,
              name: `${m.title ?? m.key}(模块档案)`,
              description: m.group ? `[${m.group}]` : undefined,
              mimeType: 'text/markdown',
            })),
          }
        } catch {
          return { resources: [] }
        }
      },
    }),
    { description: '模块档案:依赖(入/出边)+ 需求/技术/经验索引(跨模块标 scope)+ 代码 + 状态。', mimeType: 'text/markdown' },
    async (uri, variables) => {
      const key = Array.isArray(variables.key) ? variables.key[0] : variables.key
      const g = await backendRequest<Record<string, any>>('/arch-graph?projectId=default', apiKey)
      return { contents: [{ uri: uri.href, mimeType: 'text/markdown', text: moduleDossier(g, String(key)) }] }
    },
  )
}

/** mermaid 节点 id:模块 key 清洗为合法 id。 */
function mid(key: string): string {
  return 'm_' + String(key).replace(/[^a-zA-Z0-9_]/g, '_')
}

/** 把图(模块+边)拼成 mermaid flowchart,按 group 聚成 subgraph。 */
function archToMermaid(g: Record<string, any>): string {
  const modules = (g?.modules ?? []) as Array<Record<string, any>>
  const edges = (g?.edges ?? []) as Array<Record<string, any>>
  if (modules.length === 0) return 'flowchart TD\n  empty["(暂无模块)"]'
  const lines: string[] = ['flowchart TD']
  const groups = new Map<string, Array<Record<string, any>>>()
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
    lines.push(`  ${mid(e.from)} -->|${lbl}| ${mid(e.to)}`)
  }
  return lines.join('\n')
}

/** 模块档案 markdown:依赖 + 三类索引(跨模块标 scope)+ 代码 + 状态。 */
function moduleDossier(g: Record<string, any>, key: string): string {
  const modules = (g?.modules ?? []) as Array<Record<string, any>>
  const edges = (g?.edges ?? []) as Array<Record<string, any>>
  const m = modules.find((x) => x.key === key)
  if (!m) return `# 模块未找到: ${key}`
  const out = edges.filter((e) => e.from === key)
  const inc = edges.filter((e) => e.to === key)
  const docs = (m.docs ?? []) as Array<Record<string, any>>
  const fmtDoc = (d: Record<string, any>) => {
    const cross = Array.isArray(d.scope) && d.scope.length > 1 ? `(跨 ${d.scope.join('↔')})` : ''
    return `- ${d.ref}${d.title ? ' — ' + d.title : ''} ${cross}`.trimEnd()
  }
  const section = (t: string) => {
    const list = docs.filter((d) => d.type === t).map(fmtDoc)
    return list.length ? list : ['- 无']
  }
  const code = (m.related_code ?? []) as string[]
  return [
    `# ${m.title ?? key}(${key})`,
    '',
    `- group: ${m.group ?? '—'} | impl_status: ${m.impl_status ?? 'planned'}`,
    m.description ? `- ${m.description}` : '',
    '',
    '## 依赖',
    `- 出边(依赖):${out.length ? out.map((e) => `${e.to}(${e.kind})`).join('、') : '无'}`,
    `- 入边(被依赖):${inc.length ? inc.map((e) => `${e.from}(${e.kind})`).join('、') : '无'}`,
    '',
    '## 需求',
    ...section('requirement'),
    '',
    '## 技术说明',
    ...section('tech_doc'),
    '',
    '## 经验',
    ...section('experience'),
    '',
    '## related_code',
    ...(code.length ? code.map((c) => `- ${c}`) : ['- 无']),
  ].join('\n')
}
