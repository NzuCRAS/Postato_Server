// 数据访问层:权限规则 + 字典(职能/资源/动作)接口(admin)
import { request } from './client'
import type { PermissionRuleItem, PermissionDefItem } from '../types'

// ---- 规则 ----

export function listRules(): Promise<PermissionRuleItem[]> {
  return request<PermissionRuleItem[]>('/permission-rules')
}

export function createRule(input: { resource: string; action: string; requiredFunctions: string[] }): Promise<PermissionRuleItem> {
  return request<PermissionRuleItem>('/permission-rules', { method: 'POST', body: JSON.stringify(input) })
}

export function updateRule(id: string, requiredFunctions: string[]): Promise<PermissionRuleItem> {
  return request<PermissionRuleItem>(`/permission-rules/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify({ requiredFunctions }),
  })
}

export function deleteRule(id: string): Promise<void> {
  return request<void>(`/permission-rules/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

// ---- 字典(职能 functions / 资源 resources / 动作 actions)----

export type DictType = 'functions' | 'resources' | 'actions'

export function listDefs(type: DictType): Promise<PermissionDefItem[]> {
  return request<PermissionDefItem[]>(`/permission/${type}`)
}

export function createDef(type: DictType, input: { key: string; label?: string; description?: string }): Promise<PermissionDefItem> {
  return request<PermissionDefItem>(`/permission/${type}`, { method: 'POST', body: JSON.stringify(input) })
}

export function updateDef(type: DictType, key: string, input: { label?: string; description?: string }): Promise<PermissionDefItem> {
  return request<PermissionDefItem>(`/permission/${type}/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function deleteDef(type: DictType, key: string): Promise<void> {
  return request<void>(`/permission/${type}/${encodeURIComponent(key)}`, { method: 'DELETE' })
}
