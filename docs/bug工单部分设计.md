好的，模块 E 作为最后一块拼图，我们遵循“轻量、够用、可追溯”原则，让它无缝嵌入已有体系。

---

# 模块 E：工单与 Bug 反馈系统

## 1. 定位与目标
- **谁用**：测试人员提 Bug；开发人员提技术改进或任务；产品提功能调整；任何人都可创建。
- **核心功能**：创建工单 → 关联需求/模块 → 指派处理人 → 处理/评论 → 关闭。
- **不做**：复杂的 SLA、自动化流转、工时统计、甘特图。

## 2. 数据模型（MongoDB `tickets` 集合）
```javascript
{
  _id: ObjectId,
  projectId: ObjectId,
  type: "bug",                    // bug | task | improvement | question
  title: "请假表单日期选择器无法选择未来月份",
  description: "## 复现步骤\n1. 打开请假表单\n2. 点击日期选择器...",
  
  // 关联信息
  requirementId: ObjectId,        // 可选，关联需求
  devPlanNodeId: "node_1",        // 可选，关联到开发进度树的节点（便于定位模块）
  relatedDocs: ["/wiki/env-setup"], // 关联知识库文档
  
  // 状态与优先级
  status: "open",                 // open | in_progress | resolved | closed
  priority: "high",               // low | medium | high | critical
  severity: "major",              // minor | major | critical (仅 bug 类型有意义)
  
  // 人员
  assigneeId: ObjectId,           // 指派给谁
  reporterId: ObjectId,           // 创建者
  
  // 解决信息
  resolution: "",                 // 解决描述或关闭原因
  resolvedAt: ISODate,
  
  // 时间与版本
  createdAt: ISODate,
  updatedAt: ISODate,
  
  // 评论（嵌入文档，避免单独集合）
  comments: [
    {
      _id: ObjectId,
      userId: ObjectId,
      content: "已确认问题，原因是月份组件版本bug，升级后修复。",
      createdAt: ISODate
    }
  ],
  
  // 附件（可选）
  attachments: [
    { name: "screenshot.png", url: "/uploads/ticket_xx/screenshot.png" }
  ]
}
```

## 3. 页面设计

### 3.1 工单列表页（看板视图）
路由：`/tickets`

- 顶部：筛选栏（状态、优先级、类型、关联需求、处理人）。
- 主体：卡片式看板，按状态分列（**打开 → 处理中 → 已解决 → 已关闭**），可拖动卡片改变状态（可选，MVP 直接通过详情页按钮切换）。
- 每张卡片显示：标题、类型图标（Bug/任务/改进）、优先级颜色条、处理人头像、更新日期。
- 快速创建按钮（浮动 “+” 按钮），跳转创建页。

### 3.2 创建工单页
路由：`/tickets/new`

- **标题**：必填。
- **类型**：单选框（Bug / 任务 / 改进 / 问题）。
- **关联需求**：搜索式下拉选择器（过滤已确认的需求）。
- **关联开发节点**：根据所选需求，动态加载其 `dev_plan` 树，提供节点路径选择（可选）。
- **描述**：Markdown 编辑器，支持贴图（复层用组件）。
- **严重程度/优先级**：下拉。
- **附件上传**：拖拽上传（可选）。
- **提交按钮** → 创建成功跳转工单详情。

### 3.3 工单详情页
路由：`/tickets/:id`

左右布局：
- **左主区域**：
  - 标题、状态徽章、优先级标签、类型图标。
  - 关联信息区：显示关联的需求链接、开发节点路径、知识库文档链接，均可点击跳转。
  - 描述内容（Markdown 渲染）。
  - 附件列表。
- **右侧栏**：
  - 处理人（可修改，下拉选择开发者/测试）。
  - 状态变更按钮（`开始处理`、`标记已解决`、`关闭`）。
  - 严重程度/优先级修改（仅 `admin` 或 `product` 可调）。
- **底部评论区**：每条评论显示用户头像、时间、内容，输入框 + 发送按钮。

## 4. 权限控制（融合我们的职能模型）
根据配置文件规则，定义资源 `ticket` 的操作权限：

| 操作 | 允许职能 | 说明 |
|------|---------|------|
| `create` | 所有认证用户 | 通用 |
| `view_list` | 所有认证用户 | 看板查看 |
| `view_detail` | 所有认证用户 | 详情查看 |
| `update_status` | `development`, `testing`, `admin` | 处理人自己或上述职能可变更状态 |
| `edit_details` | `admin`，或创建者 + `product` | 标题、描述等编辑 |
| `delete` | `admin` | 仅管理员可删除 |
| `assign` | `admin`, `product`, `development` (仅指派自己) | 普通开发者可将工单指派给自己 |
| `comment` | 所有认证用户 | 评论添加 |

**权限中间件**使用同一套 `hasPermission(user, 'ticket', 'update_status')`。

## 5. 与其他模块的联动
- **需求页**：在需求详情下方增加一个“相关工单” Tab，列出所有关联该需求的工单。
- **开发进度树**：在进度树节点详情中，可展示与该节点关联的未关闭工单数，点击跳转。
- **通知**（未来）：当工单状态变更或有人评论时，可通过平台内通知（或企微/邮件）告知相关人。MVP 可暂不实现，但预留 `notification` 集合。

## 6. 实现优先级与 MVP 范围
**MVP 必须做**：
- 工单的 CRUD（创建、查看、编辑状态、评论）。
- 关联需求（可选）。
- 看板列表（筛选、状态列）。
- 权限限制（核心操作权限）。

**二期可做**：
- 关联开发进度树节点。
- 附件上传。
- 评论 @ 提醒。
- 邮件/IM 通知。
