import { describe, expect, it } from 'vitest'
import { contextFromQuery, courseHref, graphHref, practiceHref, readLastLearningContext, rememberLearningContext, returnHref, tutorHref } from './learningContext'

describe('learning context navigation', () => {
  it('builds context-preserving links for the learning loop', () => {
    const context = { courseId: 1, sectionId: 8, knowledgePointId: 12, entity: '数据流图DFD' }
    expect(graphHref(context)).toEqual({ path: '/knowledge', query: { courseId: '1', sectionId: '8', knowledgePointId: '12', entity: '数据流图DFD' } })
    expect(practiceHref(context)).toEqual({ path: '/practice', query: { courseId: '1', sectionId: '8', knowledgePointId: '12' } })
    expect(tutorHref(context)).toEqual({ path: '/tutor', query: { entity: '数据流图DFD', courseId: '1', sectionId: '8', knowledgePointId: '12' } })
    expect(courseHref(context)).toEqual({ path: '/courses', query: { courseId: '1', sectionId: '8', knowledgePointId: '12' } })
  })

  it('normalizes route query values and ignores invalid ids', () => {
    expect(contextFromQuery({
      courseId: '1',
      sectionId: 'bad',
      knowledgePointId: '9',
      entity: '  DFD  ',
      courseTitle: '  软件工程  ',
      sectionTitle: '需求分析',
      knowledgePointTitle: '  数据流图  ',
      updatedAt: '2026-11-24T12:00:00.000Z',
    })).toEqual({
      courseId: 1,
      knowledgePointId: 9,
      entity: 'DFD',
      courseTitle: '软件工程',
      sectionTitle: '需求分析',
      knowledgePointTitle: '数据流图',
      updatedAt: '2026-11-24T12:00:00.000Z',
    })
  })

  it('persists the last learning context with timestamp and handles legacy storage format', () => {
    localStorage.setItem('slp-last-learning-context', '{bad')
    expect(() => rememberLearningContext({ courseId: 1, sectionId: 8 })).not.toThrow()
    const saved = JSON.parse(localStorage.getItem('slp-last-learning-context')!)
    expect(saved.courseId).toBe(1)
    expect(saved.sectionId).toBe(8)
    expect(typeof saved.updatedAt).toBe('string')

    // 兼容无 updatedAt 的旧格式
    localStorage.setItem('slp-last-learning-context', JSON.stringify({ courseId: 2, sectionId: 3, entity: '体系结构' }))
    const restored = readLastLearningContext()
    expect(restored).toEqual({ courseId: 2, sectionId: 3, entity: '体系结构' })
  })

  it('provides safe returnHref with fallback to /courses when context is missing', () => {
    expect(returnHref(null)).toEqual({ path: '/courses' })
    expect(returnHref(undefined)).toEqual({ path: '/courses' })
    expect(returnHref({})).toEqual({ path: '/courses' })
    expect(returnHref({ entity: '孤立概念' })).toEqual({ path: '/courses' })
    expect(returnHref({ courseId: 5, sectionId: 10 })).toEqual({
      path: '/courses',
      query: { courseId: '5', sectionId: '10' },
    })
  })
})
