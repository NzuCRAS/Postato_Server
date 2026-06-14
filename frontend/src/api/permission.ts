// 数据访问层:权限规则接口(admin)
import { request } from './client'
import type { PermissionRuleItem } from '../types'

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
