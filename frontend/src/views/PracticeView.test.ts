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
    { path: '/tutor', component: { template: '<div />' } },
    { path: '/review', component: { template: '<div />' } },
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

  it('supports question navigator jump, uncertain flagging, pre-submit answer secrecy, and post-session review filter', async () => {
    learning.questions.mockResolvedValue([
      { id: 41, stem: '加工如何命名？', type: 'SINGLE', options: [{ key: 'A', text: '动宾短语' }, { key: 'B', text: '名词短语' }], knowledgePoint: '数据流图' },
      { id: 42, stem: '需求分析最终输出什么？', type: 'SINGLE', options: [{ key: 'A', text: 'SRS 说明书' }, { key: 'B', text: '源代码' }], knowledgePoint: '需求分析' },
    ])
    learning.submitAnswer.mockResolvedValueOnce({ questionId: 41, correct: false, correctAnswer: ['A'], explanation: '加工必须用动宾短语。' })

    const wrapper = await mountPage()
    await flushPromises()

    expect(wrapper.find('[data-testid="practice-navigator"]').exists()).toBe(true)
    // 切换第2题并标记存疑，提交前绝不显示正确答案
    await wrapper.get('[data-testid="practice-nav-slot-2"]').trigger('click')
    expect(wrapper.text()).toContain('需求分析最终输出什么？')
    await wrapper.get('[data-testid="practice-flag-btn"]').trigger('click')
    expect(wrapper.get('[data-testid="practice-flag-btn"]').text()).toContain('已标记存疑')
    expect(wrapper.find('.answer-feedback').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('正确答案：')

    // 切回第1题并提交错误答案
    await wrapper.get('[data-testid="practice-nav-slot-1"]').trigger('click')
    await wrapper.findAll('button.answer-option')[1].trigger('click')
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()

    // 打开逐题复盘清单并测试筛选
    await wrapper.get('[data-testid="practice-open-review-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="practice-review-panel"]').exists()).toBe(true)
    // 未提交的第2题严禁提前显示答案
    expect(wrapper.get('[data-testid="review-entry-42"]').text()).toContain('尚未提交判分')
    expect(wrapper.get('[data-testid="review-entry-42"]').text()).not.toContain('SRS 说明书')
    // 第1题已提交则正常显示答案和解析
    expect(wrapper.get('[data-testid="review-entry-41"]').text()).toContain('加工必须用动宾短语')

    await wrapper.get('[data-testid="review-filter-flagged"]').trigger('click')
    expect(wrapper.find('[data-testid="review-entry-41"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="review-entry-42"]').exists()).toBe(true)

    await wrapper.get('[data-testid="review-filter-wrong"]').trigger('click')
    expect(wrapper.find('[data-testid="review-entry-41"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="review-entry-42"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
