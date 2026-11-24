import { describe, expect, it } from 'vitest'
import { experimentCsv, experimentJson, type ExperimentExportRun } from './experimentExport'

describe('experiment csv export', () => {
  it('flattens one row per answer and quotes question text safely with UTF-8 BOM', () => {
    const runs: ExperimentExportRun[] = [{
      id: 'run-1',
      createdAt: '2026-11-20T03:00:00Z',
      question: '问题,带逗号',
      ratings: { DIRECT: 4 },
      evaluation: { experimentType: 'METHODS', metricsByMode: { DIRECT: { rougeL: 0.5, bleu4: 0.25, entityRecall: 1 } } },
      results: [{
        mode: 'DIRECT',
        resultKey: 'DIRECT',
        modelVariant: 'deepseek',
        answer: '答案',
        latencyMs: 120,
        error: '',
        provenance: { model: 'deepseek', temperature: 0.2, maxTokens: 256 }
      }],
      suiteVersion: '2026.1',
      caseId: 'REQ_ACCEPTANCE',
      sourceSection: '从真实问题到可验证的需求',
      batchId: 'batch-001',
      batchStatus: 'SUCCESS'
    }]

    const csv = experimentCsv(runs)

    // 必须有 UTF-8 BOM
    expect(csv.startsWith('\uFEFF')).toBe(true)
    // 检查末尾包含批量扩展列
    expect(csv).toContain('suiteVersion,caseId,sourceSection,batchId,batchStatus')
    expect(csv).toContain('run-1,2026-11-20T03:00:00Z,"问题,带逗号",METHODS,DIRECT,DIRECT,deepseek,SUCCESS,120,')
    expect(csv).toContain('deepseek,0.2,256,,0.5,0.25,,1,4,答案,,2026.1,REQ_ACCEPTANCE,从真实问题到可验证的需求,batch-001,SUCCESS')
  })

  it('handles partial failures and leaves failed answer/metrics empty', () => {
    const runs: ExperimentExportRun[] = [{
      id: 'run-fail',
      createdAt: '2026-11-20T04:00:00Z',
      question: '测试失败题目',
      ratings: {},
      results: [{
        mode: 'RANKED',
        resultKey: 'RANKED',
        latencyMs: 50,
        error: '上游服务超时',
        provenance: { systemPromptVersion: 'ranked-grounded-v3', systemPromptSha256: 'abc' }
      }]
    }]

    const csv = experimentCsv(runs)
    expect(csv).toContain('FAILED')
    expect(csv).toContain('上游服务超时')

    const json = experimentJson(runs)
    const resultRow = json.runs[0].results[0]
    expect(resultRow.status).toBe('FAILED')
    expect(resultRow.error).toBe('上游服务超时')
    expect(resultRow.answer).toBe('')
    expect(resultRow.rougeL).toBe('')
    expect(resultRow.manualRating).toBe('')
  })

  it('ensures JSON whitelist reconstruction without PII, rubric, or prompt body', () => {
    const runs: any[] = [{
      id: 'run-whitelist',
      createdAt: '2026-11-20T05:00:00Z',
      question: '白名单测试',
      userId: 12345, // 学生 PII
      studentName: '张三', // 学生 PII
      referenceAnswer: '内部教师标准答案', // rubric
      systemPromptBody: '固定约束后台正文', // prompt body
      ratings: { DIRECT: 5 },
      results: [{
        mode: 'DIRECT',
        resultKey: 'DIRECT',
        answer: '白名单回答',
        latencyMs: 80,
        prompt: '敏感 prompt 文本',
        provenance: {
          systemPromptVersion: 'ranked-grounded-v3',
          systemPromptSha256: 'sha256-hash',
          systemPromptBody: '不应泄露的后台 prompt'
        }
      }]
    }]

    const json = experimentJson(runs, {
      suiteVersion: '2026.1',
      batchId: 'batch-test-1',
      total: 1,
      successCount: 1,
      failedCount: 0
    })

    expect(json.schemaVersion).toBe('experiment-export-v2')
    expect(json.batch).toEqual({
      suiteVersion: '2026.1',
      batchId: 'batch-test-1',
      total: 1,
      successCount: 1,
      failedCount: 0
    })

    const runOut = json.runs[0]
    expect(runOut).not.toHaveProperty('userId')
    expect(runOut).not.toHaveProperty('studentName')
    expect(runOut).not.toHaveProperty('referenceAnswer')
    expect(runOut).not.toHaveProperty('systemPromptBody')

    const resOut = runOut.results[0] as Record<string, unknown>
    expect(resOut).not.toHaveProperty('systemPromptBody')
    expect(resOut).not.toHaveProperty('prompt')
    expect(resOut.promptVersion).toBe('ranked-grounded-v3')
    expect(resOut.systemPromptSha256).toBe('sha256-hash')
  })
})
