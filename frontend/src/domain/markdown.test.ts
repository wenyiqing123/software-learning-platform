import { describe, expect, it } from 'vitest'
import { renderSimpleMarkdown } from './markdown'

describe('simple markdown renderer', () => {
  it('renders headings, lists, paragraphs and fenced code without exposing raw HTML', () => {
    const html = renderSimpleMarkdown('# 标题\n\n- 第一项\n- 第二项\n\n```java\nSystem.out.println("ok");\n```\n\n<script>alert(1)</script>')
    expect(html).toContain('<h3>标题</h3>')
    expect(html).toContain('<ul><li>第一项</li><li>第二项</li></ul>')
    expect(html).toContain('<pre><code class="language-java">System.out.println(&quot;ok&quot;);</code></pre>')
    expect(html).not.toContain('<script>')
  })
})
