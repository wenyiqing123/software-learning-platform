import { describe, expect, it } from 'vitest'
import { buildHeatmapActivityOption, buildMasteryRadarOption, buildReviewForecastChartOption } from './learningAnalyticsCharts'

describe('learningAnalyticsCharts', () => {
  it('builds calendar-matrix heatmap option and responds to dark mode palette', () => {
    const light = buildHeatmapActivityOption([
      { date: '2026-11-20', count: 4, correct: 3, readingCompleted: 1 },
      { date: '2026-11-21', count: 2, correct: 2, readingCompleted: 0 },
    ], false)
    expect(light.series[0].type).toBe('heatmap')
    expect(light.series[0].data).toHaveLength(2)
    expect(light.visualMap.max).toBe(4)

    const dark = buildHeatmapActivityOption([{ date: '2026-11-20', count: 1 }], true)
    expect(dark.tooltip.textStyle.color).toBe('#e6f0ed')
  })

  it('builds radar mastery option with capped 8 dimensions and 7-day forecast bar chart', () => {
    const radar = buildMasteryRadarOption(
      Array.from({ length: 10 }, (_, idx) => ({ name: `知识点名称非常长第${idx + 1}节`, value: idx * 10 })),
      false,
    )
    expect(radar.radar.indicator).toHaveLength(8)
    expect(radar.series[0].data[0].value).toHaveLength(8)

    const forecast = buildReviewForecastChartOption(
      [
        { key: '2026-11-26', label: '今日/已到期', dueCount: 3 },
        { key: '2026-11-27', label: '明天', dueCount: 1 },
      ],
      false,
    )
    expect(forecast.xAxis.data).toEqual(['今日/已到期', '明天'])
    expect(forecast.series[0].data.map((d) => d.value)).toEqual([3, 1])
    expect(radar.aria.enabled).toBe(true)
    expect(radar.toolbox.feature.saveAsImage.name).toBe('slp-mastery-radar')
    expect(forecast.aria.decal.show).toBe(true)
    expect(forecast.toolbox.feature.saveAsImage.name).toBe('slp-review-forecast')
  })
})
