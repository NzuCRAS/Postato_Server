// 视图层:统一的 Markdown 渲染(查看 + 编辑预览共用)
// GFM(表格/任务列表/删除线)+ 代码块语法高亮 + github-markdown 样式(引用块竖线、代码块、表格)
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeHighlight from 'rehype-highlight'
import 'github-markdown-css/github-markdown-light.css'
import 'highlight.js/styles/github.css'

export default function MarkdownView({ content }: { content: string }) {
  return (
    <div className="markdown-body" style={{ background: 'transparent', fontSize: 14 }}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
        {content || ''}
      </ReactMarkdown>
    </div>
  )
}
