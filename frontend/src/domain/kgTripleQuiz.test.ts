import { describe, expect, it } from 'vitest'
import { buildDeterministicKgTripleQuiz, extractValidGraphTriples } from './kgTripleQuiz'

describe('kgTripleQuiz deterministic generator', () => {
  it('honestly refuses generation when graph is offline or lacks >= 4 valid triples', () => {
    const offline = buildDeterministicKgTripleQuiz({
      available: false,
      nodes: [],
      edges: [],
      evidence: [],
    })
    expect(offline.ready).toBe(false)
    expect(offline.questions).toHaveLength(0)
    expect(offline.reason).toContain('不可用')

    const sparse = buildDeterministicKgTripleQuiz({
      available: true,
      nodes: [{ id: '需求分析', label: '需求分析' }, { id: '系统设计', label: '系统设计' }],
      edges: [{ source: '需求分析', target: '系统设计', label: '引出' }],
      evidence: [{ subject: '需求分析', predicate: '引出', object: '系统设计', score: 0.9 }],
    })
    expect(sparse.ready).toBe(false)
    expect(sparse.tripleCount).toBe(1)
    expect(sparse.reason).toContain('至少需要 4 条')
  })

  it('generates mutually exclusive cloze items strictly from real subgraph triples', () => {
    const graph = {
      available: true,
      nodes: [
        { id: '需求分析', label: '需求分析' },
        { id: '数据流图DFD', label: '数据流图DFD' },
        { id: '用例图', label: '用例图' },
        { id: '系统设计', label: '系统设计' },
        { id: '软件需求规格说明书', label: '软件需求规格说明书' },
      ],
      edges: [],
      evidence: [
        { subject: '需求分析', predicate: '使用', object: '数据流图DFD', score: 0.95 },
        { subject: '需求分析', predicate: '包含', object: '用例图', score: 0.9 },
        { subject: '需求分析', predicate: '引出', object: '系统设计', score: 0.88 },
        { subject: '需求分析', predicate: '输出', object: '软件需求规格说明书', score: 0.92 },
        { subject: '系统设计', predicate: '依据', object: '软件需求规格说明书', score: 0.85 },
      ],
    }

    expect(extractValidGraphTriples(graph)).toHaveLength(5)
    const quiz = buildDeterministicKgTripleQuiz(graph, 4)
    expect(quiz.ready).toBe(true)
    expect(quiz.tripleCount).toBe(5)
    expect(quiz.questions.length).toBeGreaterThanOrEqual(2)

    for (const item of quiz.questions) {
      const optionValues = item.options.map((o) => o.value)
      expect(optionValues).toContain(item.correctValue)
      expect(new Set(optionValues).size).toBe(item.options.length)
      expect(item.options.length).toBeGreaterThanOrEqual(3)

      // 确认所有干扰项都不会在真实三元组库中形成碰撞真值
      const distractors = optionValues.filter((val) => val !== item.correctValue)
      for (const dist of distractors) {
        if (item.kind === 'object') {
          const collides = graph.evidence.some(
            (t) => t.subject === item.subject && t.predicate === item.predicate && t.object === dist,
          )
          expect(collides).toBe(false)
        } else {
          const collides = graph.evidence.some(
            (t) => t.subject === item.subject && t.predicate === dist && t.object === item.object,
          )
          expect(collides).toBe(false)
        }
      }
    }
  })

  it('never picks another true object as a distractor when multiple objects share subject+predicate', () => {
    const graph = {
      available: true,
      nodes: [],
      edges: [],
      evidence: [
        { subject: '需求分析', predicate: '包含', object: '用例图', score: 0.9 },
        { subject: '需求分析', predicate: '包含', object: '数据流图DFD', score: 0.88 },
        { subject: '需求分析', predicate: '输出', object: 'SRS说明书', score: 0.86 },
        { subject: '系统设计', predicate: '采用', object: 'MVC架构', score: 0.84 },
        { subject: '系统设计', predicate: '验证', object: '集成测试', score: 0.82 },
      ],
    }

    const quiz = buildDeterministicKgTripleQuiz(graph, 5)
    const useCaseQ = quiz.questions.find(
      (q) => q.subject === '需求分析' && q.predicate === '包含' && q.object === '用例图' && q.kind === 'object',
    )
    if (useCaseQ) {
      expect(useCaseQ.options.map((o) => o.value)).not.toContain('数据流图DFD')
    }
  })
})
