// 视图层:知识库目录树 —— 从文档 path 推导虚拟目录层级(像文件系统);支持节点级 重命名/移动 与 在目录下新建
import { Dropdown, Tag, Tree } from 'antd'
import { FileTextOutlined, FolderOutlined, MoreOutlined } from '@ant-design/icons'
import type { DataNode } from 'antd/es/tree'
import type { MenuProps } from 'antd'
import type { ReactNode } from 'react'
import type { WikiPageItem } from '../types'

export interface WikiNodeMeta {
  isDir: boolean
  path: string
  id?: string
  name: string
}

interface TreeNode extends DataNode {
  isDir: boolean
  nodePath: string
}

interface Props {
  pages: WikiPageItem[]
  selectedId: string | null
  onSelect: (id: string) => void
  canEdit?: boolean
  onMoveNode?: (node: WikiNodeMeta) => void
  onNewInDir?: (dirPath: string) => void
}

/** 父路径:/a/b → /a;/a → ''(根) */
function parentOf(path: string): string {
  const i = path.lastIndexOf('/')
  return i <= 0 ? '' : path.substring(0, i)
}

function buildTree(pages: WikiPageItem[], opts: Pick<Props, 'canEdit' | 'onMoveNode' | 'onNewInDir'>): DataNode[] {
  const roots: TreeNode[] = []
  const byPath = new Map<string, TreeNode>()

  // 给节点标题挂操作菜单(目录:重命名移动 / 在此新建;文档:重命名移动)
  const withActions = (meta: WikiNodeMeta, label: ReactNode): ReactNode => {
    if (!opts.canEdit) return label
    const items: MenuProps['items'] = meta.isDir
      ? [
          { key: 'move', label: '重命名 / 移动目录' },
          { key: 'new', label: '在此新建文档' },
        ]
      : [{ key: 'move', label: '重命名 / 移动' }]
    const onClick: MenuProps['onClick'] = (info) => {
      info.domEvent.stopPropagation()
      if (info.key === 'move') opts.onMoveNode?.(meta)
      else if (info.key === 'new') opts.onNewInDir?.(meta.path)
    }
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
        {label}
        <Dropdown menu={{ items, onClick }} trigger={['click']}>
          <MoreOutlined onClick={(e) => e.stopPropagation()} style={{ color: '#bbb' }} />
        </Dropdown>
      </span>
    )
  }

  // 确保某路径节点存在(默认作为目录,递归建父链),文档命中后再升级
  const ensure = (path: string): TreeNode => {
    const existing = byPath.get(path)
    if (existing) return existing
    const segs = path.split('/').filter(Boolean)
    const name = segs[segs.length - 1] ?? path
    const node: TreeNode = {
      key: `dir:${path}`,
      title: withActions({ isDir: true, path, name }, name),
      nodePath: path,
      isDir: true,
      icon: <FolderOutlined />,
      children: [],
    }
    byPath.set(path, node)
    const pp = parentOf(path)
    ;(pp ? ensure(pp).children! : roots).push(node)
    return node
  }

  ;[...pages]
    .sort((a, b) => a.path.localeCompare(b.path))
    .forEach((p) => {
      const node = ensure(p.path)
      const segs = p.path.split('/').filter(Boolean)
      const name = segs[segs.length - 1] ?? p.path
      // 升级为文档节点(保留可能已有的子节点 —— 该文档恰好也是更深文档的祖先时)
      node.isDir = false
      node.key = p.id
      node.icon = <FileTextOutlined />
      const label: ReactNode = (
        <span>
          {p.title}
          {p.category && p.category !== 'doc' ? <Tag style={{ marginLeft: 6 }}>{p.category}</Tag> : null}
        </span>
      )
      node.title = withActions({ isDir: false, path: p.path, id: p.id, name }, label)
    })

  return roots
}

export default function WikiTree({ pages, selectedId, onSelect, canEdit, onMoveNode, onNewInDir }: Props) {
  return (
    <Tree
      showLine={{ showLeafIcon: false }}
      showIcon
      blockNode
      treeData={buildTree(pages, { canEdit, onMoveNode, onNewInDir })}
      selectedKeys={selectedId ? [selectedId] : []}
      onSelect={(keys) => {
        const k = String(keys[0] ?? '')
        // 目录节点 key 形如 dir:/a/b,只用于展开;文档节点 key 是文档 id,才触发查看
        if (k && !k.startsWith('dir:')) onSelect(k)
      }}
      defaultExpandAll
    />
  )
}
