import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ExperimentResultGrid from './ExperimentResultGrid.vue'

describe('ExperimentResultGrid', () => {
  it('keeps failed conditions visible and does not expose ranked prompt text', () => {
    const wrapper = mount(ExperimentResultGrid, { props: { results: [{ resultKey: 'RANKED', mode: 'RANKED', answer: '依据证据回答', prompt: ['server', 'only', 'constraint'].join('-'), latencyMs: 20 }, { resultKey: 'DIRECT', mode: 'DIRECT', error: '模型暂不可用', latencyMs: 0 }], ratings: {}, metrics: {}, modeNames: { RANKED: '排序评分', DIRECT: '直答' }, score: (value?: number) => value == null ? '—' : String(value), onRate: vi.fn() } })
    expect(wrapper.text()).toContain('模型暂不可用')
    expect(wrapper.text()).not.toContain('server-only-constraint')
  })
})
