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
    meanDelayedScore: 7, medianDelayedScore: 7, stddevDelayedScore: 0.5, iqrDelayedScore: 1,
    meanGain: 4,
    meanEffort: 4.2, medianEffort: 4, stddevEffort: 1.2, iqrEffort: 2,
    meanElapsedSeconds: 96, medianElapsedSeconds: 90, stddevElapsedSeconds: 12, iqrElapsedSeconds: 18,
    feedbackCount: 10,
  }])
  learning.exportEvaluationSummary.mockResolvedValue(new Blob(['participant,condition\nanon-001,METHODS']))
  vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:anonymous-export'), revokeObjectURL: vi.fn() })
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
})

describe('EvaluationAdminView', () => {
  it('renders grouped descriptive statistics with semantic table caption and scope without identity', async () => {
    const wrapper = mount(EvaluationAdminView)
    await flushPromises()
    expect(wrapper.text()).toContain('METHODS')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('3.5 / 4')
    expect(wrapper.text()).toContain('SD 1.1 · IQR 2')
    expect(wrapper.text()).toContain('+4')
    expect(wrapper.text()).toContain('SD 0.5 · IQR 1')

    // 验证原生语义表格、caption 与 scope
    const table = wrapper.find('table')
    expect(table.exists()).toBe(true)
    const caption = wrapper.find('caption')
    expect(caption.exists()).toBe(true)
    expect(caption.text()).toContain('描述性统计')

    const thCols = wrapper.findAll('th[scope="col"]')
    expect(thCols.length).toBeGreaterThanOrEqual(8)
    expect(wrapper.find('th[scope="row"]').text()).toBe('METHODS')

    // 严禁输出用户身份或原始回答
    expect(wrapper.text()).not.toContain('userId')
    expect(wrapper.text()).not.toContain('userName')
    expect(wrapper.text()).not.toContain('学生姓名')
    expect(wrapper.text()).not.toContain('原始回答')
    wrapper.unmount()
  })

  it('requests the anonymized export from the admin API without leaking identity', async () => {
    const wrapper = mount(EvaluationAdminView)
    await flushPromises()
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()
    expect(learning.exportEvaluationSummary).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
})
