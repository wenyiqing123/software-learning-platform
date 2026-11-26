import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import LearningReportView from './LearningReportView.vue'

const learning = vi.hoisted(() => ({ learningReport: vi.fn(), exportLearningReport: vi.fn() }))
const http = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: learning, api: http, apiError: vi.fn(() => '加载失败') }))

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/learning-report', component: LearningReportView }] })
  await router.push('/learning-report')
  await router.isReady()
  return mount(LearningReportView, { global: { plugins: [router] } })
}

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockResolvedValue({ data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] })
  learning.learningReport.mockResolvedValue({ from: '2026-11-13', to: '2026-11-19', days: 7, courseName: '软件工程', reading: { totalSections: 8, completedSections: 3, completedInPeriod: 1 }, practice: { attempts: 4, correct: 3, accuracy: 75 }, reviews: { reviews: 2, correct: 1, accuracy: 50 }, plans: { totalTasks: 3, completedTasks: 2, plannedMinutes: 45, completedMinutes: 30 }, dailyActivity: [{ date: '2026-11-18', attempts: 2, correct: 1, readingCompleted: 1 }], chapters: [{ chapterId: 1, chapterName: '需求分析', totalSections: 4, completedSections: 2, completedInPeriod: 1 }], knowledge: [{ pointId: 1, pointName: '需求分析', mastery: 75, attempts: 4, correct: 3, dueReviewCount: 1 }] })
})

describe('learning report page', () => {
  it('shows traceable learning metrics and reloads the selected period', async () => {
    const wrapper = await mountPage()
    await flushPromises()
    expect(learning.learningReport).toHaveBeenCalledWith(7, undefined)
    expect(wrapper.text()).toContain('3 / 8')
    expect(wrapper.text()).toContain('75%')
    expect(wrapper.text()).toContain('需求分析')
    await wrapper.get('[data-testid="report-days"]').setValue('14')
    await flushPromises()
    expect(learning.learningReport).toHaveBeenLastCalledWith(14, undefined)
    wrapper.unmount()
  })
})
