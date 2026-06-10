// 数据访问层:知识库接口
import { request } from './client'
import type { WikiPageItem } from '../types'

export interface WikiInput {
  title: string
  path: string
  parentPath?: string
  content?: string
  tags?: string[]
}

export function listWiki(): Promise<WikiPageItem[]> {
  return request<WikiPageItem[]>('/wiki/pages')
}

export function getWiki(id: string): Promise<WikiPageItem> {
  return request<WikiPageItem>(`/wiki/pages/${id}`)
}

export function searchWiki(q: string): Promise<WikiPageItem[]> {
  return request<WikiPageItem[]>(`/wiki/search?q=${encodeURIComponent(q)}`)
}

export function createWiki(input: WikiInput): Promise<WikiPageItem> {
  return request<WikiPageItem>('/wiki/pages', { method: 'POST', body: JSON.stringify(input) })
}

export function updateWiki(id: string, input: Partial<WikiInput>): Promise<WikiPageItem> {
  return request<WikiPageItem>(`/wiki/pages/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}
