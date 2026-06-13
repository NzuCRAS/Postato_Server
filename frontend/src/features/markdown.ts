// 逻辑层:Markdown 编辑/渲染的纯逻辑助手
import type { KeyboardEvent } from 'react'

/**
 * 渲染前的安全规范化:给行首有序列表标记(`1.` / `1)`)后补一个空格,
 * 让「1.获取」这类(中文里很常见、但不符合 CommonMark)的写法能被识别为有序列表。
 *
 * 安全边界(尽量零误伤):
 * - 仅匹配「行首(可含缩进)+ 数字 + . 或 )」且其后紧跟非空格、非数字字符;
 *   因此 `1. 已有空格`(后面是空格)、`1.5`(后面是数字)都不会被改动。
 * - 跳过 ``` / ~~~ 围栏代码块内的行,不动代码内容。
 * 注意:缩进层级不做改动(浅缩进仍不会嵌套),需用户用 Tab 打出正确缩进。
 */
export function normalizeOrderedMarkers(src: string): string {
  const lines = src.split('\n')
  let inFence = false
  let fenceChar = ''
  for (let i = 0; i < lines.length; i++) {
    const fence = lines[i].match(/^\s*(```+|~~~+)/)
    if (fence) {
      const ch = fence[1][0]
      if (!inFence) {
        inFence = true
        fenceChar = ch
      } else if (ch === fenceChar) {
        inFence = false
      }
      continue
    }
    if (inFence) continue
    lines[i] = lines[i].replace(/^(\s*\d+[.)])(?=[^\s\d])/, '$1 ')
  }
  return lines.join('\n')
}

/**
 * 让 textarea 里按 Tab 插入 2 空格缩进(而非默认的跳焦点),方便打出嵌套列表所需缩进。
 * - 无选区:在光标处插入 2 空格;
 * - 有选区:给选区覆盖的每一行行首各加 2 空格(多行整体缩进)。
 * Shift+Tab 不拦截(保留默认,便于退出焦点)。
 */
export function handleMarkdownTabIndent(
  e: KeyboardEvent<HTMLTextAreaElement>,
  value: string,
  onChange: (next: string) => void,
): void {
  if (e.key !== 'Tab' || e.shiftKey) return
  e.preventDefault()
  const ta = e.currentTarget
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const INDENT = '  '

  if (start === end) {
    const next = value.slice(0, start) + INDENT + value.slice(end)
    onChange(next)
    requestAnimationFrame(() => {
      try {
        ta.setSelectionRange(start + INDENT.length, start + INDENT.length)
      } catch {
        /* noop */
      }
    })
    return
  }

  // 有选区:对选区涉及的每一行行首加缩进
  const lineStart = value.lastIndexOf('\n', start - 1) + 1
  const selected = value.slice(lineStart, end)
  const indented = selected.replace(/^/gm, INDENT)
  const next = value.slice(0, lineStart) + indented + value.slice(end)
  const added = indented.length - selected.length
  onChange(next)
  requestAnimationFrame(() => {
    try {
      ta.setSelectionRange(lineStart, end + added)
    } catch {
      /* noop */
    }
  })
}
