// 视图层:资产库页(OSS 对象网格 + 缩略图/引用页/孤儿标 + 按名筛选)
import { Badge, Button, Card, Empty, Input, Space, Spin, Statistic, Tag, Tooltip, Typography } from 'antd'
import { FileOutlined, ReloadOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { useAssets } from '../features/useAssets'
import type { AssetItem } from '../types'

const { Text } = Typography
const { Search } = Input

function humanSize(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function isImage(a: AssetItem): boolean {
  return (a.contentType ?? '').startsWith('image/') || /\.(png|jpe?g|gif|webp|svg)$/i.test(a.objectKey)
}

function AssetCard({ a }: { a: AssetItem }) {
  const cover = isImage(a) ? (
    <div style={{ height: 120, background: '#fafafa', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
      <img src={a.url} alt={a.name} style={{ maxHeight: 120, maxWidth: '100%', objectFit: 'contain' }} />
    </div>
  ) : (
    <div style={{ height: 120, background: '#fafafa', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <FileOutlined style={{ fontSize: 40, color: '#bbb' }} />
    </div>
  )

  return (
    <Badge.Ribbon text="孤儿" color="red" style={{ display: a.orphan ? undefined : 'none' }}>
      <Card size="small" cover={cover} styles={{ body: { padding: 12 } }}>
        <Tooltip title={a.objectKey}>
          <a href={a.url} target="_blank" rel="noreferrer" style={{ fontWeight: 500, display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {a.name}
          </a>
        </Tooltip>
        <div style={{ margin: '6px 0' }}>
          <Text type="secondary" style={{ fontSize: 12 }}>{humanSize(a.size)}</Text>
          {a.contentType && <Tag style={{ marginLeft: 6 }}>{a.contentType}</Tag>}
        </div>
        {a.orphan ? (
          <Text type="danger" style={{ fontSize: 12 }}>未被任何页引用</Text>
        ) : (
          <div style={{ fontSize: 12 }}>
            <Text type="secondary">被 {a.referencingPages.length} 页引用:</Text>
            <div>
              {a.referencingPages.map((p) => (
                <Link key={p.id} to={`/wiki`} style={{ marginRight: 8 }} title={p.path}>{p.title}</Link>
              ))}
            </div>
          </div>
        )}
      </Card>
    </Badge.Ribbon>
  )
}

export default function AssetsPage() {
  const { assets, total, orphanCount, loading, keyword, setKeyword, reload } = useAssets()

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Space size="large">
          <Statistic title="对象总数" value={total} />
          <Statistic title="孤儿资产" value={orphanCount} valueStyle={{ color: orphanCount > 0 ? '#cf1322' : undefined }} />
        </Space>
        <Space>
          <Search placeholder="按名称 / key 筛选" allowClear value={keyword} onChange={(e) => setKeyword(e.target.value)} style={{ width: 240 }} />
          <Button icon={<ReloadOutlined />} onClick={reload}>刷新</Button>
        </Space>
      </div>

      {loading ? (
        <Spin style={{ display: 'block', marginTop: 80 }} />
      ) : assets.length === 0 ? (
        <Empty description={keyword ? '无匹配资产' : '桶内暂无对象'} />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 16 }}>
          {assets.map((a) => <AssetCard key={a.objectKey} a={a} />)}
        </div>
      )}
    </div>
  )
}
