// 后端 API 调用封装。透传 Claude Code 传来的 API Key,统一错误处理。
const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080/api/v1'

export class BackendError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'BackendError'
  }
}

export async function backendRequest<T = unknown>(
  path: string,
  apiKey: string | undefined,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers)
  if (apiKey) headers.set('Authorization', `Bearer ${apiKey}`)
  if (init.body) headers.set('Content-Type', 'application/json')

  const res = await fetch(`${BACKEND_URL}${path}`, { ...init, headers })
  if (!res.ok) {
    let message = `后端返回 ${res.status}`
    try {
      const data = (await res.json()) as Record<string, unknown>
      message = (data.message as string) ?? (data.error as string) ?? message
    } catch {
      /* 响应非 JSON */
    }
    throw new BackendError(res.status, message)
  }
  if (res.status === 204) return null as T
  return (await res.json()) as T
}
