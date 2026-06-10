// 数据访问层:需求接口
import { request } from './client'
import type { Requirement, RequirementSummary, Structured } from '../types'

export interface RequirementInput {
  title?: string
  descriptionMd?: string
  structured?: Structured
  status?: string
}

export function listRequirements(status?: string): Promise<RequirementSummary[]> {
  const q = status ? `?status=${encodeURIComponent(status)}` : ''
  return request<RequirementSummary[]>(`/requirements${q}`)
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
