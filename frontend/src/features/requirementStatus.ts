// 需求状态的展示映射(视图辅助:中文标签 + 颜色)
export const STATUS_META: Record<string, { label: string; color: string }> = {
  draft: { label: '草稿', color: 'default' },
  clarifying: { label: '澄清中', color: 'processing' },
  confirmed: { label: '已确认', color: 'success' },
  deprecated: { label: '已废弃', color: 'error' },
}

export function statusLabel(s: string): string {
  return STATUS_META[s]?.label ?? s
}

export function statusColor(s: string): string {
  return STATUS_META[s]?.color ?? 'default'
}
