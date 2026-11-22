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
})
