# 知识库 wiki 治理 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把知识库 wiki 治理成「可复用知识沉淀仓 + 索引中枢」:补 `experience` 类、引入 MinIO 资产层、补齐 MCP/前端的 category 能力、加最小晋升机制,并收编 B1 未提交改动。

**Architecture:** `WikiPage`(Mongo)永远是结构化索引节点,Markdown 正文留 Mongo 供检索;二进制资产经后端代理上传到 MinIO,页内嵌 `assets[]` 记 key/url。需求→demo 的索引复用现有 `Requirement.doc_links` → `asset` 类 wiki 页 → 页 `assets[]`,不造新关联。晋升 = 一次带语义的 `update`(去 tmp + 设正式 category + 改 path,把文档从临时区搬到 `/experience/` 板块)。

**Tech Stack:** Java 17 / Spring Boot 3.3 / MongoDB / MinIO Java SDK 8.5;TypeScript MCP(`@modelcontextprotocol/sdk` + zod);React 18 + Vite + Ant Design;Docker Compose 容器化开发。

**spec:** `docs/superpowers/specs/2026-06-13-knowledge-base-governance-design.md`

**约定:** 容器内验证 —— 后端 `docker compose exec -T backend mvn -Dtest=Xxx test`、改后端 `docker compose restart backend`;MCP/前端 `docker compose exec -T <svc> npx tsc --noEmit`。领域字段 `structured`/`dev_plan` 用 snake_case;**平台外壳 API(含 WikiPage)用 camelCase**。前端视图/逻辑分离(api/ 纯 HTTP、features/useXxx 逻辑、pages/components 只渲染)。提交信息末尾保留 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

**关于改 path(长久需求):** 晋升(tmp 技术方案 → 先验经验)需把文档从临时区 `/tech-proposals/...` 搬到经验板块 `/experience/...`,**path 必须可改**。故本 plan 扩展 `WikiService.update` 支持改 path(新 path 查重 → 409;晋升对象是叶子,**不级联**子页 `parentPath`)。改 path 后原 `DevPlan` 节点的 `artifacts.tech_proposal_id`(旧 path)会失效,可接受(技术方案晋升即脱离原节点)。

---

## 文件结构(决策锁定)

**后端**
- `backend/src/main/java/com/potato/wiki/WikiPage.java` — 改:加 `assets: List<Asset>` + 内嵌 `Asset` 类
- `backend/src/main/java/com/potato/wiki/WikiService.java` — 改:`VALID_CATEGORY` 加 `experience`;`update` 支持改 path(+ 查重);加 `addAsset`/`removeAsset`
- `backend/src/test/java/com/potato/wiki/WikiServiceTest.java` — 改:加 category 专项测试 + addAsset/removeAsset 测试;删诊断探针注释
- `backend/src/main/java/com/potato/wiki/WikiController.java` — 改:加 `POST /pages/{id}/assets`、`DELETE /pages/{id}/assets`
- `backend/src/main/java/com/potato/storage/StorageService.java` — **新建**:唯一封装 MinIO 的地方(upload/urlOf/delete + 建桶)
- `backend/pom.xml` — 改:加 `io.minio:minio`
- `backend/src/main/resources/application.yml` — 改:加 `app.minio.*` + multipart 上限
- `docker-compose.yml` — 改:加 `minio` 服务 + backend `MINIO_*` env + volume

**MCP**
- `mcp-server/src/tools.ts` — 改:`search_knowledge`/`write_knowledge` 加 `category` 参数;search 返回带 category

**前端**
- `frontend/src/types/index.ts` — 改:加 `WikiAsset`;`WikiPageItem` 加 `category`/`assets`
- `frontend/src/api/client.ts` — 改:`request` 识别 `FormData` 时不强设 Content-Type
- `frontend/src/api/wiki.ts` — 改:`WikiInput` 加 `category`;`searchWiki` 加 opts;加 `uploadAsset`/`deleteAsset`
- `frontend/src/features/useWikiEditor.ts` — 改:form 加 category;加 assets 状态 + upload/remove
- `frontend/src/features/useWiki.ts` — 改:加 category 筛选
- `frontend/src/pages/WikiEditPage.tsx` — 改:加 category 选择器 + 资产上传区(仅编辑已有页)
- `frontend/src/pages/WikiPage.tsx` — 改:加 category 筛选下拉 + 资产展示 + 晋升按钮
- `frontend/src/components/WikiTree.tsx` — 改:节点标题挂 category 小标签

> `frontend/src/components/WikiEditor.tsx` 是**死代码**(无引用),本 plan 不动。

---

## Task 1: 收编 B1 地基 — `experience` 类 + category 专项测试

**Files:**
- Modify: `backend/src/main/java/com/potato/wiki/WikiService.java:17`
- Test: `backend/src/test/java/com/potato/wiki/WikiServiceTest.java`

- [ ] **Step 1: 跑现有测试,确认 B1 当前编译/通过状态**

