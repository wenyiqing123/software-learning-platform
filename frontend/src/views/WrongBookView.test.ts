import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import WrongBookView from './WrongBookView.vue'

const learning = vi.hoisted(() => ({ wrongBook: vi.fn(), markWrongMastered: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: learning }))
vi.mock('../components/LearningContextActions.vue', () => ({ default: { template: '<span />' } }))

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/wrong-book', component: WrongBookView },
    { path: '/practice', component: { template: '<div />' } },
    { path: '/tutor', component: { template: '<div />' } },
  ] })
  await router.push('/wrong-book')
  await router.isReady()
  const wrapper = mount(WrongBookView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  learning.wrongBook.mockResolvedValue([
    { id: 21, questionId: 101, question: '数据流图加工如何命名？', knowledgePoint: '数据流图', wrongCount: 2, status: 'UNMASTERED' },
    { id: 22, questionId: 102, question: '需求分析的交付文档是什么？', knowledgePoint: '需求分析', wrongCount: 1, status: 'UNMASTERED' },
  ])
  learning.markWrongMastered.mockResolvedValue(undefined)
})

describe('WrongBookView', () => {
  it('renders personal wrong questions, filters them and opens targeted practice', async () => {
    const { wrapper, router } = await mountPage()
    expect(wrapper.findAll('article.wrong-row')).toHaveLength(2)
    await wrapper.get('input[placeholder="搜索题目或知识点"]').setValue('需求分析')
    expect(wrapper.findAll('article.wrong-row')).toHaveLength(1)
    await wrapper.find('article.wrong-row button.button-outline').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/practice')
    expect(router.currentRoute.value.query).toEqual({ questionId: '102', wrongItemId: '22' })
    wrapper.unmount()
  })

  it('removes a question only after the mastered request succeeds', async () => {
    const { wrapper } = await mountPage()
    await wrapper.find('button[title="标记为已掌握"]').trigger('click')
    await flushPromises()
    expect(learning.markWrongMastered).toHaveBeenCalledWith(21)
    expect(wrapper.findAll('article.wrong-row')).toHaveLength(1)
    wrapper.unmount()
  })

  it('exports markdown handout and prints wrong-book list', async () => {
    const createObjectURL = vi.fn(() => 'blob:mock-wrong')
    const revokeObjectURL = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: createObjectURL })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: revokeObjectURL })
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {})

    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="wrong-export-md"]').trigger('click')
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-wrong')

    await wrapper.get('[data-testid="wrong-print"]').trigger('click')
    expect(printSpy).toHaveBeenCalledTimes(1)

    vi.restoreAllMocks()
    wrapper.unmount()
  })

  it('aggregates deterministic knowledge-point distribution pills and filters rows on click', async () => {
    learning.wrongBook.mockResolvedValue([
      { id: 21, questionId: 101, question: '数据流图加工如何命名？', knowledgePoint: '数据流图', wrongCount: 3, status: 'UNMASTERED' },
      { id: 22, questionId: 102, question: '需求分析的交付文档是什么？', knowledgePoint: '需求分析', wrongCount: 1, status: 'UNMASTERED' },
      { id: 23, questionId: 103, question: '外部实体符号代表什么？', knowledgePoint: '数据流图', wrongCount: 2, status: 'UNMASTERED' },
    ])

    const { wrapper } = await mountPage()
    const dist = wrapper.get('[data-testid="wrong-kp-distribution"]')
    expect(dist.text()).toContain('3 题分布于 2 个知识点')

    const dfdPill = wrapper.get('[data-testid="wrong-kp-pill-数据流图"]')
    expect(dfdPill.text()).toContain('2 题 · 67%')
    expect(dfdPill.text()).toContain('错 5 次')

    await dfdPill.trigger('click')
    expect(dfdPill.classes()).toContain('active')
    expect(wrapper.findAll('article.wrong-row')).toHaveLength(2)

    await dfdPill.trigger('click')
    expect(dfdPill.classes()).not.toContain('active')
    expect(wrapper.findAll('article.wrong-row')).toHaveLength(3)
    wrapper.unmount()
  })

  it('navigates to /tutor with question stem prefill without triggering LLM request or leaking answers', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="wrong-ask-tutor-21"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/tutor')
    expect(router.currentRoute.value.query.entity).toBe('数据流图')
    const prompt = String(router.currentRoute.value.query.prompt || '')
    expect(prompt).toContain('数据流图加工如何命名？')
    expect(prompt).toContain('梳理关键概念边界与解题思路')
    expect(prompt).not.toContain('标准答案')
    expect(fetchSpy).not.toHaveBeenCalled()

    vi.restoreAllMocks()
    wrapper.unmount()
  })
})
