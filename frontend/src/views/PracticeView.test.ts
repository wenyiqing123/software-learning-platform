import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import PracticeView from './PracticeView.vue'

const learning = vi.hoisted(() => ({
  questions: vi.fn(), submitAnswer: vi.fn(), diagnoseAnswer: vi.fn(), overview: vi.fn(),
}))
vi.mock('../api/client', () => ({ learningApi: learning }))

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/practice', component: PracticeView },
    { path: '/recommendations', component: { template: '<div />' } },
    { path: '/wrong-book', component: { template: '<div />' } },
    { path: '/knowledge', component: { template: '<div />' } },
    { path: '/courses', component: { template: '<div />' } },
  ] })
  await router.push('/practice')
  await router.isReady()
  return mount(PracticeView, { global: { plugins: [router] } })
}

beforeEach(() => {
  vi.resetAllMocks()
  learning.overview.mockResolvedValue({ wrongCount: 1 })
  learning.questions.mockResolvedValue([{ id: 41, stem: '加工如何命名？', type: 'SINGLE', options: [{ key: 'A', text: '动宾短语' }, { key: 'B', text: '名词短语' }], knowledgePoint: '数据流图' }])
  learning.submitAnswer.mockResolvedValue({ questionId: 41, correct: false, correctAnswer: ['A'], explanation: '加工表示处理逻辑。' })
  learning.diagnoseAnswer.mockResolvedValue([{ optionKey: 'B', optionText: '名词短语', knowledgePointId: 4, knowledgePointName: '加工命名', rationale: '该选项把加工名称误当成数据对象名称。', sectionId: 101, sectionTitle: '加工命名规则', practiceQuestionId: 42 }])
})

describe('quiz diagnosis feedback', () => {
  it('shows only configured evidence after a wrong answer and links remediation', async () => {
    const wrapper = await mountPage()
    await flushPromises()
    await wrapper.findAll('button.answer-option')[1].trigger('click')
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()

    expect(learning.diagnoseAnswer).toHaveBeenCalledWith(41, ['B'])
    expect(wrapper.text()).toContain('可能混淆：加工命名')
    expect(wrapper.text()).toContain('该选项把加工名称误当成数据对象名称。')
    expect(wrapper.find('a[href*="sectionId=101"]').exists()).toBe(true)
    expect(wrapper.find('a[href*="questionId=42"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
