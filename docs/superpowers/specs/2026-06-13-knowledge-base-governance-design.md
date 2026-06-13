# 知识库 wiki 治理 — 设计 spec

> 状态:设计已对齐(2026-06-13),待实施。把知识库 wiki 从「扁平文档库」治理成平台的「**可复用知识沉淀仓 + 索引中枢**」,并引入 MinIO 作为资产层。源自《关于一次完整的闭环设计》八大支柱中的「代码规范 / 先验经验 / 可复用代码」三类知识。

## 背景与目标
知识库要承载**可复用知识**,并以两种方式被 LLM 消费:
- **必备文档**(代码规范等)在开发流程中由**核心 skill 的 SOP 注入**;
- **先验经验**(场景处理手段、bug 解法)做成**触发式**,拿不准时经 MCP 检索(关键词,RAG 留后)提供。

当前 wiki 是 Mongo 直存的扁平 Markdown 库。B1 切片刚给 `WikiPage` 加了 `category`(`doc/asset/standard`)与 `search` 的 category 过滤,**但改动未提交、`WikiServiceTest` 未跟上(编译红)**,且缺「先验经验」类、无任何资产(图片/demo)存储能力。本切片统一治理:定内容模型、补先验经验类、引入 MinIO 资产层、收编 B1 半成品。

## 已对齐的决策
- **定位 = 方案 C**:沉淀仓 + 索引中枢 + 晋升机制。wiki 只原生存可复用知识;需求/开发计划保持为 `Requirement`/`DevPlan` 实体,靠链接关联,**不镜像进 wiki**——守住「结构化需求 = 单一事实来源」。
- **存储分层**:文本正文(Markdown)留 Mongo——检索/注入直接读它,**不外置**;二进制资产(demo html、视觉稿、截图、代码包)进 MinIO。
- **MinIO 简单版**:单 bucket、后端代理上传、开发期 bucket 公开读、key 带页 id 前缀。
- **分类模型**:`category` 受控枚举扩 `experience`;`doc` 作兜底;`tmp` 用 **tag** 表达「未晋升草稿」(维持现状)。
- **晋升 = 带语义的 `update`**,不造专用 API(YAGNI)。
- **消费侧不在本 spec 固化**:SOP skill 正文 / RAG 留待后续在 skill 开发与使用中迭代;本期只保证「能按 category 批量取」的能力就位。

## 内容分类模型
| category | 装什么 | 消费方式 |
|----------|--------|----------|
| `standard` | 代码规范(接口契约/数据模型/代码风格) | SOP 注入(开工必读) |
| `asset` | 可复用代码/组件(可挂 OSS 资产) | 注入清单、按需取详情 |
| `experience` ⭐新增 | 先验经验(场景手段、bug 解法) | 触发式检索 |
| `doc` | 通用说明(`/agent/*` 协议、架构说明等) | 杂项兜底(默认值) |

`tmp`(tag,正交于 category):开发伴生的技术方案/过程草稿,检索默认排除。**晋升 = 摘 tmp + 设正式 category(多半 `experience`)+ 可改 path**。

## 数据模型(`WikiPage`)
- `category`:`VALID_CATEGORY` 增 `experience` → `{ doc, asset, standard, experience }`;默认 `doc`;存量空值读作 `doc`(沿用 `effectiveCategory`)。
- 新增内嵌 `assets: List<Asset>`(默认空),`Asset` 结构:
```
Asset {
  name,         // 显示名(原文件名)
  objectKey,    // MinIO 中的 key,如 wiki/{pageId}/{uuid}-{filename}
  url,          // 可访问 URL(开发期公开读直拼;生产换签名 URL)
  contentType,  // MIME
  size,         // 字节
  uploadedAt    // 时间戳(后端填)
}
```
内嵌而非独立集合:资产生命周期从属于页,无跨页共享需求(YAGNI)。

