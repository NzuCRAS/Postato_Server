import { z } from 'zod'
import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { backendRequest, BackendError } from './api-client.js'

/**
 * 注册全部业务工具。apiKey 来自当前 /mcp 请求的 Authorization 头,
 * 由闭包捕获并透传给后端;后端按该 Key 对应用户的职能判定权限。
 */
export function registerTools(server: McpServer, apiKey: string | undefined): void {
  // ---- 读取类(里程碑 5)----

  server.tool(
    'get_requirement_detail',
    '获取需求的结构化详情(用户故事、模块、验收条件)与开发进度树摘要。开发任务开始前必须先调用以理解目标。',
    { requirement_id: z.string().describe('需求 ID') },
    async ({ requirement_id }) => {
      try {
        const r = await backendRequest<Record<string, any>>(`/requirements/${requirement_id}`, apiKey)
        const summary = {
          id: r.id,
          title: r.title,
          status: r.status,
          project_id: r.projectId,
          doc_links: r.docLinks,
          structured: r.structured,
          dev_plan: r.devPlan ? summarizePlan(r.devPlan) : null,
        }
        return { content: [{ type: 'text' as const, text: JSON.stringify(summary, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'search_knowledge',
    '在知识库中检索文档。match_mode 选匹配方式:fuzzy(默认,分词跨标题/内容/标签)/exact(整串连续匹配)/tag(仅标签)/content(仅内容)/title(仅标题)/vector(向量检索,暂未实现)。默认排除临时(tmp)技术方案,include_tmp=true 可纳入。',
    {
      query: z.string().describe('搜索关键词'),
      match_mode: z
        .enum(['fuzzy', 'exact', 'tag', 'content', 'title', 'vector'])
        .optional()
        .describe('匹配模式,默认 fuzzy'),
      include_tmp: z.boolean().optional().describe('是否纳入 tmp 临时页,默认 false'),
      limit: z.number().optional().describe('返回条数,默认 3'),
    },
    async ({ query, match_mode, include_tmp, limit }) => {
      try {
        const qs = new URLSearchParams({ q: query })
        if (match_mode) qs.set('match_mode', match_mode)
        if (include_tmp) qs.set('include_tmp', 'true')
        const results = await backendRequest<Array<Record<string, any>>>(
          `/wiki/search?${qs.toString()}`,
          apiKey,
        )
        const top = (results ?? []).slice(0, limit ?? 3).map((p) => ({
          title: p.title,
          path: p.path,
          tags: p.tags,
          snippet: String(p.content ?? '').slice(0, 300),
        }))
        return { content: [{ type: 'text' as const, text: JSON.stringify({ results: top }, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  // ---- 写入类(里程碑 6)----

  server.tool(
    'create_dev_plan',
    '为需求创建模块化开发计划树(Herness)。按软件模块分解节点(可嵌套 children),每个节点可带 module_ref(关联需求模块名)、acceptance_criteria(验收点文本数组)、related_docs(知识库 path)。建树时可在 repo 记录 GitHub 仓库。已存在则返回 409。',
    {
      requirement_id: z.string().describe('需求 ID'),
      repo: z
        .object({
          url: z.string(),
          provider: z.string().optional(),
          default_branch: z.string().optional(),
        })
        .optional()
        .describe('GitHub 仓库,如 {url:"https://github.com/org/repo", default_branch:"main"}'),
      nodes: z
        .array(
          z.object({
            title: z.string(),
            description: z.string().optional(),
            module_ref: z.string().optional(),
            acceptance_criteria: z.array(z.string()).optional(),
            related_docs: z.array(z.string()).optional(),
            children: z.array(z.any()).optional(),
          }),
        )
        .describe('分解出的节点(children 可继续嵌套同样结构)'),
      root_title: z.string().optional().describe('根节点标题,默认用需求标题'),
    },
    async ({ requirement_id, repo, nodes, root_title }) => {
      try {
        const plan = await backendRequest<unknown>(`/requirements/${requirement_id}/dev-plan`, apiKey, {
          method: 'POST',
          body: JSON.stringify({ root_title, repo, nodes }),
        })
        return { content: [{ type: 'text' as const, text: JSON.stringify(plan, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'update_dev_plan_node',
    '更新某个进度节点:状态、artifacts(branch/pr_number/pr_url/tests_added)、commit(本次提交,挂到工作日志)、log_message(摘要)/log_detail(为什么这么做)、blocked_reason。完成一段编码后用本工具上报 commit。响应可能含 warnings(软提醒,不阻断)。',
    {
      requirement_id: z.string().describe('需求 ID'),
      node_id: z.string().describe('节点 ID,如 node_1'),
      status: z.enum(['todo', 'in_progress', 'done', 'blocked']).optional(),
      artifacts: z
        .object({
          branch: z.string().optional(),
          pr_number: z.number().optional(),
          pr_url: z.string().optional(),
          tests_added: z.boolean().optional(),
        })
        .optional(),
      commit: z
        .object({
          sha: z.string(),
          url: z.string().optional(),
          message: z.string().optional(),
          files: z.array(z.string()).optional(),
        })
        .optional()
        .describe('本次提交;url 可省略(平台用 repo.url + sha 拼)'),
      log_message: z.string().optional().describe('本次操作摘要'),
      log_detail: z.string().optional().describe('为什么这么做(决策依据,写进中间态日志)'),
      blocked_reason: z.string().optional().describe('status=blocked 时必填'),
      acceptance_criteria: z
        .array(z.object({ text: z.string(), checked: z.boolean() }))
        .optional()
        .describe('整列表替换该节点验收点(先用 get_requirement_detail 取当前项,把已满足的 checked 改 true,再把完整列表回传)'),
    },
    async ({ requirement_id, node_id, status, artifacts, commit, log_message, log_detail, blocked_reason, acceptance_criteria }) => {
      try {
        const res = await backendRequest<unknown>(
          `/requirements/${requirement_id}/dev-plan/nodes/${node_id}`,
          apiKey,
          {
            method: 'PATCH',
            body: JSON.stringify({ status, artifacts, commit, log_message, log_detail, blocked_reason, acceptance_criteria }),
          },
        )
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )
  server.tool(
    'add_dev_plan_nodes',
    '在已有进度树的某个父节点下追加子节点(把一个节点拆成更细的子任务)。后端自动续号、置 todo。需要先有计划。',
    {
      requirement_id: z.string().describe('需求 ID'),
      parent_node_id: z.string().describe('父节点 ID(子节点挂到它下面),如 node_1'),
      nodes: z
        .array(
          z.object({
            title: z.string(),
            description: z.string().optional(),
            module_ref: z.string().optional(),
            acceptance_criteria: z.array(z.string()).optional(),
            related_docs: z.array(z.string()).optional(),
            children: z.array(z.any()).optional(),
          }),
        )
        .describe('要追加的子节点(children 可继续嵌套)'),
    },
    async ({ requirement_id, parent_node_id, nodes }) => {
      try {
        const res = await backendRequest<unknown>(
          `/requirements/${requirement_id}/dev-plan/nodes/${parent_node_id}/children`,
          apiKey,
          { method: 'POST', body: JSON.stringify({ nodes }) },
        )
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'reset_dev_plan',
    '重置开发计划:把当前树标记为「已重置」并入档(不删除、保留全部日志,供日后 AI/人排查),清空后可重新 create_dev_plan。慎用。',
    {
      requirement_id: z.string().describe('需求 ID'),
      reason: z.string().optional().describe('重置原因(入档记录,便于日后排查)'),
    },
    async ({ requirement_id, reason }) => {
      try {
        const res = await backendRequest<unknown>(`/requirements/${requirement_id}/dev-plan/reset`, apiKey, {
          method: 'POST',
          body: JSON.stringify({ reason }),
        })
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'set_dev_plan_repo',
    '设置/更新进度树关联的代码仓库(建树后也能改)。节点 commit 链接会用 repo.url + sha 拼出。',
    {
      requirement_id: z.string().describe('需求 ID'),
      url: z.string().describe('仓库地址,如 https://github.com/org/repo'),
      provider: z.string().optional().describe('如 github'),
      default_branch: z.string().optional().describe('默认分支,如 main'),
    },
    async ({ requirement_id, url, provider, default_branch }) => {
      try {
        const res = await backendRequest<unknown>(`/requirements/${requirement_id}/dev-plan/repo`, apiKey, {
          method: 'PATCH',
          body: JSON.stringify({ url, provider, default_branch }),
        })
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'write_knowledge',
    '把可复用经验沉淀进知识库:按 path upsert 一篇 wiki 页(命中则更新,否则新建)。选好路径(如 /vue/toast)与标签(如 toast)便于日后 search_knowledge 检索复用。',
    {
      path: z.string().describe('wiki 路径,如 /vue/toast'),
      title: z.string(),
      content: z.string().describe('Markdown 内容'),
      tags: z.array(z.string()).optional(),
      parent_path: z.string().optional().describe('父级路径(目录树用)'),
    },
    async ({ path, title, content, tags, parent_path }) => {
      try {
        const res = await backendRequest<unknown>(`/wiki/pages/upsert`, apiKey, {
          method: 'POST',
          body: JSON.stringify({ path, title, content, tags, parentPath: parent_path }),
        })
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'write_tech_proposal',
    '为某进度节点写技术方案(文档先行):建/更新一篇技术方案 wiki 页(落在 /tech-proposals 临时区,标签 tech-proposal/tmp)并关联到节点(node.artifacts.tech_proposal_id);mark_in_progress 时把节点置 in_progress。',
    {
      requirement_id: z.string().describe('需求 ID'),
      node_id: z.string().describe('关联的进度节点 ID'),
      title: z.string(),
      content: z.string().describe('技术方案 Markdown(建议含:引用的知识库文档、实现方案、问题预警)'),
      tags: z.array(z.string()).optional(),
      mark_in_progress: z.boolean().optional().describe('true 则把节点置 in_progress'),
    },
    async ({ requirement_id, node_id, title, content, tags, mark_in_progress }) => {
      try {
        const res = await backendRequest<unknown>(`/requirements/${requirement_id}/tech-proposals`, apiKey, {
          method: 'POST',
          body: JSON.stringify({ node_id, title, content, tags, mark_in_progress }),
        })
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'get_project_detail',
    '获取项目详情:代码仓库、关联文档(设计/规范/效果参考)、业务域结构树(精简 L0-L4)、项目下需求摘要。开发前先读以理解项目结构与规范。',
    { project_id: z.string().describe('项目 ID,如 default') },
    async ({ project_id }) => {
      try {
        const project = await backendRequest<Record<string, any>>(`/projects/${project_id}`, apiKey)
        const arch = await backendRequest<Array<Record<string, any>>>(`/projects/${project_id}/arch`, apiKey)
        const reqs = await backendRequest<Array<Record<string, any>>>(
          `/requirements?projectId=${encodeURIComponent(project_id)}`,
          apiKey,
        )
        const summary = {
          id: project.id,
          name: project.name,
          descriptionMd: project.descriptionMd,
          repos: project.repos,
          doc_links: project.docLinks,
          architecture: (arch ?? []).map(summarizeArchNode),
          requirements: (reqs ?? []).map((r) => ({ id: r.id, title: r.title, status: r.status })),
        }
        return { content: [{ type: 'text' as const, text: JSON.stringify(summary, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'get_architecture',
    '获取项目结构树(业务域 L0-L4)。可按 tag/layer 过滤;过滤时返回命中节点 + 其祖先链(跨切面动态树)。',
    {
      project_id: z.string().describe('项目 ID'),
      tag: z.string().optional().describe('按标签过滤,如 安全'),
      layer: z.string().optional().describe('按层过滤:L0|L1|L2|L3|L4'),
    },
    async ({ project_id, tag, layer }) => {
      try {
        const qs = new URLSearchParams()
        if (tag) qs.set('tag', tag)
        if (layer) qs.set('layer', layer)
        const arch = await backendRequest<Array<Record<string, any>>>(
          `/projects/${project_id}/arch?${qs.toString()}`,
          apiKey,
        )
        return {
          content: [{ type: 'text' as const, text: JSON.stringify({ nodes: (arch ?? []).map(summarizeArchNode) }, null, 2) }],
        }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'sync_project_modules',
    '把仓库 .project.yaml 解析出的模块声明推给平台,幂等 reconcile 结构树 L3+ 工程树(消失的归档,不覆盖手动节点)。node 须为完整物化路径(含 L0 根)。',
    {
      project_id: z.string().describe('项目 ID'),
      repo_id: z.string().describe('项目内仓库 id,如 repo_default'),
      modules: z
        .array(
          z.object({
            node: z.string().describe('归属管理节点的完整物化路径,如 /Potato 平台/用户域/认证上下文'),
            title: z.string(),
            type: z.string().optional(),
            tags: z.array(z.string()).optional(),
            related_docs: z.array(z.string()).optional(),
            related_code: z.array(z.string()).optional(),
          }),
        )
        .describe('一个仓库声明的模块列表'),
    },
    async ({ project_id, repo_id, modules }) => {
      try {
        const res = await backendRequest<unknown>(`/projects/${project_id}/arch/sync`, apiKey, {
          method: 'POST',
          body: JSON.stringify({ repo_id, modules }),
        })
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'upsert_architecture',
    '写入/更新项目结构树的一棵子树(管理树 L0–L2 或任意层):递归 upsert,parent_path 为空挂到根,layer 显式优先否则按父层自动 +1(根=L0),按 path 幂等(可重复推送)。source=manual。与 sync_project_modules(L3+ 工程树、按 repo reconcile、消失归档)互补——本工具用于人/AI 维护业务域骨架(系统/领域/上下文/模块)。',
    {
      project_id: z.string().describe('项目 ID,如 default'),
      parent_path: z
        .string()
        .optional()
        .describe('挂载点物化路径,如 /Potato 平台/用户域;留空=挂到根(建 L0)'),
      nodes: z
        .array(
          z.object({
            title: z.string(),
            layer: z.string().optional().describe('L0|L1|L2|L3|L4;缺省按父层 +1(根=L0)'),
            type: z.string().optional().describe('system|domain|context|module|component...'),
            description: z.string().optional(),
            tags: z.array(z.string()).optional(),
            related_docs: z.array(z.string()).optional().describe('知识库 wiki path'),
            related_code: z.array(z.string()).optional().describe('代码 glob,如 backend/.../auth/**'),
            children: z.array(z.any()).optional(),
          }),
        )
        .describe('一棵或多棵子树(children 可继续嵌套同样结构)'),
    },
    async ({ project_id, parent_path, nodes }) => {
      try {
        const res = await backendRequest<unknown>(`/projects/${project_id}/arch/upsert-tree`, apiKey, {
          method: 'POST',
          body: JSON.stringify({ parent_path, nodes }),
        })
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )
}

function summarizeArchNode(n: Record<string, any>) {
  return {
    path: n.path,
    layer: n.layer,
    type: n.type,
    title: n.title,
    tags: n.tags,
    related_docs: n.related_docs,
    related_code: n.related_code,
    source: n.source,
  }
}

function summarizePlan(plan: Record<string, any>) {
  const flat: Array<Record<string, any>> = []
  const walk = (n: Record<string, any> | undefined) => {
    if (!n) return
    flat.push({
      id: n.id,
      title: n.title,
      status: n.status,
      module_ref: n.module_ref,
      acceptance_criteria: n.acceptance_criteria, // 含 checked,AI 可见哪些没勾
      related_docs: n.related_docs,
      blocked_reason: n.blocked_reason,
      open_corrections: (n.corrections ?? []).filter((c: any) => !c.resolved), // 未解决纠偏
    })
    ;(n.children ?? []).forEach(walk)
  }
  walk(plan.root)
  return { repo: plan.repo, root_status: plan.root?.status, nodes: flat }
}

function toolError(e: unknown) {
  const msg =
    e instanceof BackendError ? `[${e.status}] ${e.message}` : e instanceof Error ? e.message : String(e)
  return { content: [{ type: 'text' as const, text: `错误:${msg}` }], isError: true }
}
