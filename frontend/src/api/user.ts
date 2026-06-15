// 数据访问层:用户与 API Key 接口
import { request } from './client'
import type { User, CreatedApiKey, UserAdminItem } from '../types'

export function getMe(): Promise<User> {
  return request<User>('/users/me')
}

// ---- admin 用户管理 ----

export function listUsers(): Promise<UserAdminItem[]> {
  return request<UserAdminItem[]>('/users')
}

export function createUser(input: { username: string; password: string; functions: string[] }): Promise<UserAdminItem> {
  return request<UserAdminItem>('/users', { method: 'POST', body: JSON.stringify(input) })
}

export function updateUserFunctions(id: string, functions: string[]): Promise<UserAdminItem> {
  return request<UserAdminItem>(`/users/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify({ functions }),
  })
}

export function resetUserPassword(id: string, password: string): Promise<void> {
  return request<void>(`/users/${encodeURIComponent(id)}/password`, {
    method: 'PUT',
    body: JSON.stringify({ password }),
  })
}

export function deleteUser(id: string): Promise<void> {
  return request<void>(`/users/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/** 自助修改自己密码(验旧密码)。 */
export function changeMyPassword(oldPassword: string, newPassword: string): Promise<void> {
  return request<void>('/users/me/password', {
    method: 'PUT',
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}

// ---- 自助 API Key ----

export function createApiKey(name: string): Promise<CreatedApiKey> {
  return request<CreatedApiKey>('/users/me/api-keys', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function deleteApiKey(id: string): Promise<void> {
  return request<void>(`/users/me/api-keys/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  })
}
