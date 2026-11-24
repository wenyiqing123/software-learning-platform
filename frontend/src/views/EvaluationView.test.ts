import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import EvaluationView from './EvaluationView.vue'
import router, { clearRoleCache } from '../router'

const api = vi.hoisted(() => ({
  evaluationSessions: vi.fn(),
  startEvaluation: vi.fn(),
  recordEvaluationMeasurement: vi.fn(),
  completeEvaluation: vi.fn(),
  currentUser: vi.fn(),
}))
vi.mock('../api/client', () => ({ learningApi: api }))

describe('EvaluationView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    clearRoleCache()
    localStorage.clear()
    api.evaluationSessions.mockResolvedValue([])
    api.startEvaluation.mockResolvedValue({ id: 12, conditionKey: 'METHODS', taskKey: 'TASK-1', taskOrder: 'A→B', consentVersion: 'evaluation-v2', startedAt: '2026-11-20T00:00:00Z' })
    api.recordEvaluationMeasurement.mockResolvedValue(undefined)
    api.completeEvaluation.mockResolvedValue({ id: 12, conditionKey: 'METHODS', taskKey: 'TASK-1', taskOrder: 'A→B', consentVersion: 'evaluation-v2', startedAt: '2026-11-20T00:00:00Z', completedAt: '2026-11-20T00:10:00Z' })
    api.currentUser.mockResolvedValue({ id: 1, role: 'STUDENT' })
  })

  it('renders 6-step visual stepper slots and grays out unreachable steps before session starts', async () => {
    const memoryRouter = createRouter({ history: createMemoryHistory(), routes: [{ path: '/evaluation', component: EvaluationView }] })
    await memoryRouter.push('/evaluation'); await memoryRouter.isReady()
    const wrapper = mount(EvaluationView, { global: { plugins: [memoryRouter] } })
    await flushPromises()

    const stepItems = wrapper.findAll('.evaluation-stepper .step-item')
    expect(stepItems).toHaveLength(6)
    expect(stepItems[0].text()).toContain('知情说明')
    expect(stepItems[0].classes()).toContain('status-current')
    expect(stepItems[1].text()).toContain('前测')
    expect(stepItems[1].classes()).toContain('status-pending')
    expect(stepItems[1].text()).toContain('待开始评估')
    expect(stepItems[2].text()).toContain('任务与计时')
    expect(stepItems[2].classes()).toContain('status-pending')
    expect(stepItems[5].text()).toContain('延后测验')
    expect(stepItems[5].classes()).toContain('status-pending')
    wrapper.unmount()
  })

  it('explains the Paas anchors and records delayed measurement separately', async () => {
    const memoryRouter = createRouter({ history: createMemoryHistory(), routes: [{ path: '/evaluation', component: EvaluationView }, { path: '/experiments', component: { template: '<div />' } }, { path: '/experiment-run', component: { template: '<div />' } }] })
    await memoryRouter.push('/evaluation'); await memoryRouter.isReady()
    const wrapper = mount(EvaluationView, { global: { plugins: [memoryRouter] } })
    await flushPromises()
    expect(wrapper.text()).toContain('不采集任何生理信号')
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper.get('input[placeholder*="A→B"]').setValue('B→A')
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()
    expect(api.startEvaluation).toHaveBeenCalledWith(expect.objectContaining({ taskOrder: 'B→A', consentAccepted: true }))
    expect(wrapper.text()).toContain('延后测验')
    expect(wrapper.text()).toContain('1 = 非常低')

    // 验证 Paas 选中后编号与文字辅助
    await wrapper.get('input[type="radio"][value="5"]').setValue(true)
    await flushPromises()
    expect(wrapper.find('.effort-selection-guide').text()).toContain('5 级')
    expect(wrapper.find('.effort-selection-guide').text()).toContain('中等')

    await wrapper.get('.delayed-card input[type="number"]').setValue(88)
    await wrapper.findAll('button').find(button => button.text().includes('保存延后测验'))!.trigger('click')
    expect(api.recordEvaluationMeasurement).toHaveBeenCalledWith(12, { phase: 'DELAYED', score: 88 })
    wrapper.unmount()
  })

  it('renders student-only /experiment-run task link with taskId and assignmentId query when active session has taskId', async () => {
    const memoryRouter = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/evaluation', component: EvaluationView },
        { path: '/experiment-run', component: { template: '<div>Experiment Run</div>' } }
      ]
    })
    await memoryRouter.push('/evaluation?taskId=101&assignmentId=55')
    await memoryRouter.isReady()
    const wrapper = mount(EvaluationView, { global: { plugins: [memoryRouter] } })
    await flushPromises()

    // 启动评估
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()

    // 此时 activeSession 存在，且 route.query.taskId 存在
    const runLink = wrapper.findAll('a').find(a => a.text().includes('进入问答任务'))
    expect(runLink).toBeDefined()
    expect(runLink!.attributes('href')).toBe('/experiment-run?taskId=101&assignmentId=55')
    wrapper.unmount()
  })

  it('enforces route role contracts and redirects ADMIN away from /experiment-run', async () => {
    const experimentRunRoute = router.getRoutes().find(r => r.path === '/experiment-run')
    const adminExperimentsRoute = router.getRoutes().find(r => r.path === '/experiments')

    expect(experimentRunRoute?.meta?.roles).toEqual(['STUDENT'])
    expect(adminExperimentsRoute?.meta?.roles).toEqual(['ADMIN'])

    // ADMIN 跨角色访问 /experiment-run 应被重定向到 /teaching-dashboard
    localStorage.setItem('slp-access-token', 'mock-admin-token')
    api.currentUser.mockResolvedValue({ id: 2, role: 'ADMIN' })

    await router.push('/experiment-run')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/teaching-dashboard')
  })
})
