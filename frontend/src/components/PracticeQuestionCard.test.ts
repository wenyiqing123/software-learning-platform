import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import PracticeQuestionCard from './PracticeQuestionCard.vue'

describe('PracticeQuestionCard', () => {
  it('renders progress and configured diagnosis evidence after a wrong answer', () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/courses', component: { template: '<div />' } }, { path: '/practice', component: { template: '<div />' } }] })
    const wrapper = mount(PracticeQuestionCard, { global: { plugins: [router] }, props: { question: { id: 1, stem: '加工如何命名？', type: 'SINGLE', options: [{ key: 'A', text: '动宾短语' }, { key: 'B', text: '名词短语' }] }, index: 0, total: 4, questionType: '单选题', progress: 25, isMultiple: false, isTrueFalse: false, selected: ['B'], correctAnswers: ['A'], result: { questionId: 1, correct: false, correctAnswer: ['A'], explanation: '加工表示处理逻辑。' }, submitting: false, submitError: '', diagnoses: [{ optionKey: 'B', optionText: '名词短语', knowledgePointId: 4, knowledgePointName: '加工命名', rationale: '课程配置的混淆依据。', sectionId: 101, sectionTitle: '加工命名规则', practiceQuestionId: 2 }], diagnosisLoading: false, diagnosisError: '' } })
    expect(wrapper.find('[role="progressbar"]').attributes('aria-valuemax')).toBe('4')
    expect(wrapper.text()).toContain('可能混淆：加工命名')
    expect(wrapper.text()).toContain('课程配置的混淆依据')
  })
})
