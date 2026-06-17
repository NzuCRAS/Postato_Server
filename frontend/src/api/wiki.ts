// 数据访问层:知识库接口
import { request } from './client'
import type { WikiPageItem } from '../types'

export interface WikiInput {
  title: string
  path: string
  parentPath?: string
  content?: string
  category?: string // doc | asset | standard | experience
  kind?: string // folder | doc
  tags?: string[]
}

export interface SearchOpts {
  category?: string
  matchMode?: string
  includeTmp?: boolean
}

export function listWiki(): Promise<WikiPageItem[]> {
  return request<WikiPageItem[]>('/wiki/pages')
}

export function getWiki(id: string): Promise<WikiPageItem> {
  return request<WikiPageItem>(`/wiki/pages/${id}`)
}

export function searchWiki(q: string, opts: SearchOpts = {}): Promise<WikiPageItem[]> {
  const qs = new URLSearchParams()
  if (q) qs.set('q', q)
  if (opts.category) qs.set('category', opts.category)
  if (opts.matchMode) qs.set('match_mode', opts.matchMode)
  if (opts.includeTmp) qs.set('include_tmp', 'true')
  return request<WikiPageItem[]>(`/wiki/search?${qs.toString()}`)
}

export function createWiki(input: WikiInput): Promise<WikiPageItem> {
  return request<WikiPageItem>('/wiki/pages', { method: 'POST', body: JSON.stringify(input) })
}

export function updateWiki(id: string, input: Partial<WikiInput>): Promise<WikiPageItem> {
  return request<WikiPageItem>(`/wiki/pages/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function uploadAsset(id: string, file: File): Promise<WikiPageItem> {
  const fd = new FormData()
  fd.append('file', file)
  return request<WikiPageItem>(`/wiki/pages/${id}/assets`, { method: 'POST', body: fd })
}

export function deleteAsset(id: string, objectKey: string): Promise<WikiPageItem> {
  return request<WikiPageItem>(`/wiki/pages/${id}/assets?objectKey=${encodeURIComponent(objectKey)}`, {
    method: 'DELETE',
  })
}

/** 删除整页(后端连带删其 MinIO 资产)。 */
export function deleteWiki(id: string): Promise<void> {
  return request<void>(`/wiki/pages/${id}`, { method: 'DELETE' })
}

/** 删除整目录:级联删该前缀下所有文档(目录本身 + 子文档)。返回被删的页列表。 */
export function deleteDir(prefix: string): Promise<WikiPageItem[]> {
  return request<WikiPageItem[]>(`/wiki/dir?prefix=${encodeURIComponent(prefix)}`, { method: 'DELETE' })
}

/** 整目录移动/重命名:把 fromPrefix 子树整体迁到 toPrefix(后端级联改路径前缀)。 */
export function moveDir(fromPrefix: string, toPrefix: string): Promise<WikiPageItem[]> {
  return request<WikiPageItem[]>('/wiki/move-dir', {
    method: 'POST',
    body: JSON.stringify({ fromPrefix, toPrefix }),
  })
}
