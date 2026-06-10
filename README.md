# 🥔 Potato 内部效能平台

团队内部研发效能平台:**需求对齐 + 知识沉淀 + vibecoding(AI 辅助编程)**。

核心理念:通过「结构化需求 + Herness 进度树 + MCP 契约」,让 Claude Code **步步为营、可追溯、可干预**地开发。

> 当前进度:第一切片「灵魂闭环」开发中(详见 `docs/`)。

## 目录结构

```
.
├── backend/          # Java 17 + Spring Boot 3(REST API,端口 8080)
├── frontend/         # React 18 + TS + Vite + Ant Design(端口 5173)
├── mcp-server/       # TypeScript MCP Server(Streamable HTTP,端口 3001)
├── docs/             # 设计文档
└── docker-compose.yml  # 开发期编排:mongo + backend + frontend + mcp-server
```

## 启动(全部容器化,推荐)

前置:Docker。

```bash
docker compose up -d                # 首次较慢:容器内装 npm 依赖、下载 Maven 依赖
docker compose logs -f backend      # 看到 "Started PotatoApplication" 即后端就绪
```

- 后端  http://localhost:8080/health
- 前端  http://localhost:5173
- MCP   http://localhost:3001/health
- Mongo localhost:27017

源码以 volume 挂载进容器:**前端 / MCP 改代码即时热重载**;后端容器内是 `mvn spring-boot:run`,改 Java 后重启 `backend` 容器生效(频繁改后端时可改用本机直跑或加 devtools,后续优化)。

## 启动(本机直跑,需 JDK 17 / Maven / Node)

```bash
docker compose up -d mongo
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
cd mcp-server && npm install && npm run dev
```

## 说明

- 开发用官方镜像 + 挂载源码 + 热重载;**生产多阶段镜像 + GitHub Actions 留到部署切片**。
- 服务间通信用 compose 服务名(`mongo` / `backend`),地址由环境变量注入,本机直跑回退到 `localhost`。
