import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MarkdownContent from './MarkdownContent.vue'

const mermaidApi = vi.hoisted(() => ({
  initialize: vi.fn(),
  parse: vi.fn(),
  render: vi.fn(),
}))

vi.mock('mermaid', () => ({
  default: mermaidApi,
}))

beforeEach(() => {
  mermaidApi.initialize.mockReset()
  mermaidApi.parse.mockResolvedValue(true)
  mermaidApi.render.mockResolvedValue({
    svg: '<svg xmlns="http://www.w3.org/2000/svg"><rect width="40" height="20" /></svg>',
  })
})

afterEach(() => vi.restoreAllMocks())

describe('MarkdownContent', () => {
  it('copies only the selected fenced code, leaving HTML as inert text', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.spyOn(window, 'navigator', 'get').mockReturnValue({ clipboard: { writeText } } as unknown as Navigator)
    const wrapper = mount(MarkdownContent, { props: { content: '```html\n<img src=x>\n```\n```text\nother code\n```' } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('code').text()).toBe('<img src=x>')
    await wrapper.get('button[aria-label="复制代码"]').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledExactlyOnceWith('<img src=x>')
    expect(wrapper.get('[role="status"]').text()).toBe('已复制')
  })

  it('announces clipboard failure without changing the code', async () => {
    vi.spyOn(window, 'navigator', 'get').mockReturnValue({ clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) } } as unknown as Navigator)
    const wrapper = mount(MarkdownContent, { props: { content: '```ts\nconst value = 2;\n```' } })
    await wrapper.get('button[aria-label="复制代码"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="status"]').text()).toBe('复制失败')
    expect(wrapper.get('code').text()).toBe('const value = 2;')
  })

  it('renders formatted bold text, inline code, blockquotes, ordered lists, and tables via safe Vue nodes', () => {
    const wrapper = mount(MarkdownContent, {
      props: {
        content: [
          '### 核心结论',
          '',
          '采用 **动宾短语** 描述 `加工` 逻辑。',
          '',
          '> 提示：禁止使用纯名词短语。',
          '',
          '| 对象 | 规则 |',
          '| --- | --- |',
          '| 加工 | 动宾短语 |',
        ].join('\n'),
      },
    })

    expect(wrapper.get('h4').text()).toBe('核心结论')
    expect(wrapper.get('strong').text()).toBe('动宾短语')
    expect(wrapper.get('p code').text()).toBe('加工')
    expect(wrapper.get('blockquote.markdown-quote').text()).toContain('禁止使用纯名词短语')
    expect(wrapper.findAll('table.markdown-table th').map(th => th.text())).toEqual(['对象', '规则'])
    expect(wrapper.findAll('table.markdown-table td').map(td => td.text())).toEqual(['加工', '动宾短语'])
  })

  it('keeps unclosed streaming mermaid blocks as code placeholder and renders MermaidDiagram when closed', async () => {
    const wrapper = mount(MarkdownContent, {
      props: {
        content: '正在绘制流程图：\n\n```mermaid\nflowchart LR\n  A[需求] --> B[设计]',
      },
    })

    expect(wrapper.find('[data-testid="mermaid-streaming-placeholder"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mermaid-diagram-card"]').exists()).toBe(false)
    expect(mermaidApi.render).not.toHaveBeenCalled()

    await wrapper.setProps({
      content: '正在绘制流程图：\n\n```mermaid\nflowchart LR\n  A[需求] --> B[设计]\n```',
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="mermaid-streaming-placeholder"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="mermaid-diagram-card"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="mermaid-svg-stage"]').html()).toContain('<svg')
    wrapper.unmount()
  })

  it('renders deterministic inline citation markers with hover tooltip preview and skips ungrounded text', async () => {
    const wrapper = mount(MarkdownContent, {
      props: {
        content: '需求分析用于编写需求规格说明书。也可以用 `需求分析` 代码块讨论无关内容。',
        citations: [
          { index: 1, subject: '需求分析', predicate: '输出', object: '需求规格说明书' },
          { index: 2, subject: '数据库设计', predicate: '生成', object: 'ER 图' },
        ],
      },
    })

    const btn = wrapper.get('[data-testid="inline-citation-1"]')
    expect(btn.text()).toBe('[1]')
    expect(wrapper.find('[data-testid="inline-citation-2"]').exists()).toBe(false)
    expect(wrapper.findAll('.inline-citation-btn')).toHaveLength(1)
    expect(wrapper.get('.citation-hover-preview').text()).toContain('需求分析 — 输出 — 需求规格说明书')

    await btn.trigger('click')
    expect(wrapper.emitted('select-citation')).toEqual([[1]])

    const withoutCitations = mount(MarkdownContent, {
      props: {
        content: '需求分析用于编写需求规格说明书。',
        citations: [],
      },
    })
    expect(withoutCitations.findAll('.inline-citation-btn')).toHaveLength(0)
  })
})
