import { describe, expect, it } from 'vitest'
import { buildActivityChartOption, buildWeakPointChartOption } from './teachingDashboardCharts'

describe('teaching dashboard chart adapters', () => {
  it('maps daily activity into aligned labels and three comparable series', () => {
    const option = buildActivityChartOption([
      { date: '2026-11-18', activeLearners: 3, attempts: 4, correct: 3, completedReads: 2 },
      { date: '2026-11-19', activeLearners: 4, attempts: 8, correct: 6, completedReads: 1 },
    ])

    expect(option.xAxis.data).toEqual(['11-18', '11-19'])
    expect(option.series.map(series => series.name)).toEqual(['答题次数', '答对次数', '完成阅读'])
    expect(option.series[0].data).toEqual([4, 8])
    expect(option.series[1].data).toEqual([3, 6])
    expect(option.series[2].data).toEqual([2, 1])
  })

  it('sorts weak points from lower mastery to higher mastery for a readable bar chart', () => {
    const option = buildWeakPointChartOption([
      { pointId: 1, pointName: '需求分析', averageMastery: 62, attempts: 6, learners: 4 },
      { pointId: 2, pointName: '数据流图', averageMastery: 35, attempts: 5, learners: 3 },
    ])

    expect(option.yAxis.data).toEqual(['数据流图', '需求分析'])
    expect(option.series[0].data).toEqual([35, 62])
  })
})
