// 数据访问层:统一 HTTP 封装。注入 token、统一错误处理。
// 视图/逻辑层不直接 fetch,一律走这里。

const TOKEN_KEY = 'potato_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers = new Headers(options.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const res = await fetch(`/api/v1${path}`, { ...options, headers })

  if (!res.ok) {
    let message = `请求失败 (${res.status})`
    try {
      const data = await res.json()
      message = data.message ?? data.error ?? message
    } catch {
      // 响应非 JSON,沿用默认消息
    }
    throw new ApiError(res.status, message)
  }

  if (res.status === 204) return undefined as T
  // 鲁棒处理空 body(如 200 无内容),避免对空响应调用 JSON 解析报错
  const text = await res.text()
  return (text ? (JSON.parse(text) as T) : (undefined as T))
}
