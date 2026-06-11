import express from 'express'
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js'
import { registerTools } from './tools.js'

const PORT = Number(process.env.MCP_PORT ?? 3001)

function extractApiKey(req: express.Request): string | undefined {
  const h = req.headers['authorization']
  if (typeof h === 'string' && h.startsWith('Bearer ')) return h.slice(7).trim()
  return undefined
}

// 无状态模式:每个 /mcp 请求新建 server,把该请求的 API Key 注入,tool 闭包透传给后端。
function buildServer(apiKey: string | undefined): McpServer {
  const server = new McpServer({ name: 'potato-mcp-server', version: '0.1.0' })
  registerTools(server, apiKey)
  return server
}

const app = express()
app.use(express.json())

app.get('/health', (_req, res) => {
  res.json({ status: 'UP', service: 'potato-mcp-server' })
})

app.post('/mcp', async (req, res) => {
  const apiKey = extractApiKey(req)
  const server = buildServer(apiKey)
  const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined })
  res.on('close', () => {
    transport.close()
    server.close()
  })
  try {
    await server.connect(transport)
    await transport.handleRequest(req, res, req.body)
  } catch (err) {
    console.error('[potato-mcp] request error', err)
    if (!res.headersSent) res.status(500).json({ error: 'MCP 处理失败' })
  }
})

// 已注册工具数(新增/删除工具时同步此处):get_requirement_detail / search_knowledge /
// create_dev_plan / update_dev_plan_node / add_dev_plan_nodes / reset_dev_plan /
// set_dev_plan_repo / write_knowledge / write_tech_proposal /
// get_project_detail / get_architecture / sync_project_modules / upsert_architecture /
// create_requirement
const TOOL_COUNT = 14

app.listen(PORT, () => {
  console.log(`[potato-mcp] Streamable HTTP MCP server on http://localhost:${PORT}/mcp (tools: ${TOOL_COUNT})`)
})
