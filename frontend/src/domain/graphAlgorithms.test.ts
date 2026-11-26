import { describe, expect, it } from 'vitest'
import {
  buildPrerequisiteRoadmapStages,
  buildSevenDayReviewForecast,
  computeNodeDegrees,
  findEntityBridgePath,
  getSpacedRatingPreviews,
  mergeGraphResponses,
} from './graphAlgorithms'

describe('graphAlgorithms', () => {
  it('merges 1-hop/2-hop graph expansions cumulatively and computes node degrees', () => {
    const merged = mergeGraphResponses(
      {
        available: true,
        nodes: [{ id: '需求分析', label: '需求分析' }, { id: '数据流图DFD', label: '数据流图DFD' }],
        edges: [{ source: '需求分析', target: '数据流图DFD', label: '建模工具' }],
        evidence: [{ subject: '需求分析', predicate: '建模工具', object: '数据流图DFD' }],
      },
      {
        available: true,
        nodes: [{ id: '数据流图DFD', label: '数据流图DFD' }, { id: '数据字典', label: '数据字典' }],
        edges: [
          { source: '需求分析', target: '数据流图DFD', label: '建模工具' },
          { source: '数据流图DFD', target: '数据字典', label: '配套定义' },
        ],
        evidence: [{ subject: '数据流图DFD', predicate: '配套定义', object: '数据字典' }],
      },
    )

    expect(merged.nodes).toHaveLength(3)
    expect(merged.edges).toHaveLength(2)
    expect(merged.evidence).toHaveLength(2)

    const degrees = computeNodeDegrees(merged.nodes, merged.edges)
    expect(degrees.get('数据流图DFD')).toBe(2)
    expect(degrees.get('需求分析')).toBe(1)

    const bridge = findEntityBridgePath(merged.edges, '需求分析', '数据字典')
    expect(bridge.found).toBe(true)
    expect(bridge.nodeIds).toEqual(['需求分析', '数据流图DFD', '数据字典'])
    expect(bridge.steps).toHaveLength(2)
  })

  it('builds topological roadmap stages without looping on cycles', () => {
    const stages = buildPrerequisiteRoadmapStages(
      [
        { id: 1, courseId: 1, chapterId: 1, name: '需求获取', graphEntity: '需求获取', mastery: 90, attemptCount: 4, correctCount: 4, dueReviewCount: 0, status: 'MASTERED', prerequisiteIds: [] },
        { id: 2, courseId: 1, chapterId: 1, name: '需求规格', graphEntity: 'SRS', mastery: 50, attemptCount: 2, correctCount: 1, dueReviewCount: 1, status: 'IN_PROGRESS', prerequisiteIds: [1] },
        { id: 3, courseId: 1, chapterId: 2, name: '架构设计', graphEntity: '架构设计', mastery: 0, attemptCount: 0, correctCount: 0, dueReviewCount: 0, status: 'BLOCKED', prerequisiteIds: [2] },
      ],
      [],
    )

    expect(stages).toHaveLength(3)
    expect(stages[0].nodes.map((n) => n.name)).toEqual(['需求获取'])
    expect(stages[1].nodes.map((n) => n.name)).toEqual(['需求规格'])
    expect(stages[2].nodes.map((n) => n.name)).toEqual(['架构设计'])
  })

  it('exposes transparent spaced review intervals and 7-day forecast buckets', () => {
    const previews = getSpacedRatingPreviews()
    expect(previews.map((p) => p.correctText)).toEqual(['10 分钟后', '1 天后', '3 天后', '7 天后'])
    expect(previews.every((p) => p.wrongText === '10 分钟后')).toBe(true)

    const forecast = buildSevenDayReviewForecast(
      [
        { wrongQuestionId: 1, questionId: 10, question: 'Q1', questionType: 'SINGLE', options: [], wrongCount: 1, dueAt: '2026-11-25T08:00:00', previousIntervalMinutes: 10 },
        { wrongQuestionId: 2, questionId: 11, question: 'Q2', questionType: 'SINGLE', options: [], wrongCount: 1, dueAt: '2026-11-28T09:00:00', previousIntervalMinutes: 1440 },
      ],
      [{ wrongQuestionId: 3, questionId: 12, question: 'Q3', questionType: 'SINGLE', options: [], wrongCount: 1, dueAt: '2026-11-26T08:00:00', previousIntervalMinutes: 10 }],
      new Date(2026, 10, 26, 12, 0, 0),
    )

    expect(forecast.pausedCount).toBe(1)
    expect(forecast.buckets).toHaveLength(7)
    expect(forecast.buckets[0].dueCount).toBe(1)
    expect(forecast.buckets[2].dueCount).toBe(1)
  })
})
