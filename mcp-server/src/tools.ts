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
    '获取需求的结构化详情(用户故事、模块、验收条件)、开发进度树摘要,以及**项目级规范文档**(project_doc_links:项目全局的代码/视觉/契约规范)。开发任务开始前必须先调用以理解目标并先读规范。',
    { requirement_id: z.string().describe('需求 ID') },
    async ({ requirement_id }) => {
      try {
        const r = await backendRequest<Record<string, any>>(`/requirements/${requirement_id}`, apiKey)
        // ④ 开工规范前置:在需求级 doc_links 之外,额外附项目级 docLinks,
        // 让 AI 一次调用即看到项目全局规范(代码/视觉/契约),不必再单独 get_project_detail。
        // 项目级规范是辅助信息,取不到不应阻断需求详情 → 降级为空数组。
        let projectDocLinks: unknown = []
        if (r.projectId) {
          try {
            const p = await backendRequest<Record<string, any>>(`/projects/${r.projectId}`, apiKey)
            projectDocLinks = p.docLinks ?? []
          } catch {
            projectDocLinks = []
          }
        }
        const summary = {
          id: r.id,
          title: r.title,
          status: r.status,
          project_id: r.projectId,
          project_doc_links: projectDocLinks, // 项目级规范(开工必读):代码/视觉/契约规范
          doc_links: r.docLinks,              // 需求级关联文档(本需求专属的设计/效果参考)
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
      category: z
        .enum(['doc', 'asset', 'standard', 'experience', 'runlog'])
        .optional()
        .describe('按资产分类过滤:doc/asset/standard/experience/runlog(runlog=执行文档,默认检索排除)'),
    },
    async ({ query, match_mode, include_tmp, limit, category }) => {
      try {
        const qs = new URLSearchParams({ q: query })
        if (match_mode) qs.set('match_mode', match_mode)
        if (include_tmp) qs.set('include_tmp', 'true')
        if (category) qs.set('category', category)
        const results = await backendRequest<Array<Record<string, any>>>(
          `/wiki/search?${qs.toString()}`,
          apiKey,
        )
        const top = (results ?? []).slice(0, limit ?? 3).map((p) => ({
          title: p.title,
          path: p.path,
          category: p.category,
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
    '更新某个进度节点:状态、artifacts(branch/pr_number/pr_url/tests_added)、commit(本次提交,挂到工作日志)、verifications(本地验证记录:kind/command/result/summary)、log_message(摘要)/log_detail(为什么这么做)、blocked_reason。done 前应本地跑验证(编译/测试/lint)并经 verifications 上报;done 时无通过验证会软警告。响应可能含 warnings(软提醒,不阻断)。',
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
      verifications: z
        .array(
          z.object({
            kind: z.enum(['compile', 'typecheck', 'test', 'lint', 'manual', 'e2e']),
            command: z.string().optional().describe('跑了什么,如 "mvn -Dtest=X test"'),
            result: z.enum(['pass', 'fail']),
            summary: z.string().optional().describe('结果摘要,如 "10 tests, 0 failed"'),
            covers: z.array(z.string()).optional().describe('关联的验收点文本(可选)'),
          }),
        )
        .optional()
        .describe('本地验证记录(追加累积):done 前应跑编译/测试/lint 并上报;done 时无任何 pass 验证会软警告'),
    },
    async ({ requirement_id, node_id, status, artifacts, commit, log_message, log_detail, blocked_reason, acceptance_criteria, verifications }) => {
      try {
        const res = await backendRequest<unknown>(
          `/requirements/${requirement_id}/dev-plan/nodes/${node_id}`,
          apiKey,
          {
            method: 'PATCH',
            body: JSON.stringify({ status, artifacts, commit, log_message, log_detail, blocked_reason, acceptance_criteria, verifications }),
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
      category: z
        .enum(['doc', 'asset', 'standard', 'experience', 'runlog'])
        .optional()
        .describe('资产分类:doc(默认)/asset(可复用代码)/standard(代码规范)/experience(先验经验)/runlog(执行文档轨迹)'),
    },
    async ({ path, title, content, tags, parent_path, category }) => {
      try {
        const res = await backendRequest<unknown>(`/wiki/pages/upsert`, apiKey, {
          method: 'POST',
          body: JSON.stringify({ path, title, content, tags, parentPath: parent_path, category }),
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
    'upsert_arch_layer',
    '逐层共建项目结构树:在 parent_path 下写入/更新【一层】子节点(不支持嵌套 children)。parent_path 为空=建 L0 根;按 path 幂等(同层已存在则更新)。务必配合『逐层共建协议』(先 search_knowledge 取 /agent/arch-coauthoring):每层先与用户澄清、确认后再写本层,勿跳层、勿一次性灌整树。层级语义:L0系统→L1领域→L2限界上下文→L3业务模块;L3=业务模块,Service/类/页面等实现单元属 L4、交 sync_project_modules。',
    {
      project_id: z.string().describe('项目 ID,如 default'),
      parent_path: z
        .string()
        .optional()
        .describe('挂载点物化路径,如 /Potato 平台/用户域;留空=建 L0 根'),
      nodes: z
        .array(
          z.object({
            title: z.string(),
            layer: z.string().optional().describe('L0|L1|L2|L3|L4;缺省按父层 +1(根=L0)'),
            type: z.string().optional().describe('system|domain|context|module'),
            description: z
              .string()
              .optional()
              .describe('职责(负责什么)+ 边界(不负责什么/与同层兄弟的区别);勿同义反复'),
            tags: z.array(z.string()).optional().describe('跨切面维度,如 安全'),
            related_docs: z.array(z.string()).optional().describe('知识库 wiki path'),
            related_code: z.array(z.string()).optional().describe('代码 glob,如 backend/.../auth/**'),
            impl_status: z
              .enum(['planned', 'in_progress', 'done'])
              .optional()
              .describe('实现状态(仅叶子节点用;非叶子由后端按子节点自动聚合,手动传了也会被覆盖):planned 规划中 / in_progress 实现中 / done 已完成,缺省 planned'),
          }),
        )
        .describe('【单层】兄弟节点(无 children;要建下一层请确认后再调一次本工具)'),
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

  server.tool(
    'create_requirement',
    '在某项目下创建需求(结构化需求=单一事实来源)。title 必填;status 默认 draft;structured 可含 user_stories/modules 等。建完可用 get_requirement_detail 查看、create_dev_plan 建进度树。权限:后端 requirement/create(product 职能)。',
    {
      title: z.string().describe('需求标题'),
      description_md: z.string().optional().describe('需求描述(Markdown)'),
      project_id: z.string().optional().describe('归属项目 ID,默认 default'),
      status: z
        .enum(['draft', 'clarifying', 'confirmed', 'deprecated'])
        .optional()
        .describe('需求状态,默认 draft'),
      structured: z
        .any()
        .optional()
        .describe('结构化需求:{user_stories,modules:[{name,description,acceptance_criteria,ui_states,related_assets}],interaction_flow,ambiguous_points}'),
      doc_links: z
        .array(z.object({ type: z.string().optional(), title: z.string().optional(), path: z.string() }))
        .optional()
        .describe('关联知识库文档(设计/规范/效果参考)'),
      type: z
        .enum(['feature', 'improvement', 'bugfix'])
        .optional()
        .describe('需求分类:feature 增量 / improvement 修改优化 / bugfix 维护与 bug 修复'),
      tier: z
        .enum(['Large', 'Medium', 'Small'])
        .optional()
        .describe('复杂度档(建议创建时选,**仅供参考**):驱动 potato 流程强度——Small 精简 / Medium 标准 / Large 完整。缺省 Medium'),
    },
    async ({ title, description_md, project_id, status, structured, doc_links, type, tier }) => {
      try {
        const res = await backendRequest<unknown>(`/requirements`, apiKey, {
          method: 'POST',
          body: JSON.stringify({
            title,
            descriptionMd: description_md,
            projectId: project_id ?? 'default',
            status,
            structured,
            docLinks: doc_links,
            type,
            tier,
          }),
        })
        return { content: [{ type: 'text' as const, text: JSON.stringify(res, null, 2) }] }
      } catch (e) {
        return toolError(e)
      }
    },
  )

  server.tool(
    'relate_requirement_arch',
    '需求完成回标(闭环第⑨/⑩步):把需求关联到结构树业务模块节点(双向),并可按判断回标叶子节点的 impl_status。impl_status 省略=只建关联不改状态;回标应指向**叶子业务模块**(非叶子由子节点聚合,不会被直接设,会在 warnings 提示)。响应含 related_arch_nodes(更新后)与 warnings(软提示,不阻断)。',
    {
      requirement_id: z.string().describe('需求 ID'),
      links: z
        .array(
          z.object({
            arch_path: z
              .string()
              .describe('结构树节点物化路径(从 get_architecture/get_project_detail 取),如 /Potato 平台/研发过程域/进度树上下文'),
            impl_status: z
              .enum(['planned', 'in_progress', 'done'])
              .optional()
              .describe('回标的实现状态;省略=只建关联不改状态。仅对叶子节点生效,非叶子由子聚合'),
          }),
        )
        .describe('要关联/回标的结构树节点列表'),
    },
    async ({ requirement_id, links }) => {
      try {
        const res = await backendRequest<unknown>(`/requirements/${requirement_id}/arch-links`, apiKey, {
          method: 'POST',
          body: JSON.stringify({ links }),
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
    description: n.description,
    impl_status: n.impl_status,
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
