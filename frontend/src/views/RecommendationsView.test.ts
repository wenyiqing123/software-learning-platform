import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import RecommendationsView from './RecommendationsView.vue'

const learning = vi.hoisted(() => ({ recommendations: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: learning }))

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/recommendations', component: RecommendationsView },
    { path: '/practice', component: { template: '<div />' } },
    { path: '/knowledge', component: { template: '<div />' } },
    { path: '/review', component: { template: '<div />' } },
  ] })
  await router.push('/recommendations')
  await router.isReady()
  const wrapper = mount(RecommendationsView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  learning.recommendations.mockResolvedValue([
    { id: 7, knowledgePointId: 17, title: '先复习数据流图', reason: '当前掌握度较低，且存在待处理错题；建议先完成本节练习。', kind: '薄弱点巩固', score: 0.91, knowledgePoint: '数据流图' },
  ])
})

describe('RecommendationsView', () => {
  it('shows explainable recommendation reasons and preserves context links', async () => {
    const { wrapper, router } = await mountPage()
    expect(wrapper.text()).toContain('基于掌握度、错题与先修关系的可解释规则推荐')
    expect(wrapper.text()).toContain('当前掌握度较低，且存在待处理错题；建议先完成本节练习。')
    expect(wrapper.text()).not.toContain('机器学习知识追踪')
    expect(wrapper.text()).not.toContain('AI 预测掌握度')
    await wrapper.find('button.button-dark').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/practice')
    expect(router.currentRoute.value.query).toEqual({ knowledgePointId: '17' })
    wrapper.unmount()
  })

  it('renders a useful empty state without inventing recommendations', async () => {
    learning.recommendations.mockResolvedValue([])
    const { wrapper, router } = await mountPage()
    expect(wrapper.text()).toContain('当前暂无学习建议')
    await wrapper.findAll('button').at(-1)!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/practice')
    wrapper.unmount()
  })

  it('displays forgetting risk alert badge and review shortcut for decaying topics', async () => {
    learning.recommendations.mockResolvedValueOnce([
      {
        id: 11,
        knowledgePointId: 11,
        title: '需求分析',
        reason: '距上次练习已 14 天，当前记忆保持估值回落至 40%（历史峰值 80%），建议优先复习。',
        kind: '遗忘预警',
        score: 79.2,
        knowledgePoint: '需求分析',
        peakMastery: 80,
        effectiveMastery: 40,
        daysSinceLastPractice: 14,
        reviewUrgency: 'DUE',
      },
    ])
    const { wrapper } = await mountPage()
    expect(wrapper.text()).toContain('遗忘预警 · 已隔 14 天')
    expect(wrapper.text()).toContain('保持 40% / 峰值 80%')
    expect(wrapper.get('a[href="/review"]').text()).toContain('间隔复习')
    wrapper.unmount()
  })
})
