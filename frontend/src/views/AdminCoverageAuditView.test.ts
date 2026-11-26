import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminCoverageAuditView from './AdminCoverageAuditView.vue'

const mocks = vi.hoisted(() => ({
  adminCoverageAudit: vi.fn(),
  apiGet: vi.fn(),
}))

vi.mock('../api/admin', () => ({
  adminCoverageAudit: mocks.adminCoverageAudit,
}))

vi.mock('../api/http', () => ({
  api: { get: mocks.apiGet },
  apiError: (_cause: unknown, fallback: string) => fallback,
}))

async function mountAuditView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/content', component: AdminCoverageAuditView }],
  })
  await router.push('/admin/content?tab=coverage')
  await router.isReady()
  const wrapper = mount(AdminCoverageAuditView, { global: { plugins: [router] }, props: { embedded: true } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  mocks.apiGet.mockResolvedValue({
    data: [{ id: 3, code: 'SE', name: '软件工程', term: '2026 秋季' }],
  })
  mocks.adminCoverageAudit.mockResolvedValue({
    courseId: null,
    courseName: '全部课程',
    ruleDescription: '确定性规则审计说明',
    summary: {
      totalPoints: 2,
      fullyCoveredPoints: 1,
      missingQuestionsCount: 1,
      missingDiagnosisCount: 1,
      missingMicroLessonCount: 1,
      missingFlashcardCount: 0,
      coveredQuestionsTotal: 4,
      coveredDiagnosisTotal: 2,
      publishedMicroLessonsTotal: 1,
      flashcardReviewRecordsTotal: 3,
    },
    items: [
      {
        knowledgePointId: 11,
        courseId: 3,
        courseName: '软件工程',
        chapterId: 1,
        chapterName: '软件需求',
        knowledgePointName: '需求分析',
        graphEntity: '需求分析',
        enabled: true,
        sectionCount: 1,
        hasDescription: true,
        questionCount: 4,
        diagnosisCount: 2,
        publishedMicroLessonCount: 1,
        draftMicroLessonCount: 0,
        flashcardReviewCount: 3,
        flashcardReady: true,
        gaps: [],
      },
      {
        knowledgePointId: 12,
        courseId: 3,
        courseName: '软件工程',
        chapterId: 1,
        chapterName: '软件需求',
        knowledgePointName: '数据存储',
        graphEntity: '数据存储',
        enabled: true,
        sectionCount: 1,
        hasDescription: true,
        questionCount: 0,
        diagnosisCount: 0,
        publishedMicroLessonCount: 0,
        draftMicroLessonCount: 1,
        flashcardReviewCount: 0,
        flashcardReady: true,
        gaps: [
          { code: 'NO_QUESTIONS', label: '无练习题', actionLabel: '补录题库', targetTab: 'question-bank' },
          { code: 'NO_DIAGNOSIS', label: '缺错因诊断', actionLabel: '配置错因', targetTab: 'diagnosis' },
          { code: 'NO_MICRO_LESSON', label: '无已发布微课', actionLabel: '管理微课', targetTab: 'micro-lessons' },
        ],
      },
    ],
  })
})

describe('AdminCoverageAuditView', () => {
  it('renders deterministic asset counters, filters gaps, and jumps directly to fix tab', async () => {
    const { wrapper, router } = await mountAuditView()

    expect(wrapper.get('[data-testid="coverage-summary-cards"]').text()).toContain('2')
    expect(wrapper.get('[data-testid="coverage-summary-cards"]').text()).toContain('4 / 2')
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)

    await wrapper.get('[data-testid="coverage-filter-NO_DIAGNOSIS"]').trigger('click')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.get('tbody').text()).toContain('数据存储')

    await wrapper.get('[data-testid="coverage-fix-12-NO_DIAGNOSIS"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content')
    expect(router.currentRoute.value.query).toEqual({ tab: 'diagnosis', courseId: '3' })
    wrapper.unmount()
  })

  it('shows empty state when selected course has no knowledge points', async () => {
    mocks.adminCoverageAudit.mockResolvedValueOnce({
      courseId: 3,
      courseName: '软件工程',
      ruleDescription: '确定性规则审计说明',
      summary: {
        totalPoints: 0,
        fullyCoveredPoints: 0,
        missingQuestionsCount: 0,
        missingDiagnosisCount: 0,
        missingMicroLessonCount: 0,
        missingFlashcardCount: 0,
        coveredQuestionsTotal: 0,
        coveredDiagnosisTotal: 0,
        publishedMicroLessonsTotal: 0,
        flashcardReviewRecordsTotal: 0,
      },
      items: [],
    })

    const { wrapper } = await mountAuditView()
    expect(wrapper.find('[data-testid="coverage-audit-table"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="coverage-empty-state"]').text()).toContain('当前课程暂无可统计知识点')
    wrapper.unmount()
  })
})
