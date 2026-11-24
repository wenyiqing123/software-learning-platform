import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ExperimentResultGrid from './ExperimentResultGrid.vue'

describe('ExperimentResultGrid', () => {
  it('keeps failed conditions visible and does not expose ranked prompt text', () => {
    const wrapper = mount(ExperimentResultGrid, { props: { results: [{ resultKey: 'RANKED', mode: 'RANKED', answer: '依据证据回答', prompt: ['server', 'only', 'constraint'].join('-'), latencyMs: 20 }, { resultKey: 'DIRECT', mode: 'DIRECT', error: '模型暂不可用', latencyMs: 0 }], ratings: {}, metrics: {}, modeNames: { RANKED: '排序评分', DIRECT: '直答' }, score: (value?: number) => value == null ? '—' : String(value), onRate: vi.fn() } })
    expect(wrapper.text()).toContain('模型暂不可用')
    expect(wrapper.text()).not.toContain('server-only-constraint')
  })

  it('does not display metrics or manual rating select for failed condition cards', () => {
    const wrapper = mount(ExperimentResultGrid, {
      props: {
        results: [
          { resultKey: 'DIRECT', mode: 'DIRECT', error: '服务超时', latencyMs: 0 },
          { resultKey: 'TRIPLES', mode: 'TRIPLES', answer: '成功回答', latencyMs: 15 }
        ],
        ratings: {},
        metrics: { TRIPLES: { rougeL: 0.85, bleu4: 0.72 } },
        modeNames: { DIRECT: '直答', TRIPLES: '三元组增强' },
        score: (v?: number) => v != null ? `${v * 100}%` : '—',
        onRate: vi.fn()
      }
    })

    const cards = wrapper.findAll('.compare-result-card')
    expect(cards).toHaveLength(2)

    // 失败卡片：无人工评分 select，无 ROUGE-L dl
    const failedCard = cards[0]
    expect(failedCard.find('.rating-select').exists()).toBe(false)
    expect(failedCard.find('.experiment-scores').exists()).toBe(false)
    expect(failedCard.text()).toContain('条件未完成，不产生评估指标')

    // 成功卡片：有人工评分 select 与指标
    const successCard = cards[1]
    expect(successCard.find('.rating-select').exists()).toBe(true)
    expect(successCard.find('.experiment-scores').exists()).toBe(true)
    expect(successCard.text()).toContain('85%')
    wrapper.unmount()
  })
})
