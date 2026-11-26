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
  abandonEvaluation: vi.fn(),
  currentUser: vi.fn(),
}))
vi.mock('../api/client', () => ({
  apiError: vi.fn((e: unknown, fallback = '请求失败') => (e instanceof Error ? e.message : fallback)),
  learningApi: api
}))

describe('EvaluationView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    clearRoleCache()
    localStorage.clear()
    api.evaluationSessions.mockResolvedValue([])
    api.startEvaluation.mockResolvedValue({ id: 12, conditionKey: 'METHODS', taskKey: 'TASK-1', taskOrder: 'A→B', consentVersion: 'evaluation-v2', startedAt: '2026-11-20T00:00:00Z', completedPhases: [] })
    api.recordEvaluationMeasurement.mockResolvedValue(undefined)
    api.completeEvaluation.mockResolvedValue({ id: 12, conditionKey: 'METHODS', taskKey: 'TASK-1', taskOrder: 'A→B', consentVersion: 'evaluation-v2', startedAt: '2026-11-20T00:00:00Z', completedAt: '2026-11-20T00:10:00Z', completedPhases: ['PRE', 'POST'] })
    api.abandonEvaluation.mockResolvedValue(undefined)
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

  it('enforces step blocking: blocks task link and post test until pre-test is saved, then unlocks them', async () => {
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

    // 前测未保存前，任务入口展示阻塞提示，链接未渲染
    expect(wrapper.text()).toContain('请先完成并保存前测，再解锁此任务。')
    expect(wrapper.findAll('a').find(a => a.text().includes('进入问答任务'))).toBeUndefined()

    // 提交前测
    await wrapper.get('.evaluation-card input[type="number"]').setValue(85)
    await wrapper.findAll('button').find(b => b.text().includes('保存前测'))!.trigger('click')
    await flushPromises()
    expect(api.recordEvaluationMeasurement).toHaveBeenCalledWith(12, { phase: 'PRE', score: 85 })

    // 前测完成后，任务链接解锁
    const runLink = wrapper.findAll('a').find(a => a.text().includes('进入问答任务'))
    expect(runLink).toBeDefined()
    expect(runLink!.attributes('href')).toBe('/experiment-run?taskId=101&assignmentId=55')
    wrapper.unmount()
  })

  it('resumes saved steps from server completedPhases and server-authoritative timer', async () => {
    api.evaluationSessions.mockResolvedValueOnce([
      {
        id: 77,
        conditionKey: 'METHODS',
        taskKey: 'TASK-RESUME',
        taskOrder: 'A→B',
        assignmentId: 55,
        consentVersion: 'evaluation-v2',
        startedAt: '2026-11-20T10:00:00Z',
        completedPhases: ['PRE']
      }
    ])

    const memoryRouter = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/evaluation', component: EvaluationView }]
    })
    await memoryRouter.push('/evaluation?taskId=101&assignmentId=55')
    await memoryRouter.isReady()
    const wrapper = mount(EvaluationView, { global: { plugins: [memoryRouter] } })
    await flushPromises()

    // 自动恢复会话，直接跳过知情同意并显示前测已保存
    expect(wrapper.text()).toContain('已从服务器恢复进行中的评估会话。')
    expect(wrapper.text()).toContain('前测已保存')
    const stepItems = wrapper.findAll('.evaluation-stepper .step-item')
    expect(stepItems[0].classes()).toContain('status-done') // 知情说明 done
    expect(stepItems[1].classes()).toContain('status-done') // 前测 done
    expect(stepItems[2].classes()).toContain('status-current') // 任务与计时 current
    wrapper.unmount()
  })

  it('enforces complete gating: requires both PRE, POST and effort before final submission', async () => {
    const memoryRouter = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/evaluation', component: EvaluationView }]
    })
    await memoryRouter.push('/evaluation')
    await memoryRouter.isReady()
    const wrapper = mount(EvaluationView, { global: { plugins: [memoryRouter] } })
    await flushPromises()

    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()

    // 尚未完成后测时，完成按钮应禁用或受阻
    const completeBtn = wrapper.findAll('button').find(b => b.text().includes('完成并保存'))!
    expect(completeBtn.attributes('disabled')).toBeDefined()

    // 依次完成 PRE 和 POST
    await wrapper.get('.evaluation-card input[type="number"]').setValue(80)
    await wrapper.findAll('button').find(b => b.text().includes('保存前测'))!.trigger('click')
    await flushPromises()

    const postInput = wrapper.findAll('.evaluation-card input[type="number"]')[1]
    await postInput.setValue(90)
    await wrapper.findAll('button').find(b => b.text().includes('保存后测'))!.trigger('click')
    await flushPromises()

    // 选定努力评分
    await wrapper.get('input[type="radio"][value="6"]').setValue(true)
    await flushPromises()

    // 此时允许提交完成
    expect(completeBtn.attributes('disabled')).toBeUndefined()
    await completeBtn.trigger('click')
    await flushPromises()

    expect(api.completeEvaluation).toHaveBeenCalledWith(12, expect.objectContaining({
      psychologicalEffort: 6
    }))
    expect(wrapper.text()).toContain('评估已完成，数据仅用于匿名聚合分析。')
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

  it('confirms and abandons an active uncompleted session on the server', async () => {
    const memoryRouter = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/evaluation', component: EvaluationView }]
    })
    await memoryRouter.push('/evaluation')
    await memoryRouter.isReady()
    const wrapper = mount(EvaluationView, { global: { plugins: [memoryRouter] } })
    await flushPromises()

    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()

    await wrapper.findAll('button').find(b => b.text().includes('放弃本次'))!.trigger('click')
    await flushPromises()
    ;(document.body.querySelector('[data-testid="confirm-dialog-confirm"]') as HTMLButtonElement | null)?.click()
    await flushPromises()

    expect(api.abandonEvaluation).toHaveBeenCalledWith(12)
    expect(wrapper.text()).toContain('开始评估')
    wrapper.unmount()
  })
})
