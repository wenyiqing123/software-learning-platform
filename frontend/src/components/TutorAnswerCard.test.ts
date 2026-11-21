import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import TutorAnswerCard from './TutorAnswerCard.vue'

vi.mock('./MarkdownContent.vue', () => ({ default: { props: ['content'], template: '<div class="markdown">{{ content }}</div>' } }))

describe('TutorAnswerCard', () => {
  it('never renders the RANKED backend prompt while keeping real evidence visible', () => {
    const wrapper = mount(TutorAnswerCard, { props: { showPrompt: true, item: { mode: 'RANKED', answer: '加工命名使用动宾短语。', prompt: '[GROUNDED_ANSWER_V3] hidden', triples: [{ subject: '加工', predicate: '命名', object: '动宾短语', score: .9 }] } } })
    expect(wrapper.text()).toContain('加工')
    expect(wrapper.text()).toContain('动宾短语')
    expect(wrapper.text()).not.toContain('GROUNDED_ANSWER_V3')
    expect(wrapper.text()).not.toContain('hidden')
  })
})
