// 视图层:Mermaid 图表块。懒加载 mermaid(仅当文档含 ```mermaid 代码块时才拉取这个较大的依赖),
// 在 useEffect 中异步渲染为 SVG;渲染失败时回退显示源码 + 错误信息,绝不白屏。
import { useEffect, useId, useState } from 'react'

export default function MermaidBlock({ chart }: { chart: string }) {
  // mermaid 的 render id 会用作 DOM/SVG id,不能含冒号(useId 默认返回形如 :r0:)
  const id = `mermaid-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
  const [svg, setSvg] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const mermaid = (await import('mermaid')).default
        // securityLevel: 'strict' 让 mermaid 清洗自身输出(防 XSS);startOnLoad 关闭,改为手动 render
        mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'default' })
        const { svg } = await mermaid.render(id, chart)
        if (!cancelled) {
          setSvg(svg)
          setError(null)
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e))
      }
    })()
    return () => {
      cancelled = true
    }
  }, [chart, id])

  if (error) {
    return (
      <pre style={{ border: '1px solid #ffccc7', background: '#fff2f0', borderRadius: 6, padding: 12, overflow: 'auto' }}>
        <code>{chart}</code>
        {`\n\n[Mermaid 渲染失败] ${error}`}
      </pre>
    )
  }
  if (!svg) {
    return <div style={{ color: '#999', padding: 8 }}>图表渲染中…</div>
  }
  // svg 由 mermaid 在 strict 模式下生成,可信
  return <div className="mermaid-block" style={{ textAlign: 'center' }} dangerouslySetInnerHTML={{ __html: svg }} />
}
