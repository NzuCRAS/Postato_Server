// 数据访问层:认证相关接口
import { request } from './client'
import type { LoginResponse } from '../types'

export function login(username: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}
