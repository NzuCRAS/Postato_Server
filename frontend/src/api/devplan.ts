// 数据访问层:开发进度树接口
import { request } from './client'
import type {
  AcceptanceItem,
  DevPlan,
  DevPlanArtifacts,
  DevPlanCommit,
  DevPlanCorrection,
  DevPlanNode,
  DevPlanRepo,
} from '../types'

export interface NodeInput {
  title: string
  description?: string
  module_ref?: string
  acceptance_criteria?: string[]
  related_docs?: string[]
  children?: NodeInput[]
}

export function createDevPlan(
  reqId: string,
  nodes: NodeInput[],
  opts?: { rootTitle?: string; repo?: DevPlanRepo },
): Promise<DevPlan> {
  return request<DevPlan>(`/requirements/${reqId}/dev-plan`, {
    method: 'POST',
    body: JSON.stringify({ root_title: opts?.rootTitle, repo: opts?.repo, nodes }),
  })
}

export interface NodeUpdate {
  status?: string
  artifacts?: DevPlanArtifacts
  commit?: DevPlanCommit
  log_message?: string
  log_detail?: string
  blocked_reason?: string
  acceptance_criteria?: AcceptanceItem[]
}

export interface UpdateNodeResponse {
  node: DevPlanNode
  warnings: string[]
}

export function updateDevPlanNode(
  reqId: string,
  nodeId: string,
  updates: NodeUpdate,
): Promise<UpdateNodeResponse> {
  return request<UpdateNodeResponse>(`/requirements/${reqId}/dev-plan/nodes/${nodeId}`, {
    method: 'PATCH',
    body: JSON.stringify(updates),
  })
}

export function addCorrection(
  reqId: string,
  nodeId: string,
  message: string,
): Promise<DevPlanCorrection> {
  return request<DevPlanCorrection>(`/requirements/${reqId}/dev-plan/nodes/${nodeId}/corrections`, {
    method: 'POST',
    body: JSON.stringify({ message }),
  })
}

export function resolveCorrection(
  reqId: string,
  nodeId: string,
  correctionId: string,
): Promise<DevPlanCorrection> {
  return request<DevPlanCorrection>(
    `/requirements/${reqId}/dev-plan/nodes/${nodeId}/corrections/${correctionId}`,
    { method: 'PATCH' },
  )
}
