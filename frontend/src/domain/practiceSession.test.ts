import { describe, expect, it } from 'vitest'
import { buildNavigatorSlots, computeReviewMatrix, computeSessionSummary } from './practiceSession'

const SAMPLE_QUESTIONS = [
  { id: 1, stem: '需求分析的产出物是什么？', knowledgePoint: '需求分析' },
  { id: 2, stem: '数据流图中的加工如何命名？', knowledgePoint: '数据流图DFD' },
  { id: 3, stem: '用例图描述系统外部视角下的什么？', knowledgePoint: '用例图' },
]

describe('practiceSession domain helpers', () => {
  it('builds navigator slots with distinct draft, correct, wrong, unanswered, and flagged states without leaking answers', () => {
    const slots = buildNavigatorSlots(SAMPLE_QUESTIONS, {
      currentIndex: 1,
      draftsById: { 2: ['B'] },
      outcomesById: {
        1: {
          questionId: 1,
          stem: SAMPLE_QUESTIONS[0].stem,
          knowledgePoint: '需求分析',
          selected: ['B'],
          correct: false,
          correctAnswer: ['A'],
          explanation: '应产出 SRS。',
        },
      },
      flaggedIds: new Set([2, 3]),
    })

    expect(slots).toEqual([
      { index: 0, numberLabel: '01', questionId: 1, status: 'wrong', flagged: false, isCurrent: false },
      { index: 1, numberLabel: '02', questionId: 2, status: 'draft', flagged: true, isCurrent: true },
      { index: 2, numberLabel: '03', questionId: 3, status: 'unanswered', flagged: true, isCurrent: false },
    ])
  })

  it('computes deterministic summary and filtered post-session review matrix while hiding unsubmitted answers', () => {
    const outcomes = {
      1: {
        questionId: 1,
        stem: SAMPLE_QUESTIONS[0].stem,
        knowledgePoint: '需求分析',
        selected: ['A'],
        correct: true,
        correctAnswer: ['A'],
      },
      2: {
        questionId: 2,
        stem: SAMPLE_QUESTIONS[1].stem,
        knowledgePoint: '数据流图DFD',
        selected: ['B'],
        correct: false,
        correctAnswer: ['A'],
        explanation: '加工采用动宾短语。',
      },
    }
    const flagged = new Set([2, 3])

    const summary = computeSessionSummary(SAMPLE_QUESTIONS, outcomes, flagged)
    expect(summary).toEqual({
      total: 3,
      answeredCount: 2,
      correctCount: 1,
      wrongCount: 1,
      flaggedCount: 2,
      accuracyLabel: '50%',
      missedPoints: ['数据流图DFD'],
    })

    const allRows = computeReviewMatrix(SAMPLE_QUESTIONS, outcomes, flagged, 'all')
    expect(allRows).toHaveLength(3)
    expect(allRows[2].outcome).toBeUndefined()

    const wrongRows = computeReviewMatrix(SAMPLE_QUESTIONS, outcomes, flagged, 'wrong')
    expect(wrongRows.map((r) => r.questionId)).toEqual([2])

    const flaggedRows = computeReviewMatrix(SAMPLE_QUESTIONS, outcomes, flagged, 'flagged')
    expect(flaggedRows.map((r) => r.questionId)).toEqual([2, 3])
  })
})
