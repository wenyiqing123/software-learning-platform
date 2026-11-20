import { describe, expect, it } from 'vitest'
import { gradeAnswer } from './quiz'

describe('gradeAnswer', () => {
  it('treats selected answers as a set for order-independent questions', () => {
    expect(gradeAnswer(['B', 'A'], ['A', 'B'])).toBe(true)
  })

  it('rejects missing answers, extra answers and duplicate selections', () => {
    expect(gradeAnswer(['A'], ['A', 'B'])).toBe(false)
    expect(gradeAnswer(['A', 'B', 'C'], ['A', 'B'])).toBe(false)
    expect(gradeAnswer(['A', 'A', 'B'], ['A', 'B'])).toBe(false)
  })

  it('rejects empty submissions', () => {
    expect(gradeAnswer([], ['A'])).toBe(false)
  })
})
