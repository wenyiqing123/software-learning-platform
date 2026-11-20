import { describe, expect, it } from 'vitest'
import { mapTriplesToGraph } from './knowledgeGraph'

describe('mapTriplesToGraph', () => {
  it('creates unique nodes and keeps each relation and relevance score', () => {
    const result = mapTriplesToGraph([
      { subject: '需求分析', predicate: '交付文档', object: '软件需求规格说明书', score: 0.91 },
      { subject: '需求分析', predicate: '方法', object: '结构化分析 SA', score: 0.72 },
    ])

    expect(result.nodes).toHaveLength(3)
    expect(result.edges).toEqual([
      { source: '需求分析', target: '软件需求规格说明书', label: '交付文档', score: 0.91 },
      { source: '需求分析', target: '结构化分析 SA', label: '方法', score: 0.72 },
    ])
  })

  it('deduplicates repeated triples and returns an empty graph for no evidence', () => {
    const triple = { subject: 'UML', predicate: '包含', object: '用例图', score: 0.8 }
    expect(mapTriplesToGraph([triple, triple]).edges).toHaveLength(1)
    expect(mapTriplesToGraph([])).toEqual({ nodes: [], edges: [] })
  })
})
