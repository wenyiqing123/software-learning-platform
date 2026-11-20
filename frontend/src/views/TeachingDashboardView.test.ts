import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TeachingDashboardView from './TeachingDashboardView.vue'

const http = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api/client', () => ({ api: http, apiError: vi.fn(() => '看板加载失败') }))

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockImplementation(async (path: string) => {
    if (path === '/admin/courses') return { data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] }
    if (path === '/admin/teaching-dashboard') return { data: { from: '2026-11-13', to: '2026-11-19', days: 7, courseId: 1, courseName: '软件工程', overview: { activeLearners: 4, totalSections: 8, completedReads: 3, attempts: 20, correct: 15, openWrong: 2, averageMastery: 62.5 }, dailyActivity: [{ date: '2026-11-18', activeLearners: 3, attempts: 4, correct: 3, completedReads: 2 }], weakPoints: [{ pointId: 2, pointName: '数据流图', averageMastery: 35, attempts: 5, learners: 3 }] } }
    throw new Error('unexpected path: ' + path)
  })
})

describe('TeachingDashboardView', () => {
  it('shows anonymous aggregate metrics and reloads selected range', async () => {
    const wrapper = mount(TeachingDashboardView)
    await flushPromises()
    expect(wrapper.text()).toContain('匿名聚合')
    expect(wrapper.text()).toContain('4 人')
    expect(wrapper.text()).toContain('数据流图')
    await wrapper.get('[data-testid="dashboard-days"]').setValue('14')
    await wrapper.get('button[aria-label="刷新教学看板"]').trigger('click')
    await flushPromises()
    expect(http.get).toHaveBeenLastCalledWith('/admin/teaching-dashboard', { params: { days: 14, courseId: 1 } })
    wrapper.unmount()
  })
})
