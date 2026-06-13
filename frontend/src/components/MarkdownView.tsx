// 视图层:全平台统一的 Markdown 渲染组件(文档查看 + 编辑预览 + 需求描述预览共用,唯一入口)
// 能力:GFM(表格/任务列表/删除线/自动链接)+ 内嵌 HTML + 数学公式(KaTeX)+ 代码高亮 + Mermaid 图
//      + github-markdown 样式(列表缩进、表格边框、引用块竖线等)
import ReactMarkdown, { type Components } from 'react-markdown'
import type { PluggableList } from 'unified'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import rehypeRaw from 'rehype-raw'
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize'
import rehypeKatex from 'rehype-katex'
import rehypeHighlight from 'rehype-highlight'
import 'github-markdown-css/github-markdown-light.css'
import 'highlight.js/styles/github.css'
import 'katex/dist/katex.min.css'
import MermaidBlock from './MermaidBlock'
import { normalizeOrderedMarkers } from '../features/markdown'

// rehype 顺序关键(已实测验证):
//   rehypeRaw 先把内嵌 HTML 解析进语法树
// → rehypeSanitize 按 GitHub 规则清洗用户内容(杀 <script>/on*/javascript:,放行 details/kbd/sub/sup/任务列表/语言 class)
// → rehypeKatex / rehypeHighlight 在清洗之后再生成可信 HTML,不会被误清洗。
const remarkPlugins: PluggableList = [remarkGfm, remarkMath]
const rehypePlugins: PluggableList = [
  rehypeRaw,
  [rehypeSanitize, defaultSchema],
  rehypeKatex,
  [rehypeHighlight, { ignoreMissing: true }],
]

const components: Components = {
  // 外链在新标签页打开(带安全 rel);站内相对链接 / 锚点保持默认行为
  a({ href, title, children }) {
    const external = !!href && /^https?:\/\//i.test(href)
    return (
      <a href={href} title={title} {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>
        {children}
      </a>
    )
  },
  // mermaid 代码块 → 交给 MermaidBlock 异步渲染;其余代码块由 rehype-highlight 高亮后默认渲染
  code({ className, children }) {
    if (/\blanguage-mermaid\b/.test(className || '')) {
      const chart = Array.isArray(children) ? children.join('') : String(children ?? '')
      return <MermaidBlock chart={chart.replace(/\n$/, '')} />
    }
    return <code className={className}>{children}</code>
  },
  // 把 mermaid 的 <pre> 外壳脱掉,避免给图表套上代码块底色;普通代码块仍正常包 <pre>
  pre({ children, node }) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const firstChild = (node as any)?.children?.[0]
    const cls = firstChild?.properties?.className
    if (Array.isArray(cls) && cls.includes('language-mermaid')) return <>{children}</>
    return <pre>{children}</pre>
  },
}

export default function MarkdownView({ content, className }: { content: string; className?: string }) {
  return (
    <div className={`markdown-body${className ? ` ${className}` : ''}`} style={{ background: 'transparent', fontSize: 14 }}>
      <ReactMarkdown remarkPlugins={remarkPlugins} rehypePlugins={rehypePlugins} components={components}>
        {normalizeOrderedMarkers(content || '')}
      </ReactMarkdown>
    </div>
  )
}
