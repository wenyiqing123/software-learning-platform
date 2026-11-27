import { describe, expect, it } from 'vitest'
import type { KnowledgePoint } from '../api/client'
import { buildMissingTripleRemediations, normalizeConceptTerm, resolveMatchedKnowledgePoint } from './feynmanRemediation'

const SAMPLE_POINTS: KnowledgePoint[] = [
  { id: 11, courseId: 3, chapterId: 1, name: '需求分析', graphEntity: '需求分析', sortOrder: 1, mastery: 75 },
  { id: 12, courseId: 3, chapterId: 1, name: '数据流图 DFD', graphEntity: '数据流图DFD', sortOrder: 2, mastery: 45 },
  { id: 13, courseId: 3, chapterId: 2, name: '软件需求规格说明书', graphEntity: '软件需求规格说明书', sortOrder: 3, mastery: 30 },
]

describe('feynmanRemediation', () => {
  it('normalizes whitespace/punctuation and prioritizes object over subject when matching knowledge points', () => {
    expect(normalizeConceptTerm('数据流图 DFD（结构化）')).toBe('数据流图dfd结构化')

    const matchedSpecific = resolveMatchedKnowledgePoint(['软件需求规格说明书', '需求分析'], SAMPLE_POINTS)
    expect(matchedSpecific?.id).toBe(13)

    const matchedFuzzy = resolveMatchedKnowledgePoint(['未知结果', '数据流图 DFD'], SAMPLE_POINTS)
    expect(matchedFuzzy?.id).toBe(12)
  })

  it('builds traceable practice, course-section, and flashcard targets without fabricating missing points', () => {
    const plans = buildMissingTripleRemediations(
      [
        { subject: '需求分析', predicate: '输出', object: '软件需求规格说明书', score: 0.92 },
        { subject: '某个未收录概念', predicate: '包含', object: '外部拓展实体', score: 0.5 },
      ],
      '未知主题',
      SAMPLE_POINTS,
      3,
    )

    expect(plans[0].graphTarget).toBe('需求分析')
    expect(plans[0].matchedPoint?.id).toBe(13)
    expect(plans[0].practiceTarget).toEqual({ path: '/practice', query: { knowledgePointId: '13' } })
    expect(plans[0].courseTarget).toEqual({ path: '/courses', query: { courseId: '3', knowledgePointId: '13' } })
    expect(plans[0].flashcardsTarget).toEqual({ path: '/flashcards', query: { courseId: '3' } })

    expect(plans[1].matchedPoint).toBeNull()
    expect(plans[1].practiceTarget).toBeNull()
    expect(plans[1].courseTarget).toBeNull()
    expect(plans[1].flashcardsTarget).toEqual({ path: '/flashcards', query: { courseId: '3' } })
  })
})
