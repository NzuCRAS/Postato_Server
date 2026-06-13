// 数据访问层:资产库接口(OSS 对象列表 + 交叉引用)
import { request } from './client'
import type { AssetItem } from '../types'

export function listAssets(): Promise<AssetItem[]> {
  return request<AssetItem[]>('/assets')
}
