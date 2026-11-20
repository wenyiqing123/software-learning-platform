import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import EvaluationView from './EvaluationView.vue'

const api = vi.hoisted(() => ({ evaluationSessions: vi.fn(), startEvaluation: vi.fn(), recordEvaluationMeasurement: vi.fn(), completeEvaluation: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: api }))

describe('EvaluationView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    api.evaluationSessions.mockResolvedValue([])
    api.startEvaluation.mockResolvedValue({ id: 12, conditionKey: 'METHODS', taskKey: 'TASK-1', taskOrder: 'A→B', consentVersion: 'evaluation-v2', startedAt: '2026-11-20T00:00:00Z' })
    api.recordEvaluationMeasurement.mockResolvedValue(undefined)
    api.completeEvaluation.mockResolvedValue({ id: 12, conditionKey: 'METHODS', taskKey: 'TASK-1', taskOrder: 'A→B', consentVersion: 'evaluation-v2', startedAt: '2026-11-20T00:00:00Z', completedAt: '2026-11-20T00:10:00Z' })
  })

  it('explains the Paas anchors and records delayed measurement separately', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/evaluation', component: EvaluationView }, { path: '/experiments', component: { template: '<div />' } }] })
    await router.push('/evaluation'); await router.isReady()
    const wrapper = mount(EvaluationView, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('不采集任何生理信号')
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('延后测验')
    expect(wrapper.text()).toContain('1 = 非常低')
    await wrapper.get('.delayed-card input[type="number"]').setValue(88)
    await wrapper.findAll('button').find(button => button.text().includes('保存延后测验'))!.trigger('click')
    expect(api.recordEvaluationMeasurement).toHaveBeenCalledWith(12, { phase: 'DELAYED', score: 88 })
    wrapper.unmount()
  })
})
