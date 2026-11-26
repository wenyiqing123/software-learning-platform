import { describe, expect, it } from 'vitest'
import {
  buildExperimentBarOption,
  computeBaselineDeltas,
  computeExperimentCitationCoverage,
  computeTimingBreakdown,
} from './experimentComparisonCharts'

describe('experimentComparisonCharts', () => {
  it('computes +delta vs DIRECT baseline cleanly while ignoring failed conditions', () => {
    const summary = computeBaselineDeltas(
      [
        { resultKey: 'DIRECT', mode: 'DIRECT', answer: '加工是名词或动宾', latencyMs: 500 },
        { resultKey: 'RANKED', mode: 'RANKED', answer: '加工必须用动宾短语命名', latencyMs: 820 },
        { resultKey: 'TRIPLES', mode: 'TRIPLES', error: 'timeout', latencyMs: 0 },
      ],
      {
        DIRECT: { rougeL: 0.4, bleu4: 0.25, entityRecall: 0.5 },
        RANKED: { rougeL: 0.72, bleu4: 0.5, entityRecall: 1.0 },
      },
      { DIRECT: '大模型直答', RANKED: '排序评分' },
    )

    expect(summary.baselineKey).toBe('DIRECT')
    expect(summary.rows).toHaveLength(2)
    expect(summary.rows[1].rougeDeltaPct).toBe(32)
    expect(summary.rows[1].bleuDeltaPct).toBe(25)
    expect(summary.rows[1].entityRecallDeltaPct).toBe(50)

    const option = buildExperimentBarOption(summary.rows, false)
    expect(option.xAxis.data).toEqual(['大模型直答', '排序评分'])
    expect(option.series).toHaveLength(3)
  })

  it('computes selectionMs vs answerMs stacked breakdown and citation alignment badge', () => {
    const timing = computeTimingBreakdown({
      mode: 'RANKED',
      grounding: { selectionMs: 240, answerMs: 760 },
    })
    expect(timing.hasBreakdown).toBe(true)
    expect(timing.selectionPct).toBe(24)
    expect(timing.answerPct).toBe(76)

    const coverage = computeExperimentCitationCoverage({
      mode: 'RANKED',
      answer: '加工应该采用动宾短语形式。',
      triples: [
        { subject: '加工', predicate: '命名规范', object: '动宾短语' },
        { subject: '外部实体', predicate: '代表', object: '人员' },
      ],
    })
    expect(coverage).toBe('引用覆盖 1/2 条检索证据')
  })
})
