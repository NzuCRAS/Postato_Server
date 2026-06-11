package com.potato.archnode;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 结构树请求体。 */
public final class ArchNodeDtos {

    private ArchNodeDtos() {}

    /** 新建节点 */
    public record CreateNodeRequest(
            @JsonProperty("parent_id") String parentId,
            String title,
            String layer,
            String type,
            String description,
            List<String> tags,
            @JsonProperty("related_docs") List<String> relatedDocs,
            @JsonProperty("related_code") List<String> relatedCode,
            @JsonProperty("related_requirements") List<String> relatedRequirements) {
    }

    /** 更新节点(不改 path;title 改动不联动 path,path 为稳定标识) */
    public record UpdateNodeRequest(
            String title,
            String layer,
            String type,
            String description,
            List<String> tags,
            @JsonProperty("related_docs") List<String> relatedDocs,
            @JsonProperty("related_code") List<String> relatedCode,
            @JsonProperty("related_requirements") List<String> relatedRequirements) {
    }

    /** 移动(改父节点,递归重算物化路径) */
    public record MoveNodeRequest(@JsonProperty("new_parent_id") String newParentId) {
    }

    /** .project.yaml 同步:一个仓库的模块声明(阶段3用) */
    public record SyncRequest(
            @JsonProperty("repo_id") String repoId,
            List<SyncModule> modules) {
    }

    public record SyncModule(
            String node,                 // 归属的管理节点物化路径(L2/L3)
            String path,                 // 该模块在结构树的路径(相对/绝对)
            String title,
            String type,
            List<String> tags,
            @JsonProperty("related_docs") List<String> relatedDocs,
            @JsonProperty("related_code") List<String> relatedCode) {
    }

    /** 递归 upsert 一棵结构子树(管理树/任意层):按 (projectId,path) 幂等,自动按父推断 layer。 */
    public record UpsertTreeRequest(
            @JsonProperty("parent_path") String parentPath,   // 空=挂到根(建 L0)
            List<TreeNode> nodes) {
    }

    public record TreeNode(
            String title,
            String layer,                // 显式优先;缺省按父层 +1(根=L0)
            String type,
            String description,
            List<String> tags,
            @JsonProperty("related_docs") List<String> relatedDocs,
            @JsonProperty("related_code") List<String> relatedCode,
            @JsonProperty("impl_status") String implStatus,   // 叶子:planned|in_progress|done(缺省 planned);非叶子由后端聚合
            List<TreeNode> children) {
    }
}
