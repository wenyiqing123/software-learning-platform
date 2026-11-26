import { buildCitationAlignment, type RawCitationTriple } from './citationAlignment'

export interface ExperimentMetricScores {
  rougeL?: number
  bleu4?: number
  bleuOrder?: number
  entityRecall?: number
}

export interface ExperimentConditionResult {
  mode?: string
  resultKey?: string
  answer?: string
  error?: string
  latencyMs?: number
  triples?: RawCitationTriple[]
  grounding?: {
    selectedIds?: string[]
    candidates?: unknown[]
    modelCallCount?: number
    selectionMs?: number
    answerMs?: number
  }
}

export interface BaselineDeltaRow {
  key: string
  label: string
  rougeL?: number
  bleu4?: number
  entityRecall?: number
  rougeDeltaPct?: number
  bleuDeltaPct?: number
  entityRecallDeltaPct?: number
  latencyMs?: number
}

export interface TimingBreakdown {
  hasBreakdown: boolean
  selectionMs: number
  answerMs: number
  totalMs: number
  selectionPct: number
  answerPct: number
}

function keyOf(r: ExperimentConditionResult): string {
  return r.resultKey || r.mode || ''
}

function toPct(val?: number): number | undefined {
  if (typeof val !== 'number' || !Number.isFinite(val)) return undefined
  return Number((val * 100).toFixed(1))
}

function diffPct(cur?: number, base?: number): number | undefined {
  if (typeof cur !== 'number' || typeof base !== 'number' || !Number.isFinite(cur) || !Number.isFinite(base)) {
    return undefined
  }
  return Number(((cur - base) * 100).toFixed(1))
}

export function computeBaselineDeltas(
  results: ReadonlyArray<ExperimentConditionResult>,
  metricsByMode: Record<string, ExperimentMetricScores> = {},
  modeNames: Record<string, string> = {},
): { baselineKey: string | null; baselineLabel: string; rows: BaselineDeltaRow[] } {
  const validResults = results.filter((r) => !r.error && Boolean(keyOf(r)))
  if (!validResults.length) {
    return { baselineKey: null, baselineLabel: '', rows: [] }
  }

  const direct =
    validResults.find((r) => keyOf(r).toUpperCase() === 'DIRECT') ||
    validResults.find((r) => keyOf(r).toUpperCase().endsWith('_DIRECT')) ||
    null

  const baselineKey = direct ? keyOf(direct) : null
  const baselineLabel = baselineKey ? modeNames[baselineKey] || baselineKey : ''
  const baselineMetrics = baselineKey ? metricsByMode[baselineKey] : undefined

  const rows: BaselineDeltaRow[] = validResults.map((r) => {
    const key = keyOf(r)
    const m = metricsByMode[key] || {}
    return {
      key,
      label: modeNames[key] || key,
      rougeL: toPct(m.rougeL),
      bleu4: toPct(m.bleu4),
      entityRecall: toPct(m.entityRecall),
      rougeDeltaPct: baselineKey && key !== baselineKey ? diffPct(m.rougeL, baselineMetrics?.rougeL) : undefined,
      bleuDeltaPct: baselineKey && key !== baselineKey ? diffPct(m.bleu4, baselineMetrics?.bleu4) : undefined,
      entityRecallDeltaPct:
        baselineKey && key !== baselineKey ? diffPct(m.entityRecall, baselineMetrics?.entityRecall) : undefined,
      latencyMs: typeof r.latencyMs === 'number' ? r.latencyMs : undefined,
    }
  })

  return { baselineKey, baselineLabel, rows }
}

export function computeTimingBreakdown(result: ExperimentConditionResult): TimingBreakdown {
  const sel = result.grounding?.selectionMs
  const ans = result.grounding?.answerMs
  if (typeof sel !== 'number' || typeof ans !== 'number' || (sel <= 0 && ans <= 0)) {
    return { hasBreakdown: false, selectionMs: 0, answerMs: 0, totalMs: 0, selectionPct: 0, answerPct: 0 }
  }
  const safeSel = Math.max(0, Math.round(sel))
  const safeAns = Math.max(0, Math.round(ans))
  const total = Math.max(1, safeSel + safeAns)
  const selectionPct = Math.round((safeSel / total) * 100)
  return {
    hasBreakdown: true,
    selectionMs: safeSel,
    answerMs: safeAns,
    totalMs: safeSel + safeAns,
    selectionPct,
    answerPct: Math.max(0, 100 - selectionPct),
  }
}

export function computeExperimentCitationCoverage(result: ExperimentConditionResult): string {
  if (result.error || !result.answer || !result.triples?.length) return ''
  const report = buildCitationAlignment(result.answer, result.triples)
  if (!report.totalTripleCount) return ''
  return `引用覆盖 ${report.coveredTripleCount}/${report.totalTripleCount} 条检索证据`
}

export function buildExperimentBarOption(rows: ReadonlyArray<BaselineDeltaRow>, isDark = false) {
  const textColor = isDark ? '#e6f0ed' : '#172b27'
  const mutedColor = isDark ? '#adc2bc' : '#52645f'
  const lineColor = isDark ? '#293c37' : '#dce5e3'
  const surfaceColor = isDark ? '#172320' : '#ffffff'

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: surfaceColor,
      borderColor: lineColor,
      textStyle: { color: textColor, fontSize: 12 },
    },
    legend: {
      top: 0,
      textStyle: { color: mutedColor, fontSize: 11.5 },
      data: ['ROUGE-L (%)', 'BLEU ≤ 4 (%)', '实体召回率 (%)'],
    },
    grid: { top: 34, right: 16, bottom: 28, left: 40 },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.label),
      axisLine: { lineStyle: { color: lineColor } },
      axisLabel: { color: mutedColor, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      max: 100,
      splitLine: { lineStyle: { color: lineColor, type: 'dashed' } },
      axisLabel: { color: mutedColor, fontSize: 11, formatter: '{value}%' },
    },
    series: [
      {
        name: 'ROUGE-L (%)',
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { color: isDark ? '#40b8a8' : '#0f665e', borderRadius: [4, 4, 0, 0] },
        data: rows.map((r) => r.rougeL ?? 0),
      },
      {
        name: 'BLEU ≤ 4 (%)',
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { color: isDark ? '#6bb0d1' : '#355f75', borderRadius: [4, 4, 0, 0] },
        data: rows.map((r) => r.bleu4 ?? 0),
      },
      {
        name: '实体召回率 (%)',
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { color: isDark ? '#dfa34b' : '#80591c', borderRadius: [4, 4, 0, 0] },
        data: rows.map((r) => r.entityRecall ?? 0),
      },
    ],
  }
}
