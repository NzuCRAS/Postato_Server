// 逻辑层:资产库列表 + 按名筛选 + 重载
import { useCallback, useEffect, useMemo, useState } from 'react'
import { listAssets } from '../api/asset'
import type { AssetItem } from '../types'

export function useAssets() {
  const [assets, setAssets] = useState<AssetItem[]>([])
  const [loading, setLoading] = useState(true)
  const [keyword, setKeyword] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setAssets(await listAssets())
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  const filtered = useMemo(() => {
    const k = keyword.trim().toLowerCase()
    if (!k) return assets
    return assets.filter((a) => a.name.toLowerCase().includes(k) || a.objectKey.toLowerCase().includes(k))
  }, [assets, keyword])

  const orphanCount = useMemo(() => assets.filter((a) => a.orphan).length, [assets])

  return { assets: filtered, total: assets.length, orphanCount, loading, keyword, setKeyword, reload }
}