Run: `docker compose exec -T backend mvn -q -Dtest=WikiServiceTest test`
Expected: 要么 BUILD SUCCESS(签名已对齐),要么编译错误。若编译失败,先按报错把测试签名对齐到 `upsertByPath(path,title,content,category,tags,parentPath,userId)` 与 `search(q,mode,includeTmp,category)` 再继续。

- [ ] **Step 2: 删诊断探针注释**

删除 `WikiServiceTest.java` 末尾的 `// diag-probe-7788` 这一行。

- [ ] **Step 3: 写失败测试(category 行为)**

在 `WikiServiceTest.java` 的 `vector_mode_is_not_implemented` 测试之后、类结束 `}` 之前,加:

```java
    // ---- category ----

    @Test
    void create_defaults_category_to_doc_when_null() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("T", "/p", null, "c", null, null, "u");
        assertThat(p.getCategory()).isEqualTo("doc");
    }

    @Test
    void create_rejects_invalid_category() {
        assertThatThrownBy(() -> service.create("T", "/p", null, "c", "bogus", null, "u"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("category");
    }

    @Test
    void create_accepts_experience_category() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.create("T", "/p", null, "c", "experience", null, "u");
        assertThat(p.getCategory()).isEqualTo("experience");
    }

    @Test
    void search_filters_by_category_treating_null_as_doc() {
        WikiPage exp = page("经验页", "缓存雪崩处理", List.of("cache"));
        exp.setCategory("experience");
        WikiPage plain = page("普通页", "缓存说明", List.of("cache")); // category null → 视为 doc
        when(repo.findAllByOrderByPathAsc()).thenReturn(List.of(exp, plain));
        assertThat(titles(service.search("缓存", MatchMode.FUZZY, false, "experience"))).containsExactly("经验页");
        assertThat(titles(service.search("缓存", MatchMode.FUZZY, false, "doc"))).containsExactly("普通页");
    }
```

- [ ] **Step 4: 跑测试,验证 `create_accepts_experience_category` 失败**

Run: `docker compose exec -T backend mvn -q -Dtest=WikiServiceTest test`
Expected: FAIL —— `create_accepts_experience_category` 因 `experience` 不在 `VALID_CATEGORY` 抛 400 而非返回该值。

- [ ] **Step 5: 加 `experience` 到 VALID_CATEGORY**

`WikiService.java` 第 17 行:
```java
    private static final Set<String> VALID_CATEGORY = Set.of("doc", "asset", "standard", "experience");
```

- [ ] **Step 6: 跑测试,验证全绿**

Run: `docker compose exec -T backend mvn -q -Dtest=WikiServiceTest test`
Expected: PASS(含 4 个新 category 测试)。

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/potato/wiki/WikiService.java backend/src/test/java/com/potato/wiki/WikiServiceTest.java
git commit -m "$(printf 'feat(wiki): category 增 experience 类 + 专项测试,收编 B1\n\nCo-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>')"
```

---

## Task 2: 后端数据模型 — `WikiPage.assets[]` + `Asset`

**Files:**
- Modify: `backend/src/main/java/com/potato/wiki/WikiPage.java`

- [ ] **Step 1: 加 `assets` 字段与内嵌 `Asset` 类**

`WikiPage.java` —— 在 `private int version;` 之后加字段,在类体末尾(最后一个 `}` 之前)加内嵌类:

```java
    private List<Asset> assets = new ArrayList<>(); // 挂载在本页的 OSS 资产(demo/图片/代码包),平台 API camelCase

    @Data
    public static class Asset {
        private String name;        // 显示名(原文件名)
        private String objectKey;   // MinIO 中的 key,如 wiki/{pageId}/{uuid}-{filename}
        private String url;         // 浏览器可访问 URL(开发期公开读直拼)
        private String contentType; // MIME
        private long size;          // 字节
        private Instant uploadedAt; // 上传时间
    }
```

(`List`/`ArrayList`/`Instant` 已 import;`@Data` 已 import。)

- [ ] **Step 2: 编译验证**

Run: `docker compose exec -T backend mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/potato/wiki/WikiPage.java
git commit -m "$(printf 'feat(wiki): WikiPage 挂载 OSS 资产 assets[]\n\nCo-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>')"
```

---

## Task 3: MinIO 基础设施 — compose + 依赖 + 配置 + `StorageService`

**Files:**
- Modify: `docker-compose.yml`
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/potato/storage/StorageService.java`

- [ ] **Step 1: docker-compose 加 minio 服务 + backend 环境变量 + volume**

`docker-compose.yml` —— 在 `mongo` 服务之后加 `minio` 服务:

```yaml
  minio:
    image: minio/minio
    container_name: potato-minio
    command: server /data --console-address ":9001"
    environment:
      - MINIO_ROOT_USER=minioadmin
      - MINIO_ROOT_PASSWORD=minioadmin
    volumes:
      - minio-data:/data
    ports:
      - "9000:9000"
      - "9001:9001"
    restart: unless-stopped
```