## 存储分层 + MinIO
- **Mongo**:`WikiPage` 全部结构化字段 + Markdown 正文。
- **MinIO**:二进制资产。
- 后端新增 `StorageService`(`upload(pageId, file) / getUrl(objectKey) / delete(objectKey)`),**唯一碰对象存储的地方**(隔离),用 S3 兼容 SDK(MinIO Java SDK 或 AWS SDK)。
- `docker-compose.yml` 加 `minio/minio` 服务(挂 volume 持久化 + 控制台),启动建 bucket `potato-assets`;后端配置经环境变量注入(`MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET`,服务名 `minio`,本机回退 localhost)。
- 上传走**后端代理**:前端 `POST /wiki/pages/{id}/assets`(multipart)→ 后端写 MinIO + 把 `Asset` 追加进页 → 返回更新后的 assets。删除 `DELETE /wiki/pages/{id}/assets/{objectKey}`。
- 访问:开发期 bucket 公开读,`url` 直拼 `{endpoint}/{bucket}/{objectKey}`,demo html 可在浏览器/沙箱直接加载。

## 检索(触发式 · 先验经验)
- 沿用现有 `search(q, mode, includeTmp, category)`——**category 过滤 B1 已实现**。
- 先验经验触发式检索 = `search_knowledge(category="experience", q=...)`。
- **RAG 本期不做**:维持关键词/分词;`MatchMode.VECTOR` 仍占位报 501,接口已预留。

## 晋升机制(最小形态)
「晋升」复用现有 `update`(已能改 tags/category/parentPath):摘 `tmp` 标签 + 设正式 category + 可改 path(如从 `/tech-proposals/...` 移到 `/experience/...`)。不引入独立 `promote` API——晋升即一次带语义的 update。前端给 tmp 页一个「晋升」按钮(填目标 category/path);AI 经 `write_knowledge` 亦可晋升。

## MCP(工具数不变,确认参数)
- `write_knowledge` / `search_knowledge`:`category` 入参取值含 `experience`(改已有工具参数,**重启即生效、无需重连**)。
- 资产上传**不走 MCP**(AI 不传二进制);AI 经读取工具拿到 `assets` 的 `url` 去引用。
- wiki 读取(及 `get_requirement_detail` 关联文档)返回体带上 `assets`。

## 前端(遵循视图/逻辑分离)
- `api/wiki.ts` 加资产上传/删除(纯 HTTP);`features/useWikiEditor.ts` 编排上传副作用。
- `WikiEditor`:category 选择器(4 类) + 资产上传组件(传 MinIO、列已传资产、复制 URL)。
- `WikiTree`/列表:category 标签 + 按 category 筛选(B1c)。
- tmp 页的「晋升」按钮(填目标 category/path,走 update)。

## 索引中枢(需求 ↔ 资产,用现有机制)
需求「通向 demo」的索引链路:`Requirement.doc_links` → 指向一个 `asset` 类 wiki 页 → 该页 `assets[]` 挂 demo html(MinIO)。全程复用已有 `doc_links`,不新造关联机制。

## 迁移与兼容 + 收编 B1
- **先修 `WikiServiceTest`**(新签名 + category 测试)让编译转绿,把 B1 未提交改动纳入本切片统一实现/测试。
- `experience` 加入 `VALID_CATEGORY`;`assets` 默认空数组;存量 `category` 空读作 `doc`——全向后兼容,无数据迁移脚本。

## 本期范围 / 非目标
- **做**:`experience` 类、`assets` + MinIO 简单版、检索 category 过滤(已有)、晋升 = 语义 update、前端三件套(category 选择/资产上传/筛选)+ 晋升按钮、收编并修绿 B1。
- **不做(后续 / skill 阶段)**:RAG/向量检索、SOP skill 正文、签名 URL / 生产加固、独立资产集合、资产跨页共享、需求↔资产反向索引 UI、检索「只返清单省上下文」优化(nice-to-have)。

## 验证
- 后端:`docker compose exec -T backend mvn -Dtest=WikiServiceTest test`(category 默认 doc / 非法 400 / experience 合法 / 按 category 过滤);`StorageService` 上传/取 URL 冒烟(可用 manual)。
- MCP/前端:`tsc --noEmit`。
- 前端界面统一验证:新建 experience 页、上传一张图/一个 html 资产看 URL 可访问、按 category 筛选、把一个 tmp 页晋升。

## 风险
- **公开读**:开发期 bucket 公开读可接受;生产须换签名 URL / 私有桶(已列非目标,留部署切片)。
- **后端代理上传**:大文件占后端内存/带宽,加上传大小上限(如 10MB)兜底。
- **MinIO 依赖**:多一个容器,docker-compose 启动变重;开发期可接受。
