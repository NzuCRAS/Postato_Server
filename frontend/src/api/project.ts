// 数据访问层:项目接口
import { request } from './client'
import type { Project, ProjectDocLink } from '../types'

export function listProjects(): Promise<Project[]> {
  return request<Project[]>('/projects')
}

export function getProject(id: string): Promise<Project> {
  return request<Project>(`/projects/${id}`)
}

export function createProject(name: string, descriptionMd?: string): Promise<Project> {
  return request<Project>('/projects', { method: 'POST', body: JSON.stringify({ name, descriptionMd }) })
}

export function updateProject(id: string, body: { name?: string; descriptionMd?: string }): Promise<Project> {
  return request<Project>(`/projects/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export interface RepoInput {
  name?: string
  url: string
  provider?: string
  default_branch?: string
}

export function addRepo(id: string, repo: RepoInput): Promise<Project> {
  return request<Project>(`/projects/${id}/repos`, { method: 'POST', body: JSON.stringify(repo) })
}

export function removeRepo(id: string, repoId: string): Promise<Project> {
  return request<Project>(`/projects/${id}/repos/${repoId}`, { method: 'DELETE' })
}

export function addDocLink(id: string, link: ProjectDocLink): Promise<Project> {
  return request<Project>(`/projects/${id}/doc-links`, { method: 'POST', body: JSON.stringify(link) })
}

export function removeDocLink(id: string, path: string): Promise<Project> {
  return request<Project>(`/projects/${id}/doc-links?path=${encodeURIComponent(path)}`, { method: 'DELETE' })
}