`backend` 服务的 `environment` 增补(内部连 `minio:9000`,但返给浏览器的 URL 用 `localhost:9000`):
```yaml
    environment:
      - MONGODB_URI=mongodb://mongo:27017/potato
      - MINIO_ENDPOINT=http://minio:9000
      - MINIO_PUBLIC_ENDPOINT=http://localhost:9000
      - MINIO_ACCESS_KEY=minioadmin
      - MINIO_SECRET_KEY=minioadmin
      - MINIO_BUCKET=potato-assets
```

`backend` 的 `depends_on` 加 `minio`:
```yaml
    depends_on:
      - mongo
      - minio
```

文件末尾 `volumes:` 块加一行:
```yaml
  minio-data:
```

- [ ] **Step 2: pom.xml 加 MinIO SDK 依赖**

`backend/pom.xml` —— 在 lombok 依赖之后、`</dependencies>` 之前加:
```xml
        <dependency>
            <groupId>io.minio</groupId>
            <artifactId>minio</artifactId>
            <version>8.5.12</version>
        </dependency>
```

- [ ] **Step 3: application.yml 加 minio 配置 + multipart 上限**

`backend/src/main/resources/application.yml` —— `spring:` 下加 `servlet.multipart`,文件末尾加 `app.minio`:

```yaml
spring:
  application:
    name: potato-backend
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/potato}
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```
并在文件末尾(与 `app.jwt` 同级,即 `app:` 下)加:
```yaml
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    public-endpoint: ${MINIO_PUBLIC_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket: ${MINIO_BUCKET:potato-assets}
```

- [ ] **Step 4: 新建 StorageService**

Create `backend/src/main/java/com/potato/storage/StorageService.java`:
```java
package com.potato.storage;

import com.potato.wiki.WikiPage;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

/** 唯一封装对象存储(MinIO)的地方。上传走后端代理,开发期桶公开读。 */
@Service
public class StorageService {

    private final MinioClient client;
    private final String bucket;
    private final String publicEndpoint;

    public StorageService(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.public-endpoint}") String publicEndpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey,
            @Value("${app.minio.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.publicEndpoint = publicEndpoint;
    }

    /** 启动确保桶存在并设匿名只读(开发期 demo/图片可直接被浏览器加载)。 */
    @PostConstruct
    void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
        client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
    }

    /** 上传一个文件,返回可写进 WikiPage 的 Asset 元数据。 */
    public WikiPage.Asset upload(String pageId, MultipartFile file) throws Exception {
        String safeName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String key = "wiki/" + pageId + "/" + UUID.randomUUID() + "-" + safeName;
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                .build());
        WikiPage.Asset a = new WikiPage.Asset();
        a.setName(safeName);
        a.setObjectKey(key);
        a.setUrl(publicEndpoint + "/" + bucket + "/" + key);
        a.setContentType(file.getContentType());
        a.setSize(file.getSize());
        a.setUploadedAt(Instant.now());
        return a;
    }

    public void delete(String objectKey) throws Exception {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }
}
```

- [ ] **Step 5: 重启后端 + 起 minio,验证桶就绪、无启动错误**

Run:
```bash
docker compose up -d minio && docker compose restart backend && docker compose logs --tail=40 backend
```
Expected: 日志见 `Started PotatoApplication`,无 MinIO 连接异常。浏览器开 `http://localhost:9001`(minioadmin/minioadmin)能看到 `potato-assets` 桶。

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml backend/pom.xml backend/src/main/resources/application.yml backend/src/main/java/com/potato/storage/StorageService.java
git commit -m "$(printf 'feat(storage): 引入 MinIO + StorageService(开发期公开读)\n\nCo-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>')"
```

---

## Task 4: 后端 API — `update` 支持改 path + 资产上传/删除

**Files:**
- Modify: `backend/src/main/java/com/potato/wiki/WikiService.java`
- Modify: `backend/src/test/java/com/potato/wiki/WikiServiceTest.java`
- Modify: `backend/src/main/java/com/potato/wiki/WikiController.java`

- [ ] **Step 1: 写失败测试(update 改 path + 查重)**

`WikiServiceTest.java` —— 在 category 测试之后加:
```java
    // ---- update 改 path(晋升用) ----

    @Test
    void update_moves_path_when_changed() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        existing.setPath("/tech-proposals/r1/node_1");
        existing.setVersion(1);
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.findByPath("/experience/cache-avalanche")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.update("id1", "经验", "/experience/cache-avalanche", "正文",
                "experience", List.of("cache"), "/experience", "u");
        assertThat(p.getPath()).isEqualTo("/experience/cache-avalanche");
        assertThat(p.getCategory()).isEqualTo("experience");
    }

    @Test
    void update_rejects_path_conflict() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        existing.setPath("/tech-proposals/r1/node_1");
        WikiPage other = new WikiPage();
        other.setId("id2");
        other.setPath("/experience/taken");
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.findByPath("/experience/taken")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.update("id1", "x", "/experience/taken", null, null, null, null, "u"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("路径已存在");
    }
