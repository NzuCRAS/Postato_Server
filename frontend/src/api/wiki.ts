// 数据访问层:知识库接口
import { request } from './client'
import type { WikiPageItem } from '../types'

export interface WikiInput {
  title: string
  path: string
  parentPath?: string
  content?: string
  category?: string // doc | asset | standard | experience
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
