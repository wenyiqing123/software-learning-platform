import { describe, expect, it } from 'vitest'
import {
  alignSentenceCitations,
  buildCitationAlignment,
  normalizeCitationTriples,
  splitMarkdownLineIntoSentences,
} from './citationAlignment'

describe('citationAlignment', () => {
  it('supports both subject/predicate/object and source/relation/target shapes without fabricating indices', () => {
    const triples = normalizeCitationTriples([
      { source: '数据流', relation: '命名规则', target: '名词短语' },
      { subject: '加工', predicate: '命名规范', object: '动宾短语', score: 0.91 },
      { source: '', relation: '无效', target: '缺少主体' },
    ])

    expect(triples).toEqual([
      { index: 1, subject: '数据流', predicate: '命名规则', object: '名词短语' },
      { index: 2, subject: '加工', predicate: '命名规范', object: '动宾短语', score: 0.91 },
    ])
  })

  it('skips fenced code blocks, inline code spans, and link URLs during matching', () => {
    const triples = [
      { source: '数据流', relation: '表示', target: '箭头线' },
      { source: 'UML', relation: '包含', target: '用例图' },
    ]
    const answer = [
      '请先理解基础概念，例如参考 [外部文档](https://example.org/UML/数据流)。',
      '',
      '这里有行内代码 `数据流 -> 箭头线` 与词根 `umlgraph`（不应触发伪匹配）。',
      '',
      '```mermaid',
      'flowchart LR',
      '  A[数据流] --> B[UML用例图]',
      '```',
      '',
      '实际正文明确指出：**数据流**在图中用箭头线表示。',
    ].join('\n')

    const report = buildCitationAlignment(answer, triples)
    expect(report.referencedIndices).toEqual([1])
    expect(report.coveredTripleCount).toBe(1)
    expect(report.totalTripleCount).toBe(2)
    expect(report.coverageText).toBe('回复提及了 1/2 条已检索图谱证据中的实体')
    expect(report.coverageText).not.toMatch(/正确率|准确率|置信度/)
  })

  it('prefers exact dual-endpoint matches over single-endpoint matches within a sentence', () => {
    const triples = normalizeCitationTriples([
      { source: '需求分析', relation: '产出', target: '软件需求规格说明书' },
      { source: '需求分析', relation: '辅助工具', target: '数据流图' },
    ])

    expect(alignSentenceCitations('需求分析阶段最终产出软件需求规格说明书。', triples)).toEqual([1])
    expect(alignSentenceCitations('可以结合数据流图进行业务拆解。', triples)).toEqual([2])
    expect(alignSentenceCitations('这段话没有涉及上述图谱实体。', triples)).toEqual([])
  })

  it('splits sentences safely without breaking bold markers, inline code, or version decimals', () => {
    const line = '核心概念如 **数据流图 v1.2** 需要配合 `校验权限()` 使用。此外应关注系统边界！'
    const parts = splitMarkdownLineIntoSentences(line)

    expect(parts).toHaveLength(2)
    expect(parts[0]).toBe('核心概念如 **数据流图 v1.2** 需要配合 `校验权限()` 使用。')
    expect(parts[1].trim()).toBe('此外应关注系统边界！')
  })

  it('returns empty alignment cleanly when triples are missing or offline', () => {
    const report = buildCitationAlignment('任意软件工程解释正文。', [])
    expect(report.triples).toEqual([])
    expect(report.referencedIndices).toEqual([])
    expect(report.totalTripleCount).toBe(0)
    expect(report.coverageText).toBe('')
  })
})
