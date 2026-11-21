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
})
