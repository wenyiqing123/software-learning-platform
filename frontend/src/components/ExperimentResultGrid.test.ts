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

  it('shows citation alignment pill and selectionMs vs answerMs timing bar when returned', () => {
    const wrapper = mount(ExperimentResultGrid, {
      props: {
        results: [
          {
            resultKey: 'RANKED',
            mode: 'RANKED',
            answer: '数据流图加工应该采用动宾短语形式。',
            latencyMs: 1000,
            triples: [
              { subject: '加工', predicate: '采用', object: '动宾短语' },
              { subject: '数据存储', predicate: '命名', object: '名词短语' },
            ],
            grounding: { selectedIds: ['1'], candidates: [1, 2], modelCallCount: 2, selectionMs: 250, answerMs: 750 },
          },
        ],
        ratings: {},
        metrics: { RANKED: { rougeL: 0.72, bleu4: 0.5, entityRecall: 1 } },
        modeNames: { RANKED: '排序评分' },
        score: (v?: number) => (v != null ? `${v * 100}%` : '—'),
        onRate: vi.fn(),
      },
    })

    expect(wrapper.get('[data-testid="experiment-citation-coverage"]').text()).toBe('引用覆盖 1/2 条检索证据')
    const timing = wrapper.get('[data-testid="experiment-timing-breakdown"]')
    expect(timing.text()).toContain('检索筛选 250ms')
    expect(timing.text()).toContain('生成 750ms')
    wrapper.unmount()
  })
})
