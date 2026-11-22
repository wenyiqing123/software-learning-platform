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

export function buildActivityChartOption(points: TeachingActivityPoint[]) {
  const sorted = [...points].sort((left, right) => left.date.localeCompare(right.date))
  return {
    animationDuration: 360,
    grid: { top: 28, right: 18, bottom: 28, left: 42, containLabel: true },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 8, textStyle: { color: '#475b56', fontSize: 12 } },
    xAxis: { type: 'category', boundaryGap: false, data: sorted.map(point => dateLabel(point.date)), axisLine: { lineStyle: { color: '#dce6e4' } }, axisLabel: { color: '#52645f', fontSize: 12 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf2f1' } }, axisLabel: { color: '#52645f', fontSize: 12 } },
    series: [
      { name: '答题次数', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: sorted.map(point => point.attempts), lineStyle: { width: 2, color: '#0f766e' }, itemStyle: { color: '#0f766e' }, areaStyle: { color: 'rgba(15,118,110,.10)' } },
      { name: '答对次数', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5, data: sorted.map(point => point.correct), lineStyle: { width: 2, color: '#3d7fa2' }, itemStyle: { color: '#3d7fa2' } },
      { name: '完成阅读', type: 'bar', barMaxWidth: 12, data: sorted.map(point => point.completedReads), itemStyle: { color: '#b7d9d2', borderRadius: [4, 4, 0, 0] } },
    ],
  }
}

export function buildWeakPointChartOption(points: TeachingWeakPoint[]) {
  const sorted = [...points].sort((left, right) => left.averageMastery - right.averageMastery).slice(0, 8)
  return {
    animationDuration: 360,
    grid: { top: 8, right: 24, bottom: 16, left: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (value: number) => `${value}%` },
    xAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: '#edf2f1' } }, axisLabel: { color: '#52645f', fontSize: 12, formatter: '{value}%' } },
    yAxis: { type: 'category', data: sorted.map(point => point.pointName), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#475b56', fontSize: 12 } },
    series: [{ name: '平均掌握度', type: 'bar', barMaxWidth: 16, data: sorted.map(point => point.averageMastery), label: { show: true, position: 'right', color: '#475b56', fontSize: 12, formatter: '{c}%' }, itemStyle: { color: '#c98772', borderRadius: [0, 5, 5, 0] } }],
  }
}
