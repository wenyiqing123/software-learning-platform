import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MarkdownContent from './MarkdownContent.vue'

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
})