```

- [ ] **Step 2: 跑测试,验证失败**

Run: `docker compose exec -T backend mvn -q -Dtest=WikiServiceTest test`
Expected: FAIL —— `update` 旧签名无 path 参数,编译错误。

- [ ] **Step 3: update 加 path 参数 + 查重,并改两处调用点**

`WikiService.java` 的 `update` 方法**整体替换**为(在 `title` 后加 `path` 参数):
```java
    public WikiPage update(String id, String title, String path, String content, String category, List<String> tags, String parentPath, String userId) {
        WikiPage page = get(id);
        if (title != null) page.setTitle(title);
        if (path != null && !path.isBlank() && !path.equals(page.getPath())) {
            repository.findByPath(path).ifPresent(p -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "路径已存在: " + path);
            });
            page.setPath(path);
        }
        if (content != null) page.setContent(content);
        if (category != null) page.setCategory(validateCategory(category));
        if (tags != null) page.setTags(tags);
        if (parentPath != null) page.setParentPath(parentPath);
        page.setVersion(page.getVersion() + 1);
        page.setUpdatedBy(userId);
        page.setUpdatedAt(Instant.now());
        return repository.save(page);
    }
```
同文件 `upsertByPath` 内调用 `update` 处补 `path` 参数(第 4 个位置):
```java
        return repository.findByPath(path)
                .map(existing -> update(existing.getId(), title, path, content, category, tags, parentPath, userId))
                .orElseGet(() -> create(title, path, parentPath, content, category, tags, userId));
```
`WikiController.java` 的 `update` 端点调用补 `req.path()`:
```java
        return service.update(id, req.title(), req.path(), req.content(), req.category(), req.tags(), req.parentPath(), user.getId());
```

- [ ] **Step 4: 跑测试,验证 update 测试通过**

Run: `docker compose exec -T backend mvn -q -Dtest=WikiServiceTest test`
Expected: PASS(含 update 改 path/查重;原 upsert 测试不破——upsert 时 path 与原 path 相等,不进查重分支)。

- [ ] **Step 5: 写失败测试(addAsset/removeAsset)**

`WikiServiceTest.java` —— 在 update 改 path 测试之后、类结束 `}` 之前加:
```java
    // ---- assets ----

    @Test
    void addAsset_appends_and_saves() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage.Asset a = new WikiPage.Asset();
        a.setObjectKey("wiki/id1/x-demo.html");
        a.setName("demo.html");
        WikiPage p = service.addAsset("id1", a);
        assertThat(p.getAssets()).hasSize(1);
        assertThat(p.getAssets().get(0).getObjectKey()).isEqualTo("wiki/id1/x-demo.html");
    }

    @Test
    void removeAsset_drops_matching_key() {
        WikiPage existing = new WikiPage();
        existing.setId("id1");
        WikiPage.Asset a = new WikiPage.Asset();
        a.setObjectKey("wiki/id1/x-demo.html");
        existing.getAssets().add(a);
        when(repo.findById("id1")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        WikiPage p = service.removeAsset("id1", "wiki/id1/x-demo.html");
        assertThat(p.getAssets()).isEmpty();
    }
```

- [ ] **Step 6: 跑测试,验证失败**

Run: `docker compose exec -T backend mvn -q -Dtest=WikiServiceTest test`
Expected: FAIL —— `addAsset`/`removeAsset` 方法不存在,编译错误。

- [ ] **Step 7: 实现 addAsset/removeAsset**

`WikiService.java` —— 在 `update` 方法之后加:
```java
    /** 给页追加一个资产(已上传到 MinIO 后调用)。 */
    public WikiPage addAsset(String id, WikiPage.Asset asset) {
        WikiPage page = get(id);
        page.getAssets().add(asset);
        page.setUpdatedAt(Instant.now());
        return repository.save(page);
    }

    /** 按 objectKey 移除页上的资产记录(MinIO 中的对象删除由调用方处理)。 */
    public WikiPage removeAsset(String id, String objectKey) {
        WikiPage page = get(id);
        page.getAssets().removeIf(a -> a.getObjectKey() != null && a.getObjectKey().equals(objectKey));
        page.setUpdatedAt(Instant.now());
        return repository.save(page);
    }
```

- [ ] **Step 8: 跑测试,验证通过**

Run: `docker compose exec -T backend mvn -q -Dtest=WikiServiceTest test`
Expected: PASS。

- [ ] **Step 9: Controller 加上传/删除端点**

`WikiController.java` —— 加 import:
```java
import com.potato.storage.StorageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
```
构造器注入 StorageService(改字段 + 构造器):
```java
    private final WikiService service;
    private final PermissionService permissionService;
    private final StorageService storageService;

    public WikiController(WikiService service, PermissionService permissionService, StorageService storageService) {
        this.service = service;
        this.permissionService = permissionService;
        this.storageService = storageService;
    }
```
在 `search` 方法之后、类结束 `}` 之前加端点:
```java
    /** 上传资产到某页(后端代理 → MinIO),返回更新后的页。 */
    @PostMapping("/pages/{id}/assets")
    public WikiPage uploadAsset(@AuthenticationPrincipal User user, @PathVariable String id,
                               @RequestPart("file") MultipartFile file) throws Exception {
        permissionService.check(user, "wiki", "edit");
        WikiPage.Asset asset = storageService.upload(id, file);
        return service.addAsset(id, asset);
    }

    /** 删除某页的一个资产(MinIO 对象 + 页记录),objectKey 作查询参数。 */
    @DeleteMapping("/pages/{id}/assets")
    public WikiPage deleteAsset(@AuthenticationPrincipal User user, @PathVariable String id,
                               @RequestParam("objectKey") String objectKey) throws Exception {
        permissionService.check(user, "wiki", "edit");
        storageService.delete(objectKey);
        return service.removeAsset(id, objectKey);
    }
