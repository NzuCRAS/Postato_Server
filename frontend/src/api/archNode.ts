// 数据访问层:项目结构树(arch_nodes)接口
import { request } from './client'
import type { ArchNode } from '../types'

export function listArch(pid: string, opts?: { tag?: string; layer?: string }): Promise<ArchNode[]> {
  const qs = new URLSearchParams()
  if (opts?.tag) qs.set('tag', opts.tag)
  if (opts?.layer) qs.set('layer', opts.layer)
  return request<ArchNode[]>(`/projects/${pid}/arch?${qs.toString()}`)
}

export interface ArchNodeInput {
  parent_id?: string
  title: string
  layer?: string
  type?: string
  description?: string
  tags?: string[]
  related_docs?: string[]
  related_code?: string[]
  related_requirements?: string[]
}

export function createArchNode(pid: string, body: ArchNodeInput): Promise<ArchNode> {
  return request<ArchNode>(`/projects/${pid}/arch/nodes`, { method: 'POST', body: JSON.stringify(body) })
}

export function updateArchNode(pid: string, nodeId: string, body: Partial<ArchNodeInput>): Promise<ArchNode> {
  return request<ArchNode>(`/projects/${pid}/arch/nodes/${nodeId}`, { method: 'PATCH', body: JSON.stringify(body) })
}

export function archiveArchNode(pid: string, nodeId: string): Promise<{ archived: number }> {
  return request<{ archived: number }>(`/projects/${pid}/arch/nodes/${nodeId}/archive`, { method: 'POST' })
}

export function moveArchNode(pid: string, nodeId: string, newParentId: string | null): Promise<ArchNode> {
  return request<ArchNode>(`/projects/${pid}/arch/nodes/${nodeId}/move`, {
    method: 'POST',
    body: JSON.stringify({ new_parent_id: newParentId }),
  })
}
