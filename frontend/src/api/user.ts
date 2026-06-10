// 数据访问层:用户与 API Key 接口
import { request } from './client'
import type { User, CreatedApiKey } from '../types'

export function getMe(): Promise<User> {
  return request<User>('/users/me')
}

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