```

- [ ] **Step 6: 重启后端,编译验证**

Run: `docker compose restart backend && docker compose logs --tail=30 backend`
Expected: `Started PotatoApplication`,无编译错误。

- [ ] **Step 7: 冒烟上传(manual)**

Run(先取 admin token):
```bash
T=$(curl -s http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
# 造一个测试页拿 id(或用已有页 id),再传文件:
echo "<h1>demo</h1>" > /tmp/demo.html
curl -s -X POST "http://localhost:8080/api/v1/wiki/pages/<PAGE_ID>/assets" -H "Authorization: Bearer $T" -F "file=@/tmp/demo.html"
```
Expected: 返回的 JSON 里 `assets[0].url` 形如 `http://localhost:9000/potato-assets/wiki/<PAGE_ID>/...-demo.html`,该 URL 浏览器可直接打开。

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/potato/wiki/WikiService.java backend/src/test/java/com/potato/wiki/WikiServiceTest.java backend/src/main/java/com/potato/wiki/WikiController.java
git commit -m "$(printf 'feat(wiki): 资产上传/删除 API(后端代理 MinIO)\n\nCo-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>')"
```

---

## Task 5: MCP — `search_knowledge` / `write_knowledge` 加 category

**Files:**
- Modify: `mcp-server/src/tools.ts`

> 改已有工具参数,重启 mcp 即生效、无需重连。

- [ ] **Step 1: search_knowledge 加 category 入参 + 透传 + 返回带 category**

`tools.ts` `search_knowledge` 的入参对象加一项(在 `limit` 之后):
```ts
      category: z
        .enum(['doc', 'asset', 'standard', 'experience'])
        .optional()
        .describe('按资产分类过滤:doc/asset/standard/experience'),
```
handler 签名加 `category`,并在 qs 拼接 + 返回项加 category:
```ts
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
```

- [ ] **Step 2: write_knowledge 加 category 入参 + body 透传**

`tools.ts` `write_knowledge` 的入参对象加一项(在 `tags` 之后):
```ts
      category: z
        .enum(['doc', 'asset', 'standard', 'experience'])
        .optional()
        .describe('资产分类:doc(默认)/asset(可复用代码)/standard(代码规范)/experience(先验经验)'),
```
handler 签名加 `category`,body 带上:
```ts
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
```

- [ ] **Step 3: tsc 验证**

Run: `docker compose exec -T mcp-server npx tsc --noEmit`
Expected: 无类型错误(无输出)。

- [ ] **Step 4: Commit**

```bash
git add mcp-server/src/tools.ts
git commit -m "$(printf 'feat(mcp): search/write_knowledge 加 category(支持 experience 检索与写入)\n\nCo-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>')"
```

---

## Task 6: 前端 types + client + api

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/api/wiki.ts`

- [ ] **Step 1: types 加 WikiAsset + WikiPageItem 扩展**

`types/index.ts` —— 把现有 `WikiPageItem` 接口替换为(加 `category`/`assets`),并在其前加 `WikiAsset`:
```ts
export interface WikiAsset {
  name: string
  objectKey: string
  url: string
  contentType?: string
  size?: number
  uploadedAt?: string
}

export interface WikiPageItem {
  id: string
  projectId?: string
  title: string
  path: string
  parentPath?: string
  content: string
  category?: string // doc | asset | standard | experience
  tags: string[]
  assets?: WikiAsset[]
  status: string
  version: number
  updatedAt: string
}
```

- [ ] **Step 2: client.ts 识别 FormData(不强设 Content-Type)**

`api/client.ts` —— 把设 Content-Type 的判断改为排除 FormData:
```ts
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
```

- [ ] **Step 3: api/wiki.ts 加 category / 资产函数 / search opts**

`api/wiki.ts` —— 整文件替换为:
```ts
// 数据访问层:知识库接口
import { request } from './client'
import type { WikiPageItem } from '../types'

export interface WikiInput {
  title: string
  path: string
  parentPath?: string
  content?: string
  category?: string // doc | asset | standard | experience
  tags?: string[]
}

export interface SearchOpts {
  category?: string
  matchMode?: string
  includeTmp?: boolean
}

export function listWiki(): Promise<WikiPageItem[]> {
  return request<WikiPageItem[]>('/wiki/pages')
}

export function getWiki(id: string): Promise<WikiPageItem> {
  return request<WikiPageItem>(`/wiki/pages/${id}`)
}

export function searchWiki(q: string, opts: SearchOpts = {}): Promise<WikiPageItem[]> {
  const qs = new URLSearchParams()
  if (q) qs.set('q', q)
  if (opts.category) qs.set('category', opts.category)
  if (opts.matchMode) qs.set('match_mode', opts.matchMode)
  if (opts.includeTmp) qs.set('include_tmp', 'true')
  return request<WikiPageItem[]>(`/wiki/search?${qs.toString()}`)
}

export function createWiki(input: WikiInput): Promise<WikiPageItem> {
  return request<WikiPageItem>('/wiki/pages', { method: 'POST', body: JSON.stringify(input) })
}

export function updateWiki(id: string, input: Partial<WikiInput>): Promise<WikiPageItem> {
  return request<WikiPageItem>(`/wiki/pages/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function uploadAsset(id: string, file: File): Promise<WikiPageItem> {
  const fd = new FormData()
  fd.append('file', file)
  return request<WikiPageItem>(`/wiki/pages/${id}/assets`, { method: 'POST', body: fd })
}

export function deleteAsset(id: string, objectKey: string): Promise<WikiPageItem> {
  return request<WikiPageItem>(`/wiki/pages/${id}/assets?objectKey=${encodeURIComponent(objectKey)}`, {
    method: 'DELETE',
  })
}
```

- [ ] **Step 4: tsc 验证**

Run: `docker compose exec -T frontend npx tsc --noEmit`
Expected: 无类型错误。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/client.ts frontend/src/api/wiki.ts
git commit -m "$(printf 'feat(frontend): wiki 类型/HTTP/api 支持 category 与资产上传\n\nCo-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>')"
```

---

## Task 7: 前端 UI — category 选择 / 资产上传 / 筛选 / 晋升

**Files:**
- Modify: `frontend/src/features/useWikiEditor.ts`
- Modify: `frontend/src/features/useWiki.ts`
- Modify: `frontend/src/pages/WikiEditPage.tsx`
- Modify: `frontend/src/pages/WikiPage.tsx`
- Modify: `frontend/src/components/WikiTree.tsx`

- [ ] **Step 1: useWikiEditor 加 category + 资产状态/操作**

`features/useWikiEditor.ts` —— 整文件替换为:
```ts
// 逻辑层:知识库文档编辑(加载 + 保存 + 资产)
import { useEffect, useState } from 'react'
import { createWiki, deleteAsset, getWiki, updateWiki, uploadAsset, type WikiInput } from '../api/wiki'
import type { WikiAsset, WikiPageItem } from '../types'

const EMPTY: WikiInput = { title: '', path: '', parentPath: undefined, content: '', category: 'doc', tags: [] }

export function useWikiEditor(id?: string) {
  const [form, setForm] = useState<WikiInput | null>(id ? null : { ...EMPTY })
  const [assets, setAssets] = useState<WikiAsset[]>([])
  const [loading, setLoading] = useState(!!id)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    getWiki(id)
      .then((p: WikiPageItem) => {
        setForm({
          title: p.title,
          path: p.path,
          parentPath: p.parentPath,
          content: p.content,
          category: p.category ?? 'doc',
          tags: p.tags,
        })
        setAssets(p.assets ?? [])
      })
      .finally(() => setLoading(false))
  }, [id])

  const save = async (): Promise<WikiPageItem> => {
    if (!form) throw new Error('表单未就绪')
    setSaving(true)
    try {
      return id ? await updateWiki(id, form) : await createWiki(form)
    } finally {
      setSaving(false)
    }
  }

  const upload = async (file: File): Promise<void> => {
    if (!id) throw new Error('请先保存文档,再上传资产')
    const updated = await uploadAsset(id, file)
    setAssets(updated.assets ?? [])
  }

  const removeAsset = async (objectKey: string): Promise<void> => {
    if (!id) return
    const updated = await deleteAsset(id, objectKey)
    setAssets(updated.assets ?? [])
  }

  return { form, setForm, assets, loading, saving, save, upload, removeAsset }
}
```

- [ ] **Step 2: WikiEditPage 加 category 选择器 + 资产上传区**

`pages/WikiEditPage.tsx` —— ① import 增补:
```ts
import { Button, Input, Segmented, Select, Space, Spin, Upload, message } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
```
② 取出新返回值:
```ts
  const { form, setForm, assets, loading, saving, save, upload, removeAsset } = useWikiEditor(id)
```
③ 在标签 `Select`(mode="tags")之后,同一个 `<Space wrap>` 内加 category 选择器:
```tsx
          <Select
            value={form.category ?? 'doc'}
            onChange={(category: string) => set({ category })}
            style={{ width: 160 }}
            options={[
              { value: 'doc', label: 'doc 通用' },
              { value: 'asset', label: 'asset 可复用' },
              { value: 'standard', label: 'standard 规范' },
              { value: 'experience', label: 'experience 经验' },
            ]}
          />
```
④ 在外层第一个 `<Space direction="vertical">` 块的末尾(标签/路径那个 `<Space wrap>` 之后),加资产区(仅编辑已有页时显示):
```tsx
        {id && (
          <Space wrap>
            <Upload
              showUploadList={false}
              beforeUpload={(file) => {
                upload(file).then(() => message.success('已上传')).catch((e) => message.error(e.message))
                return false
              }}
            >
              <Button icon={<UploadOutlined />}>上传资产</Button>
            </Upload>
            {assets.map((a) => (
              <Space key={a.objectKey} size={4}>
                <a href={a.url} target="_blank" rel="noreferrer">{a.name}</a>
                <Button size="small" type="link" onClick={() => navigator.clipboard.writeText(a.url)}>复制URL</Button>
                <Button size="small" type="link" danger onClick={() => removeAsset(a.objectKey)}>删除</Button>
              </Space>
            ))}
          </Space>
        )}
```

- [ ] **Step 3: useWiki 加 category 筛选**

`features/useWiki.ts` —— 整文件替换为:
```ts
// 逻辑层:知识库列表 / 搜索 / 筛选 / 选中(编辑保存逻辑见 useWikiEditor)
import { useCallback, useEffect, useState } from 'react'
import { listWiki, searchWiki } from '../api/wiki'
import type { WikiPageItem } from '../types'

export function useWiki() {
  const [pages, setPages] = useState<WikiPageItem[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<string | undefined>(undefined)

  const run = useCallback(async (q: string, cat?: string) => {
    setLoading(true)
    try {
      if (!q.trim() && !cat) {
        setPages(await listWiki())
      } else {
        setPages(await searchWiki(q, { category: cat }))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    run(query, category)
  }, [run, query, category])

  const search = (q: string) => setQuery(q)
  const filterCategory = (cat?: string) => setCategory(cat)

  const selected = pages.find((p) => p.id === selectedId) ?? null

  return { pages, loading, selected, selectedId, setSelectedId, search, category, filterCategory, reload: () => run(query, category) }
}
```

- [ ] **Step 4: WikiPage 加 category 筛选下拉 + 资产展示 + 晋升按钮**

`pages/WikiPage.tsx` —— 整文件替换为:
```tsx
// 视图层:知识库页(左目录树 + 搜索 + 分类筛选 + 右文档查看/资产/晋升)
import { useState } from 'react'
import { Button, Card, Empty, Input, Modal, Select, Space, Tag, Typography, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useWiki } from '../features/useWiki'
import { updateWiki } from '../api/wiki'
import WikiTree from '../components/WikiTree'
import MarkdownView from '../components/MarkdownView'

const { Title, Text } = Typography
const { Search } = Input

const CATEGORY_OPTIONS = [
  { value: '', label: '全部分类' },
  { value: 'doc', label: 'doc 通用' },
  { value: 'asset', label: 'asset 可复用' },
  { value: 'standard', label: 'standard 规范' },
  { value: 'experience', label: 'experience 经验' },
]

export default function WikiPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { pages, loading, selected, selectedId, setSelectedId, search, category, filterCategory, reload } = useWiki()
  const canEdit = user?.functions.some((f) => f === 'admin' || f === 'product') ?? false
  const [promoting, setPromoting] = useState(false)
  const [promoteCat, setPromoteCat] = useState('experience')

  const isTmp = selected?.tags?.includes('tmp') ?? false

  const doPromote = async () => {
    if (!selected) return
    try {
      await updateWiki(selected.id, {
        category: promoteCat,
        tags: (selected.tags ?? []).filter((t) => t !== 'tmp'),
      })
      message.success('已晋升')
      setPromoting(false)
      reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '晋升失败')
    }
  }

  return (
    <div style={{ display: 'flex', gap: 16, height: 'calc(100vh - 140px)' }}>
      <Card
        size="small"
        title="知识库"
        style={{ width: 280, overflow: 'auto', flexShrink: 0 }}
        extra={canEdit ? <Button size="small" type="primary" onClick={() => navigate('/wiki/new')}>新建</Button> : null}
      >
        <Search placeholder="搜索标题/内容/标签" onSearch={search} allowClear style={{ marginBottom: 8 }} />
        <Select
          value={category ?? ''}
          onChange={(v: string) => filterCategory(v || undefined)}
          options={CATEGORY_OPTIONS}
          style={{ width: '100%', marginBottom: 12 }}
        />
        <WikiTree pages={pages} selectedId={selectedId} onSelect={setSelectedId} />
      </Card>

      <Card style={{ flex: 1, overflow: 'auto' }}>
        {selected ? (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Title level={3} style={{ margin: 0 }}>{selected.title}</Title>
              <Space>
                {canEdit && isTmp && <Button onClick={() => setPromoting(true)}>晋升</Button>}
                {canEdit && <Button onClick={() => navigate(`/wiki/${selected.id}/edit`)}>编辑</Button>}
              </Space>
            </div>
            <Space style={{ margin: '8px 0' }} wrap>
              <Text type="secondary" code>{selected.path}</Text>
              {selected.category && <Tag color="blue">{selected.category}</Tag>}
              {selected.tags.map((t) => <Tag key={t}>{t}</Tag>)}
            </Space>
            {(selected.assets?.length ?? 0) > 0 && (
              <Space style={{ marginBottom: 8 }} wrap>
                <Text type="secondary">资产:</Text>
                {selected.assets!.map((a) => (
                  <a key={a.objectKey} href={a.url} target="_blank" rel="noreferrer">{a.name}</a>
                ))}
              </Space>
            )}
            <MarkdownView content={selected.content} />
          </>
        ) : (
          <Empty description={loading ? '加载中…' : '选择左侧文档查看,或点击「新建」'} />
        )}
      </Card>

      <Modal title="晋升为正式知识" open={promoting} onOk={doPromote} onCancel={() => setPromoting(false)} okText="晋升">
        <p>去掉 <Tag>tmp</Tag> 标签,并归入正式分类:</p>
        <Select
          value={promoteCat}
          onChange={setPromoteCat}
          style={{ width: 220 }}
          options={[
            { value: 'experience', label: 'experience 先验经验' },
            { value: 'standard', label: 'standard 代码规范' },
            { value: 'asset', label: 'asset 可复用代码' },
            { value: 'doc', label: 'doc 通用文档' },
          ]}
        />
      </Modal>
    </div>
  )
}
```

- [ ] **Step 5: WikiTree 节点标题挂 category 小标签**

`components/WikiTree.tsx` —— ① import 加 `Tag`:
```ts
import { Tag, Tree } from 'antd'
```
② `build` 函数的 `title` 改为带分类标签:
```tsx
  const build = (p: WikiPageItem): DataNode => ({
    key: p.id,
    title: (
      <span>
        {p.title}
        {p.category && p.category !== 'doc' ? <Tag style={{ marginLeft: 6 }}>{p.category}</Tag> : null}
      </span>
    ),
    children: (childrenOf[p.path] ?? []).map(build),
  })
```

- [ ] **Step 6: tsc 验证**

Run: `docker compose exec -T frontend npx tsc --noEmit`
Expected: 无类型错误。

- [ ] **Step 7: 前端界面统一验证(manual)**

浏览器 `http://localhost:5173` 登录 admin/admin123 → 知识库:
1. 新建一篇 category=`experience` 的页,保存。
2. 进编辑页,上传一张图/一个 html,确认列出资产、点链接能在新标签打开(URL 为 `localhost:9000/...`)。
3. 左侧用分类下拉筛 `experience`,只剩该页。
4. 对一篇带 `tmp` 标签的页(如某 `/tech-proposals/...` 技术方案),点「晋升」选 experience → 确认 tmp 消失、分类变 experience、筛选 experience 能搜到。

- [ ] **Step 8: Commit**

```bash
git add frontend/src/features/useWikiEditor.ts frontend/src/features/useWiki.ts frontend/src/pages/WikiEditPage.tsx frontend/src/pages/WikiPage.tsx frontend/src/components/WikiTree.tsx
git commit -m "$(printf 'feat(frontend): category 选择/资产上传/分类筛选/晋升 UI\n\nCo-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>')"
```

---

## 收尾(全部 task 完成后)

- [ ] **更新开发文档:** `docs/平台开发说明.md` 增一段「知识库治理(2026-06-13):category 扩 experience、MinIO 资产层、晋升机制、MCP/前端 category」。
- [ ] **沉淀经验(Herness 第7步):** 用 `write_knowledge` 把「MinIO 简单接入(后端代理 + 公开读 + 内外网 endpoint 区分)」沉淀为 `/platform/minio-storage`(category=experience),供日后复用。
- [ ] **回标结构树:** 用 `relate_requirement_arch` 把本需求回标到知识域相关结构树叶子(impl_status=done)。
- [ ] **处理残留:** 确认 working tree 干净(B1 改动已随 Task 1/4 提交);`.claude/settings.local.json`、`CLAUDE.md` 等无关改动按需单独处理或还原。
