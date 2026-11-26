import type { ForecastDayBucket } from './graphAlgorithms'

export interface HeatmapActivityPoint {
  date: string
  count: number
  correct?: number
  readingCompleted?: number
}

export interface MasteryRadarPoint {
  name: string
  value: number
}

function chartPalette(isDark: boolean) {
  return isDark
    ? {
        text: '#e6f0ed',
        muted: '#adc2bc',
        line: '#293c37',
        surface: '#172320',
        accent: '#40b8a8',
        accentSoft: 'rgba(64, 184, 168, 0.24)',
        heatLow: '#1b2b27',
        heatMid: '#24685f',
        heatHigh: '#5dd0c0',
      }
    : {
        text: '#172b27',
        muted: '#52645f',
        line: '#dce5e3',
        surface: '#ffffff',
        accent: '#0f665e',
        accentSoft: 'rgba(15, 102, 94, 0.2)',
        heatLow: '#eef2f3',
        heatMid: '#5bb4a6',
        heatHigh: '#0b514b',
      }
}

export function buildHeatmapActivityOption(days: ReadonlyArray<HeatmapActivityPoint>, isDark = false) {
  const palette = chartPalette(isDark)
  const sorted = [...days].sort((a, b) => a.date.localeCompare(b.date))
  const cols = Math.max(7, sorted.length)
  const maxVal = Math.max(1, ...sorted.map((d) => d.count))
  const weekLabels = ['日', '一', '二', '三', '四', '五', '六']

  const data = sorted.map((d, idx) => {
    const dt = new Date(`${d.date}T00:00:00`)
    const row = Number.isNaN(dt.getTime()) ? idx % 7 : dt.getDay()
    const col = Math.floor(idx / 7)
    return {
      value: [col, row, d.count],
      date: d.date,
      correct: d.correct,
      readingCompleted: d.readingCompleted,
    }
  })

  const totalWeeks = Math.max(1, Math.ceil(cols / 7))
  const xLabels = Array.from({ length: totalWeeks }, (_, w) => {
    const sample = sorted[w * 7]
    return sample ? sample.date.slice(5) : `W${w + 1}`
  })

  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: palette.surface,
      borderColor: palette.line,
      textStyle: { color: palette.text, fontSize: 12 },
      formatter: (params: { data?: { date?: string; value?: [number, number, number]; correct?: number; readingCompleted?: number } }) => {
        const item = params.data
        if (!item) return ''
        const total = item.value?.[2] ?? 0
        const extra: string[] = [`${item.date}`, `总学习活动：${total}`]
        if (typeof item.correct === 'number') extra.push(`答对：${item.correct}`)
        if (typeof item.readingCompleted === 'number') extra.push(`读完小节：${item.readingCompleted}`)
        return extra.join('<br/>')
      },
    },
    grid: { top: 12, right: 16, bottom: 28, left: 32 },
    xAxis: {
      type: 'category',
      data: xLabels,
      splitArea: { show: false },
      axisLine: { lineStyle: { color: palette.line } },
      axisLabel: { color: palette.muted, fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: weekLabels,
      splitArea: { show: false },
      axisLine: { lineStyle: { color: palette.line } },
      axisLabel: { color: palette.muted, fontSize: 11 },
    },
    visualMap: {
      show: false,
      min: 0,
      max: maxVal,
      inRange: {
        color: [palette.heatLow, palette.heatMid, palette.heatHigh],
      },
    },
    series: [
      {
        name: '学习活跃热力',
        type: 'heatmap',
        data,
        itemStyle: {
          borderColor: palette.surface,
          borderWidth: 2,
          borderRadius: 4,
        },
        label: {
          show: sorted.length <= 14,
          color: palette.text,
          fontSize: 10,
          formatter: (p: { data?: { value?: [number, number, number] } }) => {
            const v = p.data?.value?.[2] ?? 0
            return v > 0 ? String(v) : ''
          },
        },
      },
    ],
  }
}

export function buildMasteryRadarOption(points: ReadonlyArray<MasteryRadarPoint>, isDark = false) {
  const palette = chartPalette(isDark)
  const limited = points.slice(0, 8)
  const indicators = limited.map((p) => ({
    name: p.name.length > 8 ? `${p.name.slice(0, 8)}…` : p.name,
    max: 100,
  }))
  const values = limited.map((p) => Math.max(0, Math.min(100, Math.round(p.value))))

  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: palette.surface,
      borderColor: palette.line,
      textStyle: { color: palette.text, fontSize: 12 },
    },
    radar: {
      indicator: indicators,
      radius: '64%',
      splitNumber: 4,
      axisName: { color: palette.muted, fontSize: 11 },
      splitLine: { lineStyle: { color: palette.line } },
      splitArea: { areaStyle: { color: ['transparent'] } },
      axisLine: { lineStyle: { color: palette.line } },
    },
    series: [
      {
        name: '知识点保持度分布',
        type: 'radar',
        data: [
          {
            value: values,
            name: '当前掌握度 (%)',
            symbolSize: 5,
            lineStyle: { color: palette.accent, width: 2 },
            itemStyle: { color: palette.accent },
            areaStyle: { color: palette.accentSoft },
          },
        ],
      },
    ],
  }
}

export function buildReviewForecastChartOption(buckets: ReadonlyArray<ForecastDayBucket>, isDark = false) {
  const palette = chartPalette(isDark)
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: palette.surface,
      borderColor: palette.line,
      textStyle: { color: palette.text, fontSize: 12 },
    },
    grid: { top: 16, right: 14, bottom: 26, left: 32 },
    xAxis: {
      type: 'category',
      data: buckets.map((b) => b.label),
      axisLine: { lineStyle: { color: palette.line } },
      axisLabel: { color: palette.muted, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: palette.line, type: 'dashed' } },
      axisLabel: { color: palette.muted, fontSize: 11 },
    },
    series: [
      {
        name: '到期复习题量',
        type: 'bar',
        barMaxWidth: 26,
        data: buckets.map((b, i) => ({
          value: b.dueCount,
          itemStyle: {
            color: i === 0 ? palette.accent : palette.heatMid,
            borderRadius: [4, 4, 0, 0],
          },
        })),
      },
    ],
  }
}
