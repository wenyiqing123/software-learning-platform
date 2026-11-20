import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdaptivePracticeView from './AdaptivePracticeView.vue'

const learning = vi.hoisted(() => ({ adaptivePractice: vi.fn() }))
const http = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: learning, api: http }))

const candidates = [
  {
    questionId: 31,
    stem: '\u6570\u636e\u6d41\u56fe\u4e2d\uff0c\u52a0\u5de5\u7684\u547d\u540d\u5e94\u91c7\u7528\u4ec0\u4e48\u5f62\u5f0f\uff1f',
    knowledgePointId: 4,
    knowledgePointName: '\u6570\u636e\u6d41\u56fe',
    difficulty: 2,
    mastery: 24,
    wrongCount: 2,
    due: true,
    score: 61,
    reason: '\u8be5\u9898\u5173\u8054\u9519\u9898\u5df2\u5230\u590d\u4e60\u65f6\u95f4\uff1b\u5148\u4fee\u77e5\u8bc6\u70b9\u5c1a\u672a\u5b8c\u5168\u638c\u63e1',
  },
]

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/adaptive-practice', component: AdaptivePracticeView },
      { path: '/practice', component: { template: '<div />' } },
    ],
  })
  await router.push('/adaptive-practice')
  await router.isReady()
  return { wrapper: mount(AdaptivePracticeView, { global: { plugins: [router] } }), router }
}

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockResolvedValue({ data: [{ id: 3, name: '\u8f6f\u4ef6\u5de5\u7a0b\u8bfe\u7a0b' }] })
  learning.adaptivePractice.mockResolvedValue(candidates)
})

describe('adaptive practice page', () => {
  it('shows rule-based reasons and links each candidate to the real question', async () => {
    const { wrapper, router } = await mountPage()
    await flushPromises()

    expect(learning.adaptivePractice).toHaveBeenCalledWith({ limit: 10 })
    expect(wrapper.text()).toContain('\u6570\u636e\u6d41\u56fe\u4e2d\uff0c\u52a0\u5de5\u7684\u547d\u540d\u5e94\u91c7\u7528\u4ec0\u4e48\u5f62\u5f0f\uff1f')
    expect(wrapper.text()).toContain('\u8be5\u9898\u5173\u8054\u9519\u9898\u5df2\u5230\u590d\u4e60\u65f6\u95f4')
    expect(wrapper.text()).toContain('\u638c\u63e1\u5ea6 24%')

    await wrapper.get('[data-testid="adaptive-start-31"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/practice?questionId=31')
    wrapper.unmount()
  })

  it('reloads with the selected limit and course filter', async () => {
    const { wrapper } = await mountPage()
    await flushPromises()
    await wrapper.get('[data-testid="adaptive-limit"]').setValue('20')
    await wrapper.get('[data-testid="adaptive-course"]').setValue('3')
    await wrapper.get('button[aria-label="\u5237\u65b0\u4e13\u9879\u7ec3\u4e60"]').trigger('click')
    await flushPromises()
    expect(learning.adaptivePractice).toHaveBeenLastCalledWith({ limit: 20, courseId: 3 })
    wrapper.unmount()
  })
})
