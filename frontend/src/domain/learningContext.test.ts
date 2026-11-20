import { describe, expect, it } from 'vitest'
import { contextFromQuery, graphHref, practiceHref, rememberLearningContext, tutorHref } from './learningContext'

describe('learning context navigation', () => {
  it('builds context-preserving links for the learning loop', () => {
    const context = { courseId: 1, sectionId: 8, knowledgePointId: 12, entity: '数据流图DFD' }
    expect(graphHref(context)).toEqual({ path: '/knowledge', query: { courseId: '1', sectionId: '8', knowledgePointId: '12', entity: '数据流图DFD' } })
    expect(practiceHref(context)).toEqual({ path: '/practice', query: { courseId: '1', sectionId: '8', knowledgePointId: '12' } })
    expect(tutorHref(context)).toEqual({ path: '/tutor', query: { entity: '数据流图DFD', courseId: '1', sectionId: '8', knowledgePointId: '12' } })
  })

  it('normalizes route query values and ignores invalid ids', () => {
    expect(contextFromQuery({ courseId: '1', sectionId: 'bad', knowledgePointId: '9', entity: '  DFD  ' })).toEqual({ courseId: 1, knowledgePointId: 9, entity: 'DFD' })
  })

  it('persists the last learning context without throwing on malformed storage', () => {
    localStorage.setItem('slp-last-learning-context', '{bad')
    expect(() => rememberLearningContext({ courseId: 1, sectionId: 8 })).not.toThrow()
    expect(JSON.parse(localStorage.getItem('slp-last-learning-context')!)).toEqual({ courseId: 1, sectionId: 8 })
  })
})
