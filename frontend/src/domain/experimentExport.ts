export interface ExperimentExportMetric { rougeL?: number; bleu4?: number; bleuOrder?: number; entityRecall?: number }
export interface ExperimentExportResult {
  mode: string
  resultKey?: string
  modelVariant?: string
  answer?: string
  latencyMs: number
  error?: string
  triples?: Array<{ subject: string; predicate: string; object: string; score?: number }>
  provenance?: Record<string, unknown>
}
export interface ExperimentExportRun {
  id: string
  createdAt: string
  question: string
  ratings: Record<string, number>
  results: ExperimentExportResult[]
  evaluation?: { experimentType?: string; metricsByMode?: Record<string, ExperimentExportMetric> }
  suiteVersion?: string
  caseId?: string
  sourceSection?: string
  batchId?: string
  batchStatus?: string
}

export interface BatchExportMeta {
  suiteVersion?: string
  batchId?: string
  total?: number
  successCount?: number
  failedCount?: number
}

export interface ExperimentExportPayload {
  schemaVersion: string
  exportedAt: string
  batch?: {
    suiteVersion: string
    batchId: string
    total: number
    successCount: number
    failedCount: number
  }
  runs: Array<{
    id: string
    createdAt: string
    question: string
    suiteVersion?: string
    caseId?: string
    sourceSection?: string
    batchId?: string
    batchStatus?: string
    results: Array<Record<string, unknown>>
  }>
}

function csv(value: unknown): string {
  const text = value == null ? '' : String(value)
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

function rowFor(run: ExperimentExportRun, result: ExperimentExportResult) {
  const key = result.resultKey || result.mode
  const metric = run.evaluation?.metricsByMode?.[key] || {}
  const provenance = result.provenance || {}
  const isFailed = Boolean(result.error)

  return {
    runId: run.id,
    createdAt: run.createdAt,
    question: run.question,
    experimentType: run.evaluation?.experimentType || '',
    resultKey: key,
    mode: result.mode,
    modelVariant: result.modelVariant || '',
    status: isFailed ? 'FAILED' : 'SUCCESS',
    latencyMs: result.latencyMs,
    evidenceOrder: (result.triples || []).map(item => `${item.subject}--${item.predicate}-->${item.object}`).join(' | '),
    promptVersion: String(provenance.systemPromptVersion || provenance.promptTemplate || ''),
    promptSha256: String(provenance.promptSha256 || ''),
    systemPromptSha256: String(provenance.systemPromptSha256 || ''),
    model: String(provenance.model || ''),
    temperature: provenance.temperature ?? '',
    maxTokens: provenance.maxTokens ?? '',
    topK: provenance.topK ?? '',
    rougeL: isFailed ? '' : (metric.rougeL ?? ''),
    bleu4: isFailed ? '' : (metric.bleu4 ?? ''),
    bleuOrder: isFailed ? '' : (metric.bleuOrder ?? ''),
    entityRecall: isFailed ? '' : (metric.entityRecall ?? ''),
    manualRating: isFailed ? '' : (run.ratings[key] ?? ''),
    answer: isFailed ? '' : (result.answer || ''),
    error: result.error || '',
    suiteVersion: run.suiteVersion || '',
    caseId: run.caseId || '',
    sourceSection: run.sourceSection || '',
    batchId: run.batchId || '',
    batchStatus: run.batchStatus || ''
  }
}

export function experimentCsv(runs: ExperimentExportRun[]): string {
  const header = [
    'runId', 'createdAt', 'question', 'experimentType', 'resultKey', 'mode', 'modelVariant',
    'status', 'latencyMs', 'evidenceOrder', 'promptVersion', 'promptSha256', 'systemPromptSha256',
    'model', 'temperature', 'maxTokens', 'topK', 'rougeL', 'bleu4', 'bleuOrder', 'entityRecall',
    'manualRating', 'answer', 'error', 'suiteVersion', 'caseId', 'sourceSection', 'batchId', 'batchStatus'
  ]
  const rows = runs.flatMap(run => run.results.map(result => {
    const row = rowFor(run, result)
    return header.map(key => csv(row[key as keyof typeof row])).join(',')
  }))
  return '\uFEFF' + [header.join(','), ...rows].join('\r\n') + '\r\n'
}

export function experimentJson(runs: ExperimentExportRun[], batchMeta?: BatchExportMeta): ExperimentExportPayload {
  const batch = batchMeta ? {
    suiteVersion: batchMeta.suiteVersion || '',
    batchId: batchMeta.batchId || '',
    total: batchMeta.total ?? runs.length,
    successCount: batchMeta.successCount ?? runs.filter(r => r.results.every(res => !res.error)).length,
    failedCount: batchMeta.failedCount ?? runs.filter(r => r.results.some(res => Boolean(res.error))).length
  } : undefined

  return {
    schemaVersion: 'experiment-export-v2',
    exportedAt: new Date().toISOString(),
    ...(batch ? { batch } : {}),
    runs: runs.map(run => ({
      id: run.id,
      createdAt: run.createdAt,
      question: run.question,
      suiteVersion: run.suiteVersion,
      caseId: run.caseId,
      sourceSection: run.sourceSection,
      batchId: run.batchId,
      batchStatus: run.batchStatus,
      results: run.results.map(result => rowFor(run, result))
    }))
  }
}
