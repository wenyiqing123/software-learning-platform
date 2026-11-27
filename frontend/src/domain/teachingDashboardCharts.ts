import { buildAriaConfig, buildSaveAsImageToolbox, getChartSurfacePalette } from './chartExport'

export interface TeachingActivityPoint {
  date: string
  activeLearners: number
  attempts: number
  correct: number
  completedReads: number
}

export interface TeachingWeakPoint {
  pointId: number
  pointName: string
  averageMastery: number
  attempts: number
  learners: number
}

const dateLabel = (value: string) => value.length >= 10 ? value.slice(5, 10) : value

export function buildActivityChartOption(points: TeachingActivityPoint[], isDark = false) {
  const sorted = [...points].sort((left, right) => left.date.localeCompare(right.date))
  const palette = getChartSurfacePalette(isDark)
  return {
    animationDuration: 360,
    aria: buildAriaConfig('每日答题次数、答对次数与阅读完成量趋势对比图', true),
    toolbox: buildSaveAsImageToolbox('slp-teaching-activity', isDark),
    grid: { top: 34, right: 18, bottom: 28, left: 42, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: palette.surface,
      borderColor: palette.line,
      textStyle: { color: palette.text, fontSize: 12 },
    },
    legend: { top: 0, left: 0, itemWidth: 12, itemHeight: 8, textStyle: { color: palette.muted, fontSize: 12 } },
    xAxis: { type: 'category', boundaryGap: false, data: sorted.map(point => dateLabel(point.date)), axisLine: { lineStyle: { color: palette.line } }, axisLabel: { color: palette.muted, fontSize: 12 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: palette.line, type: 'dashed' } }, axisLabel: { color: palette.muted, fontSize: 12 } },
    series: [
      { name: '答题次数', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: sorted.map(point => point.attempts), lineStyle: { width: 2, color: isDark ? '#40b8a8' : '#0f766e' }, itemStyle: { color: isDark ? '#40b8a8' : '#0f766e' }, areaStyle: { color: isDark ? 'rgba(64,184,168,.16)' : 'rgba(15,118,110,.10)' } },
      { name: '答对次数', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5, data: sorted.map(point => point.correct), lineStyle: { width: 2, color: isDark ? '#6bb0d1' : '#3d7fa2' }, itemStyle: { color: isDark ? '#6bb0d1' : '#3d7fa2' } },
      { name: '完成阅读', type: 'bar', barMaxWidth: 12, data: sorted.map(point => point.completedReads), itemStyle: { color: isDark ? '#2d7c70' : '#b7d9d2', borderRadius: [4, 4, 0, 0] } },
    ],
  }
}

export function buildWeakPointChartOption(points: TeachingWeakPoint[], isDark = false) {
  const sorted = [...points].sort((left, right) => left.averageMastery - right.averageMastery).slice(0, 8)
  const palette = getChartSurfacePalette(isDark)
  return {
    animationDuration: 360,
    aria: buildAriaConfig('薄弱知识点平均掌握度排行横向柱状图', true),
    toolbox: buildSaveAsImageToolbox('slp-teaching-weakpoints', isDark),
    grid: { top: 28, right: 28, bottom: 16, left: 8, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: palette.surface,
      borderColor: palette.line,
      textStyle: { color: palette.text, fontSize: 12 },
      valueFormatter: (value: number) => `${value}%`,
    },
    xAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: palette.line, type: 'dashed' } }, axisLabel: { color: palette.muted, fontSize: 12, formatter: '{value}%' } },
    yAxis: { type: 'category', data: sorted.map(point => point.pointName), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: palette.text, fontSize: 12 } },
    series: [{ name: '平均掌握度', type: 'bar', barMaxWidth: 16, data: sorted.map(point => point.averageMastery), label: { show: true, position: 'right', color: palette.text, fontSize: 12, formatter: '{c}%' }, itemStyle: { color: isDark ? '#df997c' : '#c98772', borderRadius: [0, 5, 5, 0] } }],
  }
}
