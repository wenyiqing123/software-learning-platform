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

  it('renders inline emphasis, code spans, ordered lists, blockquotes, dividers and tables safely', () => {
    const input = [
      '### 核心结论',
      '',
      '应采用 **动宾短语**（*动词 + 宾语*），例如 `校验权限`。',
      '',
      '1. 先确定加工输入与输出',
      '2. 使用动词加宾语概括处理动作',
      '',
      '> 参考示例：生成报表',
      '',
      '| 元素 | 规范 |',
      '| --- | --- |',
      '| 加工 | 动宾短语 |',
      '',
      '---',
    ].join('\n')
    const html = renderSimpleMarkdown(input)

    expect(html).toContain('<h4>核心结论</h4>')
    expect(html).toContain('<strong>动宾短语</strong>')
    expect(html).toContain('<em>动词 + 宾语</em>')
    expect(html).toContain('<code>校验权限</code>')
    expect(html).toContain('<ol><li>先确定加工输入与输出</li><li>使用动词加宾语概括处理动作</li></ol>')
    expect(html).toContain('<blockquote><p>参考示例：生成报表</p></blockquote>')
    expect(html).toContain('<table><thead><tr><th>元素</th><th>规范</th></tr></thead><tbody><tr><td>加工</td><td>动宾短语</td></tr></tbody></table>')
    expect(html).toContain('<hr>')
  })
})
