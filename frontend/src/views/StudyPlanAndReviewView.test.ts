import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ReviewView from './ReviewView.vue'
import StudyPlanView from './StudyPlanView.vue'

const learning = vi.hoisted(() => ({
  spacedReviewsDue: vi.fn(), spacedReviewsPaused: vi.fn(), submitSpacedReview: vi.fn(),
  setSpacedReviewState: vi.fn(), studyPlanToday: vi.fn(), generateStudyPlan: vi.fn(), updateStudyPlanTask: vi.fn(),
}))
vi.mock('../api/client', () => ({ learningApi: learning }))
const card = { wrongQuestionId: 9, questionId: 31, question: '数据流图加工应如何命名？', knowledgePoint: '数据流图', questionType: 'SINGLE', options: [{ key: 'A', text: '动词加宾语' }, { key: 'B', text: '名词短语' }], wrongCount: 2, dueAt: '2026-11-19T08:00:00', previousIntervalMinutes: 10 }
const task = { id: 4, type: 'READING', targetId: 101, title: '阅读需求分析', detail: '软件工程 · 第一章', minutes: 15, reason: '课程小节尚未完成', state: 'PENDING', path: '/courses?courseId=1&sectionId=101' }
async function mountWithRouter(component: typeof ReviewView | typeof StudyPlanView, path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/review', component: ReviewView }, { path: '/study-plan', component: StudyPlanView },
    { path: '/wrong-book', component: { template: '<div />' } },
    { path: '/courses', component: { template: '<div />' } },
  ] })
  await router.push(path); await router.isReady()
  return mount(component, { global: { plugins: [router] } })
}
beforeEach(() => {
  vi.resetAllMocks()
  learning.spacedReviewsDue.mockResolvedValue([structuredClone(card)])
  learning.spacedReviewsPaused.mockResolvedValue([])
  learning.submitSpacedReview.mockResolvedValue({ attemptId: 2, questionId: 31, correct: true, correctAnswer: ['A'], explanation: '加工描述处理逻辑', recallRating: 2, intervalMinutes: 4320, nextDueAt: '2026-11-22T08:00:00', mastered: false, consecutiveSuccessfulReviews: 1 })
  learning.studyPlanToday.mockResolvedValue({ id: 1, date: '2026-11-19', budgetMinutes: 45, tasks: [structuredClone(task)] })
  learning.generateStudyPlan.mockResolvedValue({ id: 1, date: '2026-11-19', budgetMinutes: 60, tasks: [structuredClone(task)] })
  learning.updateStudyPlanTask.mockResolvedValue({ ...task, state: 'SKIPPED' })
})

describe('spaced review page', () => {
  it('requires an answer and recall rating before submitting', async () => {
    const wrapper = await mountWithRouter(ReviewView, '/review'); await flushPromises()
    expect(wrapper.text()).toContain(card.question)
    await wrapper.get('button.button-dark').trigger('click')
    expect(learning.submitSpacedReview).not.toHaveBeenCalled()
    await wrapper.get('button.answer-option').trigger('click')
    await wrapper.get('input[name="recall-rating"][value="2"]').setValue()
    await wrapper.get('button.button-dark').trigger('click'); await flushPromises()
    expect(learning.submitSpacedReview).toHaveBeenCalledWith(9, expect.objectContaining({ answer: ['A'], recallRating: 2, requestKey: expect.any(String) }))
    expect(wrapper.text()).toContain('4320 分钟后')
    wrapper.unmount()
  })

  it('loads the wrong question selected by the daily plan link', async () => {
    const wrapper = await mountWithRouter(ReviewView, '/review?wrongQuestionId=9'); await flushPromises()
    expect(learning.spacedReviewsDue).toHaveBeenCalledWith(9)
    wrapper.unmount()
  })

  it('shows and resumes a paused review item', async () => {
    learning.spacedReviewsDue.mockResolvedValue([])
    learning.spacedReviewsPaused.mockResolvedValue([structuredClone(card)])
    const wrapper = await mountWithRouter(ReviewView, '/review'); await flushPromises()
    expect(wrapper.text()).toContain('暂停题目 1')
    await wrapper.findAll('button').find(button => button.text().includes('暂停题目'))!.trigger('click')
    expect(wrapper.text()).toContain('已暂停 1 题')
    await wrapper.findAll('button').find(button => button.text().includes('恢复并立即安排'))!.trigger('click'); await flushPromises()
    expect(learning.setSpacedReviewState).toHaveBeenCalledWith(9, 'ACTIVE')
    wrapper.unmount()
  })

})

describe('daily plan page', () => {
  it('generates a budgeted plan and can skip a task', async () => {
    const wrapper = await mountWithRouter(StudyPlanView, '/study-plan'); await flushPromises()
    expect(wrapper.text()).toContain('阅读需求分析')
    learning.studyPlanToday.mockResolvedValue({ id: 1, date: '2026-11-19', budgetMinutes: 60, tasks: [{ ...structuredClone(task), state: 'SKIPPED' }] })
    await wrapper.get('select').setValue('60')
    await wrapper.findAll('button').find(button => button.text().includes('生成计划'))!.trigger('click'); await flushPromises()
    expect(learning.generateStudyPlan).toHaveBeenCalledWith(60)
    await wrapper.get('button[title="跳过任务"]').trigger('click'); await flushPromises()
    expect(learning.updateStudyPlanTask).toHaveBeenCalledWith(4, 'SKIP')
    expect(wrapper.text()).toContain('已跳过')
    wrapper.unmount()
  })

  it('shows non-blocking floating toast and task highlight when prerequisite behavior is missing', async () => {
    const wrapper = await mountWithRouter(StudyPlanView, '/study-plan'); await flushPromises()
    learning.updateStudyPlanTask.mockRejectedValueOnce({
      response: {
        status: 409,
        data: { message: '请先完成对应课程阅读、正确作答或复习，再标记计划任务完成' },
      },
    })

    await wrapper.get('.plan-complete-btn').trigger('click')
    await flushPromises()

    // 顶部文档流错误条不推出位移，而是弹出固定定位的非打扰悬浮提醒
    expect(wrapper.find('.inline-notice').exists()).toBe(false)
    const toast = wrapper.get('[data-testid="study-plan-hint-toast"]')
    expect(toast.attributes('role')).toBe('alert')
    expect(toast.text()).toContain('请先完成对应课程阅读、正确作答或复习，再标记计划任务完成')
    expect(toast.text()).toContain('阅读需求分析')
    expect(toast.get('a.plan-hint-link').attributes('href')).toContain('/courses?courseId=1&sectionId=101')
    expect(wrapper.get('.plan-task').classes()).toContain('is-prereq-blocked')

    await wrapper.get('.plan-hint-close').trigger('click')
    expect(wrapper.find('[data-testid="study-plan-hint-toast"]').exists()).toBe(false)
    expect(wrapper.get('.plan-task').classes()).not.toContain('is-prereq-blocked')
    wrapper.unmount()
  })
})
