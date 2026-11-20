export interface ExperimentExportMetric { rougeL?: number; bleu4?: number; bleuOrder?: number; entityRecall?: number }
export interface ExperimentExportResult { mode: string; resultKey?: string; modelVariant?: string; answer?: string; latencyMs: number; error?: string; triples?: Array<{ subject: string; predicate: string; object: string; score?: number }>; provenance?: Record<string, unknown> }
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

function rowFor(run: ExperimentExportRun, result: ExperimentExportResult) {
  const key = result.resultKey || result.mode
  const metric = run.evaluation?.metricsByMode?.[key] || {}
  const provenance = result.provenance || {}
  return {
    runId: run.id, createdAt: run.createdAt, question: run.question, experimentType: run.evaluation?.experimentType || '', resultKey: key, mode: result.mode,
    modelVariant: result.modelVariant || '', status: result.error ? 'FAILED' : 'SUCCESS', latencyMs: result.latencyMs,
    evidenceOrder: (result.triples || []).map(item => `${item.subject}--${item.predicate}-->${item.object}`).join(' | '),
    promptVersion: provenance.systemPromptVersion || provenance.promptTemplate || '', promptSha256: provenance.promptSha256 || '', systemPromptSha256: provenance.systemPromptSha256 || '',
    model: provenance.model || '', temperature: provenance.temperature ?? '', maxTokens: provenance.maxTokens ?? '', topK: provenance.topK ?? '',
    rougeL: metric.rougeL ?? '', bleu4: metric.bleu4 ?? '', bleuOrder: metric.bleuOrder ?? '', entityRecall: metric.entityRecall ?? '', manualRating: run.ratings[key] ?? '',
    answer: result.error ? '' : result.answer || '', error: result.error || '',
  }
}

export function experimentCsv(runs: ExperimentExportRun[]): string {
  const header = ['runId', 'createdAt', 'question', 'experimentType', 'resultKey', 'mode', 'modelVariant', 'status', 'latencyMs', 'evidenceOrder', 'promptVersion', 'promptSha256', 'systemPromptSha256', 'model', 'temperature', 'maxTokens', 'topK', 'rougeL', 'bleu4', 'bleuOrder', 'entityRecall', 'manualRating', 'answer', 'error']
  const rows = runs.flatMap(run => run.results.map(result => { const row = rowFor(run, result); return header.map(key => csv(row[key as keyof typeof row])).join(',') }))
  return '\uFEFF' + [header.join(','), ...rows].join('\r\n') + '\r\n'
}

export function experimentJson(runs: ExperimentExportRun[]) {
  return { schemaVersion: 'experiment-export-v2', exportedAt: new Date().toISOString(), runs: runs.map(run => ({ ...run, results: run.results.map(result => { const row = rowFor(run, result); return { ...row } }) })) }
}
