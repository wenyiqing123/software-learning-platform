import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import DashboardView from './DashboardView.vue'
import { resetLearningMetrics } from '../composables/useLearningMetrics'

const learning = vi.hoisted(() => ({ overview: vi.fn(), knowledgePoints: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: learning }))
vi.mock('../domain/learningContext', () => ({
  readLastLearningContext: () => ({ courseId: 3, sectionId: 8, entity: '需求分析' }),
  courseHref: () => ({ path: '/courses', query: { courseId: '3', sectionId: '8' } }),
  practiceHref: () => ({ path: '/practice', query: { knowledgePointId: '11', entity: '需求分析' } }),
}))

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', component: DashboardView },
    { path: '/courses', component: { template: '<div />' } },
    { path: '/practice', component: { template: '<div />' } },
    { path: '/knowledge', component: { template: '<div />' } },
    { path: '/tutor', component: { template: '<div />' } },
    { path: '/review', component: { template: '<div />' } },
  ] })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(DashboardView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  localStorage.setItem('slp-access-token', 'test-token')
  resetLearningMetrics()
  learning.overview.mockResolvedValue({ completed: 4, mastery: 42, wrongCount: 2, accuracy: 75, activity: [] })
  learning.knowledgePoints.mockResolvedValue([{ id: 11, courseId: 3, name: '需求分析', graphEntity: '需求分析', mastery: 42, sortOrder: 1 }])
})

describe('DashboardView', () => {
  it('renders overview metrics and context-aware next actions', async () => {
    const { wrapper } = await mountPage()
    expect(wrapper.text()).toContain('今天，从一个知识点开始')
    expect(wrapper.text()).toContain('42%')
    expect(wrapper.text()).toContain('有 2 道错题待处理')
    expect(wrapper.find('.dashboard-hero').exists()).toBe(true)
    expect(wrapper.findAll('.metric-tile')).toHaveLength(4)
    expect(wrapper.find('.section-heading .button-outline').text()).toContain('问问 AI 助教')
    expect(wrapper.find('a.learning-next-card.next-primary').attributes('href')).toContain('/courses')
    expect(wrapper.find('a.learning-next-card').attributes('href')).toContain('/courses')
    expect(wrapper.findAll('a.learning-next-card')[2].attributes('href')).toContain('/practice')
    wrapper.unmount()
  })

  it('shows honest unavailable states when learning metrics fail', async () => {
    learning.overview.mockRejectedValue(new Error('offline'))
    learning.knowledgePoints.mockRejectedValue(new Error('offline'))
    const { wrapper } = await mountPage()
    expect(wrapper.text()).toContain('学习统计暂不可用')
    expect(wrapper.text()).toContain('答题记录暂不可用')
    wrapper.unmount()
  })

  it('renders dual-value retention alerts with quick review links and explainable rule copy', async () => {
    learning.overview.mockResolvedValue({
      completed: 8,
      mastery: 70,
      peakMastery: 85,
      effectiveMastery: 44,
      daysSinceLastPractice: 14,
      reviewUrgency: 'DUE',
      wrongCount: 1,
      accuracy: 80,
      activity: [],
    })
    learning.knowledgePoints.mockResolvedValue([
      {
        id: 11,
        courseId: 3,
        name: '需求分析',
        graphEntity: '需求分析',
        mastery: 70,
        peakMastery: 85,
        effectiveMastery: 44,
        daysSinceLastPractice: 14,
        reviewUrgency: 'DUE',
        sortOrder: 1,
      },
    ])

    const { wrapper } = await mountPage()
    expect(wrapper.text()).toContain('当前保持 44% · 历史峰值 85%')
    const alertBox = wrapper.get('[data-testid="dashboard-retention-alerts"]')
    expect(alertBox.text()).toContain('遗忘预警 · 已隔 14 天')
    expect(alertBox.text()).toContain('需求分析')
    expect(alertBox.get('a[href*="/practice"]').text()).toContain('定向复习')
    expect(alertBox.get('a[href="/review"]').text()).toContain('间隔复习')
    expect(wrapper.get('[data-testid="dashboard-retention-rule"]').text()).toContain('半衰期 14 天')
    wrapper.unmount()
  })
})
