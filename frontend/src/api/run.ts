// 数据访问层:SOP 执行工作流 Run(只读展示)
import { request } from './client'
import type { SopRunItem } from '../types'

/** 取某需求的最新 Run(只读,无则后端返回空)。 */
export function getRun(reqId: string): Promise<SopRunItem | null> {
  return request<SopRunItem | null>(`/runs?reqId=${encodeURIComponent(reqId)}`)
}
