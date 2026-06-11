// 共享类型定义(与后端 DTO 对应)

export interface ApiKeyInfo {
  id: string
  name: string
  keyPreview: string
  createdAt: string
}

export interface User {
  id: string
  username: string
  functions: string[]
  apiKeys: ApiKeyInfo[]
}

export interface LoginResponse {
  token: string
  userId: string
  username: string
  functions: string[]
}

export interface CreatedApiKey {
  name: string
  key: string
  createdAt: string
}

// ---- 需求(里程碑 2)----
// structured / dev_plan 内部字段为 snake_case(对齐后端 @JsonProperty 与 MCP 契约)

export interface StructuredModule {
  name: string
  description?: string
  acceptance_criteria?: string[]
  ui_states?: string[]
  related_assets?: string[]
}

export interface Structured {
  user_stories?: string[]
  modules?: StructuredModule[]
  interaction_flow?: string
  ambiguous_points?: string[]
}

// ---- 进度树(里程碑 3 + 做深)----

export interface AcceptanceItem {
  text: string
  checked: boolean
}

export interface DevPlanCommit {
  sha: string
  url?: string
  message?: string
  files?: string[]
}

export interface DevPlanArtifacts {
  branch?: string
  pr_number?: number
  pr_url?: string
  tests_added?: boolean
  tech_proposal_id?: string
}

export interface DevPlanLog {
  timestamp: string
  actor?: string // ai | human
  action: string // created | status_change | note
  summary?: string
  detail?: string
  from?: string
  to?: string
  commit?: DevPlanCommit
}

export interface DevPlanCorrection {
  id: string
  timestamp: string
  by: string
  message: string
  resolved: boolean
}

export interface DevPlanNode {
  id: string
  title: string
  description?: string
  status: string // todo | in_progress | done | blocked
  blocked_reason?: string
  module_ref?: string
  acceptance_criteria?: AcceptanceItem[]
  related_docs?: string[]
  artifacts?: DevPlanArtifacts
  log?: DevPlanLog[]
  corrections?: DevPlanCorrection[]
  children?: DevPlanNode[]
}

export interface DevPlanRepo {
  url: string
  provider?: string
  default_branch?: string
}

export interface DevPlan {
  created_at?: string
  updated_at?: string
  repo?: DevPlanRepo
  root: DevPlanNode
}

export interface RequirementSummary {
  id: string
  title: string
  status: string
  version: number
  updatedAt: string
}

export interface Requirement {
  id: string
  projectId: string
  repoId?: string
  title: string
  descriptionMd?: string
  sourceHtml?: string
  status: string
  version: number
  structured: Structured
  devPlan?: DevPlan
  docLinks?: ProjectDocLink[]
  createdBy?: string
  createdAt: string
  updatedAt: string
}

// ---- 知识库(里程碑 4)----

export interface WikiPageItem {
  id: string
  projectId?: string
  title: string
  path: string
  parentPath?: string
  content: string
  tags: string[]
  status: string
  version: number
  updatedAt: string
}

// ---- 项目(Project)+ 结构树 ----

export interface ProjectRepo {
  id: string
  name?: string
  url: string
  provider?: string
  default_branch?: string
}

export interface ProjectDocLink {
  type?: string // design | standard | reference
  title?: string
  path: string
}

export interface Project {
  id: string
  name: string
  descriptionMd?: string
  repos?: ProjectRepo[]
  docLinks?: ProjectDocLink[]
  createdAt?: string
  updatedAt?: string
}

export interface ArchNode {
  id: string
  projectId: string
  parentId?: string
  path: string
  layer?: string // L0..L4
  type?: string
  title: string
  description?: string
  tags?: string[]
  related_docs?: string[]
  related_code?: string[]
  related_requirements?: string[]
  source?: string // manual | sync
  repoId?: string
  status?: string // active | archived
  impl_status?: string // planned | in_progress | done(叶子手动标,非叶子后端聚合)
}
