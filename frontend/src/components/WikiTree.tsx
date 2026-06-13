// 视图层:知识库目录树(折线连接 + 整行可点)
import { Tag, Tree } from 'antd'
import type { DataNode } from 'antd/es/tree'
import type { WikiPageItem } from '../types'

function toTreeData(pages: WikiPageItem[]): DataNode[] {
  const exists: Record<string, boolean> = {}
  pages.forEach((p) => {
    exists[p.path] = true
  })

  const childrenOf: Record<string, WikiPageItem[]> = {}
  const roots: WikiPageItem[] = []
  pages.forEach((p) => {
    if (p.parentPath && exists[p.parentPath]) {
      ;(childrenOf[p.parentPath] ??= []).push(p)
    } else {
      roots.push(p)
    }
  })

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

  return roots.map(build)
}

export default function WikiTree({
  pages,
  selectedId,
  onSelect,
}: {
  pages: WikiPageItem[]
  selectedId: string | null
  onSelect: (id: string) => void
}) {
  return (
    <Tree
      showLine={{ showLeafIcon: false }}
      blockNode
      treeData={toTreeData(pages)}
      selectedKeys={selectedId ? [selectedId] : []}
      onSelect={(keys) => keys[0] && onSelect(String(keys[0]))}
      defaultExpandAll
    />
  )
}
