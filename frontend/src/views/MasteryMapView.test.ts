import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MasteryMapView from './MasteryMapView.vue'

const learning = vi.hoisted(() => ({ masteryMap: vi.fn() }))
const http = vi.hoisted(() => ({ get: vi.fn() }))
const graphApi = vi.hoisted(() => ({ instances: [] as Array<{ render: ReturnType<typeof vi.fn>; destroy: ReturnType<typeof vi.fn>; on: ReturnType<typeof vi.fn> }> }))
vi.mock('@antv/g6', () => ({ Graph: class {
  render = vi.fn().mockResolvedValue(undefined)
  destroy = vi.fn()
  on = vi.fn()
  constructor() { graphApi.instances.push(this) }
} }))
vi.mock('../api/client', () => ({ learningApi: learning, api: http }))

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/mastery-map', component: MasteryMapView },
    { path: '/courses', component: { template: '<div />' } },
    { path: '/practice', component: { template: '<div />' } },
    { path: '/review', component: { template: '<div />' } },
  ] })
  await router.push('/mastery-map')
  await router.isReady()
  return { wrapper: mount(MasteryMapView, { global: { plugins: [router] } }), router }
}

beforeEach(() => {
  vi.resetAllMocks()
  graphApi.instances = []
  http.get.mockResolvedValue({ data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] })
  learning.masteryMap.mockResolvedValue({ courseId: 1, courseName: '软件工程', nodes: [
    { id: 1, courseId: 1, chapterId: 1, name: '需求分析', graphEntity: '需求分析', mastery: 80, attemptCount: 5, correctCount: 4, dueReviewCount: 0, status: 'MASTERED', prerequisiteIds: [] },
    { id: 2, courseId: 1, chapterId: 1, name: '数据流图', graphEntity: '数据流图DFD', mastery: 0, attemptCount: 0, correctCount: 0, dueReviewCount: 2, status: 'BLOCKED', prerequisiteIds: [1] },
  ], edges: [{ source: 1, target: 2, relation: '先修' }] })
})

describe('personal mastery map', () => {
  it('shows status, due review evidence and study links', async () => {
    const { wrapper, router } = await mountPage()
    await flushPromises()
    expect(learning.masteryMap).toHaveBeenCalledWith(undefined)
    expect(graphApi.instances).toHaveLength(1)
    expect(graphApi.instances[0].render).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('需求分析')
    expect(wrapper.text()).toContain('掌握程度')
    expect(wrapper.text()).toContain('已掌握')
    expect(wrapper.text()).toContain('被先修知识点阻塞')
    expect(wrapper.text()).toContain('待复习 2')
    await wrapper.get('button[aria-label="全屏图谱"]').trigger('click')
    expect(wrapper.find('.visualization-viewport.is-fullscreen').exists()).toBe(true)
    await wrapper.get('button[aria-label="退出全屏"]').trigger('click')
    expect(wrapper.find('.visualization-viewport.is-fullscreen').exists()).toBe(false)
    await wrapper.get('.mastery-node').trigger('click')
    expect(wrapper.get('.evidence-drawer').text()).toContain('当前节点：需求分析')
    expect(wrapper.find('.evidence-triple-card').exists()).toBe(false)
    await wrapper.get('[data-testid="mastery-practice-2"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/practice?knowledgePointId=2')
    wrapper.unmount()
  })

  it('displays dual-value retention, forgetting alerts, and spaced-review shortcut', async () => {
    learning.masteryMap.mockResolvedValueOnce({
      courseId: 1,
      courseName: '软件工程',
      nodes: [
        {
          id: 1,
          courseId: 1,
          chapterId: 1,
          name: '需求分析',
          graphEntity: '需求分析',
          mastery: 80,
          peakMastery: 85,
          effectiveMastery: 40,
          daysSinceLastPractice: 14,
          reviewUrgency: 'DUE',
          attemptCount: 5,
          correctCount: 4,
          dueReviewCount: 1,
          status: 'MASTERED',
          prerequisiteIds: [],
        },
      ],
      edges: [],
    })
    const { wrapper, router } = await mountPage()
    await flushPromises()

    expect(wrapper.get('[data-testid="mastery-retention-rule"]').text()).toContain('半衰期 14 天')
    expect(wrapper.text()).toContain('当前保持度 40%')
    expect(wrapper.text()).toContain('历史峰值 85%')
    expect(wrapper.get('[data-testid="mastery-retention-alert-1"]').text()).toContain('遗忘预警 · 已隔 14 天')

    await wrapper.get('[data-testid="mastery-review-1"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/review')
    wrapper.unmount()
  })
})
