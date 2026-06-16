// 数据访问层:架构图谱(模块 + 依赖边)
import { request } from './client'
import type { ArchGraph } from '../types'

export function getArchGraph(projectId: string): Promise<ArchGraph> {
  return request<ArchGraph>(`/arch-graph?projectId=${encodeURIComponent(projectId)}`)
}
