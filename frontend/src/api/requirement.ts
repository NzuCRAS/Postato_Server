// 数据访问层:需求接口
import { request } from './client'
import type { ProjectDocLink, Requirement, RequirementSummary, Structured } from '../types'

export interface RequirementInput {
  title?: string
  descriptionMd?: string
  structured?: Structured
  status?: string
  projectId?: string
  docLinks?: ProjectDocLink[]
}

export function listRequirements(status?: string, projectId?: string): Promise<RequirementSummary[]> {
  const qs = new URLSearchParams()
  if (status) qs.set('status', status)
  if (projectId) qs.set('projectId', projectId)
  const q = qs.toString()
  return request<RequirementSummary[]>(`/requirements${q ? `?${q}` : ''}`)
}

export function getRequirement(id: string): Promise<Requirement> {
  return request<Requirement>(`/requirements/${id}`)
}

export function createRequirement(input: RequirementInput): Promise<Requirement> {
  return request<Requirement>('/requirements', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateRequirement(id: string, input: RequirementInput): Promise<Requirement> {
  return request<Requirement>(`/requirements/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function updateRequirementStatus(id: string, status: string): Promise<Requirement> {
  return request<Requirement>(`/requirements/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}
