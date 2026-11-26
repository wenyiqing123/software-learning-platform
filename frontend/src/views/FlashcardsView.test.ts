import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import FlashcardsView from './FlashcardsView.vue'

const mocks = vi.hoisted(() => ({
  getCourses: vi.fn(),
  flashcards: vi.fn(),
  reviewFlashcard: vi.fn(),
}))

vi.mock('../api/client', () => ({
  api: { get: mocks.getCourses },
  apiError: (_cause: unknown, fallback: string) => fallback,
  learningApi: {
    flashcards: mocks.flashcards,
    reviewFlashcard: mocks.reviewFlashcard,
  },
}))

const sampleCards = [
  {
    knowledgePointId: 10,
    courseId: 1,
    courseName: '软件工程',
    chapterName: '第 1 章 需求分析',
    sectionId: 101,
    sectionTitle: '1.1 需求获取',
    objectives: ['区分用户目标与系统边界'],
    name: '需求分析',
    graphEntity: '需求分析',
    summary: '需求分析回答系统必须完成什么并明确系统边界。',
    masteryScore: 70,
    recallRating: null,
    reviewCount: 0,
    intervalMinutes: null,
    due: true,
    nextDueAt: null,
    lastReviewedAt: null,
  },
  {
    knowledgePointId: 11,
    courseId: 1,
    courseName: '软件工程',
    chapterName: '第 2 章 建模方法',
    sectionId: 102,
    sectionTitle: '2.1 数据流图',
    objectives: ['校验父图子图平衡'],
    name: '数据流图 DFD',
    graphEntity: '数据流图DFD',
    summary: '用外部实体、加工、数据流和数据存储刻画数据变换。',
    masteryScore: 55,
    recallRating: 2,
    reviewCount: 1,
    intervalMinutes: 4320,
    due: false,
    nextDueAt: '2026-11-29T08:00:00',
    lastReviewedAt: '2026-11-26T08:00:00',
  },
]

async function mountFlashcards() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/flashcards', component: FlashcardsView },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/tutor', component: { template: '<div />' } },
    ],
  })
  await router.push('/flashcards')
  await router.isReady()
  const wrapper = mount(FlashcardsView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  mocks.getCourses.mockResolvedValue({ data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] })
  mocks.flashcards.mockResolvedValue(structuredClone(sampleCards))
  mocks.reviewFlashcard.mockImplementation(async (id: number, rating: number) => ({
    ...sampleCards[0],
    knowledgePointId: id,
    recallRating: rating,
    reviewCount: 1,
    intervalMinutes: rating === 3 ? 10080 : 10,
    due: false,
  }))
})

describe('FlashcardsView', () => {
  it('flips card front/back, submits active recall rating to server, and advances queue', async () => {
    const { wrapper } = await mountFlashcards()

    expect(wrapper.get('[data-testid="flashcard-front"]').text()).toContain('需求分析')
    expect(wrapper.find('[data-testid="flashcard-back"]').exists()).toBe(false)

    await wrapper.get('[data-testid="flashcard-flip-btn"]').trigger('click')
    const back = wrapper.get('[data-testid="flashcard-back"]')
    expect(back.text()).toContain('需求分析回答系统必须完成什么')
    expect(back.text()).toContain('区分用户目标与系统边界')

    await wrapper.get('[data-testid="flashcard-rate-3"]').trigger('click')
    await flushPromises()

    expect(mocks.reviewFlashcard).toHaveBeenCalledWith(10, 3)
    expect(wrapper.get('[data-testid="flashcards-notice"]').text()).toContain('7 天后')
    // 自动切到下一张卡片并回到正面
    expect(wrapper.get('[data-testid="flashcard-front"]').text()).toContain('数据流图 DFD')

    wrapper.unmount()
  })

  it('switches to due-only mode and handles empty queue gracefully', async () => {
    mocks.flashcards.mockImplementation(async ({ dueOnly }: { dueOnly?: boolean }) =>
      dueOnly ? [] : structuredClone(sampleCards),
    )

    const { wrapper } = await mountFlashcards()
    await wrapper.get('[data-testid="flashcards-tab-due"]').trigger('click')
    await flushPromises()

    expect(mocks.flashcards).toHaveBeenLastCalledWith({ dueOnly: true })
    expect(wrapper.text()).toContain('当前没有待回忆的概念闪卡')
    wrapper.unmount()
  })
})
