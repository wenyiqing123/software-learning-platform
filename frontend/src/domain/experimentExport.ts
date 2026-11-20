export interface ExperimentExportMetric { rougeL?: number; bleu4?: number; entityRecall?: number }
export interface ExperimentExportResult { mode: string; resultKey?: string; modelVariant?: string; answer?: string; latencyMs: number; error?: string }
export interface ExperimentExportRun {
  id: string
  createdAt: string
  question: string
  ratings: Record<string, number>
  results: ExperimentExportResult[]
  evaluation?: { experimentType?: string; metricsByMode?: Record<string, ExperimentExportMetric> }
}

function csv(value: unknown): string {
  const text = value == null ? '' : String(value)
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

export function experimentCsv(runs: ExperimentExportRun[]): string {
  const header = ['runId', 'createdAt', 'question', 'experimentType', 'resultKey', 'mode', 'modelVariant', 'status', 'latencyMs', 'rougeL', 'bleu4', 'entityRecall', 'manualRating', 'answer', 'error']
  const rows = runs.flatMap(run => run.results.map(result => {
    const key = result.resultKey || result.mode
    const metric = run.evaluation?.metricsByMode?.[key] || {}
    return [run.id, run.createdAt, run.question, run.evaluation?.experimentType || '', key, result.mode, result.modelVariant || '', result.error ? 'FAILED' : 'SUCCESS', result.latencyMs, metric.rougeL ?? '', metric.bleu4 ?? '', metric.entityRecall ?? '', run.ratings[key] ?? '', result.answer || '', result.error || ''].map(csv).join(',')
  }))
  return '\uFEFF' + [header.join(','), ...rows].join('\r\n') + '\r\n'
}
