import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import EvaluationAdminView from './EvaluationAdminView.vue'

const learning = vi.hoisted(() => ({ evaluationSummary: vi.fn(), exportEvaluationSummary: vi.fn() }))
vi.mock('../api/client', () => ({
  learningApi: learning,
  apiError: vi.fn((_cause: unknown, fallback: string) => fallback),
}))

beforeEach(() => {
  vi.resetAllMocks()
  learning.evaluationSummary.mockResolvedValue([{
    conditionKey: 'METHODS', sampleCount: 12,
    meanPreScore: 3.5, medianPreScore: 4, stddevPreScore: 1.1, iqrPreScore: 2,
    meanPostScore: 7.5, medianPostScore: 8, stddevPostScore: 0.9, iqrPostScore: 1,
    meanDelayedScore: 7, medianDelayedScore: 7, iqrDelayedScore: 1,
    meanEffort: 4.2, medianEffort: 4, stddevEffort: 1.2, iqrEffort: 2,
    meanElapsedSeconds: 96, medianElapsedSeconds: 90, stddevElapsedSeconds: 12, iqrElapsedSeconds: 18,
    feedbackCount: 10,
  }])
  learning.exportEvaluationSummary.mockResolvedValue(new Blob(['participant,condition\nanon-001,METHODS']))
  vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:anonymous-export'), revokeObjectURL: vi.fn() })
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
})

describe('EvaluationAdminView', () => {
  it('renders grouped descriptive statistics without real participant identity', async () => {
    const wrapper = mount(EvaluationAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('METHODS')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('3.5 / 4')
    expect(wrapper.text()).toContain('SD 1.1 · IQR 2')
    expect(wrapper.text()).not.toContain('userId')
    wrapper.unmount()
  })

  it('requests the anonymized export from the admin API', async () => {
    const wrapper = mount(EvaluationAdminView)
    await flushPromises()
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()
    expect(learning.exportEvaluationSummary).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
})
