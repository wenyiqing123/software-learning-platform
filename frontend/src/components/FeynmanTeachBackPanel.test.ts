import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import FeynmanTeachBackPanel from './FeynmanTeachBackPanel.vue'

const learning = vi.hoisted(() => ({
  knowledgePoints: vi.fn(),
  evaluateFeynmanExplanation: vi.fn(),
}))

vi.mock('../api/client', () => ({
  learningApi: learning,
  apiError: vi.fn((err: any, fallback?: string) => err?.message || fallback || '请求失败'),
}))

async function mountPanel(props: { initialKnowledgePoint?: string; courseId?: number } = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/tutor', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/practice', component: { template: '<div />' } },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/flashcards', component: { template: '<div />' } },
    ],
  })
  await router.push('/tutor')
  await router.isReady()
  const wrapper = mount(FeynmanTeachBackPanel, {
    props,
    global: { plugins: [router] },
  })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.clearAllMocks()
  learning.knowledgePoints.mockResolvedValue([
    { id: 11, courseId: 3, chapterId: 1, name: '需求分析', graphEntity: '需求分析', sortOrder: 1, mastery: 75 },
    { id: 12, courseId: 3, chapterId: 1, name: '数据流图 DFD', graphEntity: '数据流图DFD', sortOrder: 2, mastery: 45 },
  ])
})

afterEach(() => vi.restoreAllMocks())

describe('FeynmanTeachBackPanel', () => {
  it('prefills initial topic without auto-submitting and enforces 10-char minimum', async () => {
    const { wrapper } = await mountPanel({ initialKnowledgePoint: '需求分析', courseId: 3 })

    expect(learning.evaluateFeynmanExplanation).not.toHaveBeenCalled()
    expect((wrapper.get('[data-testid="feynman-topic-input"]').element as HTMLInputElement).value).toBe('需求分析')
    expect(wrapper.get('[data-testid="feynman-submit-btn"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="feynman-explanation-input"]').setValue('太短了')
    expect(wrapper.get('[data-testid="feynman-submit-btn"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="feynman-explanation-input"]').setValue('需求分析用于明确软件目标、业务边界与系统功能约束。')
    expect(wrapper.get('[data-testid="feynman-submit-btn"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('submits explanation, renders grounded diagnostic sections, supports follow-up revision and graph navigation', async () => {
    learning.evaluateFeynmanExplanation.mockResolvedValueOnce({
      knowledgePoint: '需求分析',
      coveredPoints: ['说明了分析用户业务目标的核心目的'],
      missingTriples: [{ subject: '需求分析', predicate: '输出', object: '软件需求规格说明书', score: 0.92 }],
      misconceptions: ['注意区分 **需求分析** 与系统概要设计。'],
      followUpQuestion: '需求分析最终通过哪份规范化文档冻结？',
      evidenceTriples: [
        { subject: '需求分析', predicate: '输出', object: '软件需求规格说明书', score: 0.92 },
        { subject: '需求分析', predicate: '使用', object: '数据流图DFD', score: 0.85 },
      ],
    })

    const { wrapper, router } = await mountPanel({ initialKnowledgePoint: '需求分析', courseId: 3 })
    await wrapper.get('[data-testid="feynman-explanation-input"]').setValue(
      '需求分析主要用来调研业务方的真实诉求并确定系统边界。',
    )
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(learning.evaluateFeynmanExplanation).toHaveBeenCalledWith({
      knowledgePoint: '需求分析',
      studentExplanation: '需求分析主要用来调研业务方的真实诉求并确定系统边界。',
      courseId: 3,
    })

    expect(wrapper.get('[data-testid="feynman-covered"]').text()).toContain('说明了分析用户业务目标的核心目的')
    expect(wrapper.get('[data-testid="feynman-missing"]').text()).toContain('软件需求规格说明书')
    expect(wrapper.get('[data-testid="feynman-misconceptions"] strong').text()).toBe('需求分析')
    expect(wrapper.get('[data-testid="feynman-followup"]').text()).toContain('需求分析最终通过哪份规范化文档冻结？')

    const evidenceDetails = wrapper.get('[data-testid="feynman-evidence-details"]')
    expect((evidenceDetails.element as HTMLDetailsElement).open).toBe(false)
    expect(evidenceDetails.text()).toContain('本次对照的 2 条真实图谱证据')
    expect(wrapper.get('[data-testid="feynman-disclaimer"]').text()).toContain(
      '基于当前课程知识图谱三元组对照生成的辅助自检建议，仅供学习参考，不作为正式成绩评定',
    )

    // 基于追问继续完善讲解不应清空已有内容
    await wrapper.get('[data-testid="feynman-refine-btn"]').trigger('click')
    const draft = (wrapper.get('[data-testid="feynman-explanation-input"]').element as HTMLTextAreaElement).value
    expect(draft).toContain('需求分析主要用来调研业务方的真实诉求并确定系统边界。')
    expect(draft).toContain('需求分析最终通过哪份规范化文档冻结？')

    // 点击遗漏图谱关联跳转到对应图谱实体，同时展示定向练习/回看小节/闪卡闭环链接
    expect(wrapper.find('[data-testid="feynman-remediate-practice"]').attributes('href')).toContain('/practice?knowledgePointId=11')
    expect(wrapper.find('[data-testid="feynman-remediate-course"]').attributes('href')).toContain('/courses?courseId=3&knowledgePointId=11')
    expect(wrapper.find('[data-testid="feynman-remediate-flashcard"]').attributes('href')).toContain('/flashcards?courseId=3')

    await wrapper.get('[data-testid="feynman-missing"] button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/knowledge')
    expect(router.currentRoute.value.query.entity).toBe('需求分析')
    wrapper.unmount()
  })

  it('preserves student draft and displays honest error alert when evaluation fails', async () => {
    learning.evaluateFeynmanExplanation.mockRejectedValueOnce(new Error('503 KNOWLEDGE_UNAVAILABLE'))
    const { wrapper } = await mountPanel({ initialKnowledgePoint: '数据流图DFD' })

    const draftText = '数据流图用于刻画数据如何在加工与数据存储之间流转。'
    await wrapper.get('[data-testid="feynman-explanation-input"]').setValue(draftText)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="feynman-error"]').text()).toContain('503 KNOWLEDGE_UNAVAILABLE')
    expect((wrapper.get('[data-testid="feynman-explanation-input"]').element as HTMLTextAreaElement).value).toBe(draftText)
    wrapper.unmount()
  })
})
