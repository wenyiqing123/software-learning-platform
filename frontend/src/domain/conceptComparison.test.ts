import { describe, expect, it } from 'vitest'
import { buildConceptCompareTutorPrompt, compareConcepts, extractConceptTriples } from './conceptComparison'
import type { GraphResponse } from '../api/types'

function makeResponse(overrides: Partial<GraphResponse> = {}): GraphResponse {
  return {
    available: true,
    nodes: [],
    edges: [],
    evidence: [],
    ...overrides,
  }
}

describe('conceptComparison domain', () => {
  it('deduplicates triples merged from edges and evidence', () => {
    const res = makeResponse({
      edges: [
        { source: '结构化分析SA', target: '数据流图DFD', label: '核心工具', score: 0.85 },
        { source: '结构化分析SA', target: '需求分析', label: '属于' },
      ],
      evidence: [
        { subject: '结构化分析SA', predicate: '核心工具', object: '数据流图DFD', score: 0.92 },
        { subject: '结构化分析SA', predicate: '输出', object: '软件需求规格说明书' },
      ],
    })

    const triples = extractConceptTriples(res)
    expect(triples).toHaveLength(3)
    expect(triples.find(t => t.object === '数据流图DFD')?.score).toBe(0.92)
  })

  it('computes direct links, shared neighbor intersection, and exclusive relations deterministically', () => {
    const leftRes = makeResponse({
      edges: [
        { source: '需求分析', target: '系统设计', label: '引出' },
        { source: '需求分析', target: '软件需求规格说明书', label: '输出' },
        { source: '需求分析', target: '软件工程', label: '属于阶段' },
        { source: '需求分析', target: '数据流图DFD', label: '使用' },
      ],
    })
    const rightRes = makeResponse({
      edges: [
        { source: '系统设计', target: '软件工程', label: '属于阶段' },
        { source: '软件需求规格说明书', target: '系统设计', label: '作为输入指导' },
        { source: '系统设计', target: '架构模式', label: '选用' },
      ],
    })

    const result = compareConcepts('需求分析', leftRes, '系统设计', rightRes)

    expect(result.sameEntity).toBe(false)
    expect(result.directLinks).toEqual([
      { subject: '需求分析', predicate: '引出', object: '系统设计', direction: 'left-to-right' },
    ])
    expect(result.sharedNeighbors).toEqual([
      {
        entity: '软件工程',
        leftPredicates: ['属于阶段 →'],
        rightPredicates: ['属于阶段 →'],
      },
      {
        entity: '软件需求规格说明书',
        leftPredicates: ['输出 →'],
        rightPredicates: ['← 作为输入指导'],
      },
    ])
    expect(result.leftExclusive.map(item => item.counterpart)).toEqual(['数据流图DFD'])
    expect(result.rightExclusive.map(item => item.counterpart)).toEqual(['架构模式'])
    expect(buildConceptCompareTutorPrompt(result)).toContain('需求分析')
    expect(buildConceptCompareTutorPrompt(result)).toContain('系统设计')
  })

  it('flags identical or offline concepts without fabricating triples', () => {
    const same = compareConcepts('需求分析', makeResponse(), '需求分析', makeResponse())
    expect(same.sameEntity).toBe(true)
    expect(same.directLinks).toEqual([])
    expect(same.sharedNeighbors).toEqual([])

    const offline = compareConcepts(
      '结构化分析SA',
      makeResponse({ available: false, edges: [{ source: 'a', target: 'b', label: 'x' }] }),
      '面向对象分析OOA',
      makeResponse({ available: true, edges: [{ source: '面向对象分析OOA', target: '用例图', label: '使用' }] }),
    )
    expect(offline.leftAvailable).toBe(false)
    expect(offline.rightAvailable).toBe(true)
    expect(offline.leftTripleCount).toBe(0)
    expect(offline.rightTripleCount).toBe(1)
  })
})
