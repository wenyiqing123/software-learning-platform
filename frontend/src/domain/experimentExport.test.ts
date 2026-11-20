import { describe, expect, it } from 'vitest'
import { experimentCsv, type ExperimentExportRun } from './experimentExport'

describe('experiment csv export', () => {
  it('flattens one row per answer and quotes question text safely', () => {
    const runs: ExperimentExportRun[] = [{
      id: 'run-1', createdAt: '2026-11-20T03:00:00Z', question: '问题,带逗号',
      ratings: { DIRECT: 4 },
      evaluation: { experimentType: 'METHODS', metricsByMode: { DIRECT: { rougeL: .5, bleu4: .25, entityRecall: 1 } } },
      results: [{ mode: 'DIRECT', resultKey: 'DIRECT', modelVariant: 'deepseek', answer: '答案', latencyMs: 120, error: '', provenance: { model: 'deepseek', temperature: 0.2, maxTokens: 256 } }],
    }]

    const csv = experimentCsv(runs)

    expect(csv).toContain('runId,createdAt,question,experimentType,resultKey,mode,modelVariant,status,latencyMs,evidenceOrder,promptVersion,promptSha256,systemPromptSha256,model,temperature,maxTokens,topK,rougeL,bleu4,bleuOrder,entityRecall,manualRating,answer,error')
    expect(csv).toContain('run-1,2026-11-20T03:00:00Z,"问题,带逗号",METHODS,DIRECT,DIRECT,deepseek,SUCCESS,120,')
    expect(csv).toContain('deepseek,0.2,256,,0.5,0.25,,1,4,答案,')
  })
})
