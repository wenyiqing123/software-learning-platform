import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import CourseView from './CourseView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), delete: vi.fn() }))
vi.mock('../api/client', () => ({
  api: http,
  apiError: vi.fn(() => '接口错误'),
}))

const mockStudyData = {
  courseId: 1,
  name: '软件工程导论',
  code: 'SE101',
  term: '2026秋',
  description: '软件工程核心基础',
  totalSections: 2,
  completedSections: 1,
  chapters: [
    {
      id: 10,
      name: '需求工程',
      description: '需求获取与分析',
      sections: [
        {
          id: 101,
          knowledgePointId: 11,
          knowledgePointName: '需求分析',
          graphEntity: '需求分析',
          title: '用例建模基础',
          objectives: ['理解用例图'],
          content: [{ heading: '引言', paragraphs: ['用例图用于表达系统功能需求。'] }],
          resources: [],
          completed: true,
        },
        {
          id: 102,
          knowledgePointId: 12,
          knowledgePointName: '需求规格',
          graphEntity: '需求规格说明',
          title: 'SRS 规范',
          objectives: ['编写 SRS 文档'],
          content: [{ heading: '规范要点', paragraphs: ['需求规格说明书必须具备无歧义性。'] }],
          resources: [],
          completed: false,
        },
      ],
    },
  ],
}

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockImplementation(async (path: string) => {
    if (path === '/courses') return { data: [{ id: 1, name: '软件工程导论', term: '2026秋', description: '软件工程核心基础' }] }
    if (path === '/courses/1/study') return { data: mockStudyData }
    if (path === '/learning/notes') return { data: [] }
    if (path === '/learning/bookmarks') return { data: { bookmarked: false } }
    throw new Error('unexpected path: ' + path)
  })
})

async function mountCourseView(query = { courseId: '1', sectionId: '101' }) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/courses', component: CourseView },
      { path: '/practice', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/tutor', component: { template: '<div />' } },
    ],
  })
  await router.push({ path: '/courses', query })
  await router.isReady()
  const wrapper = mount(CourseView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('CourseView', () => {
  it('renders outline and reading flow with correct 72ch width class and collapsed notes', async () => {
    const { wrapper, router } = await mountCourseView()

    expect(wrapper.text()).toContain('软件工程导论')
    expect(wrapper.text()).toContain('用例建模基础')

    const readingArticle = wrapper.find('article.study-reading')
    expect(readingArticle.exists()).toBe(true)
    expect(readingArticle.classes()).toContain('reading-flow')

    const notesPanel = wrapper.find('.learning-notes-panel')
    expect(notesPanel.exists()).toBe(true)
    // 笔记面板初始折叠
    expect(notesPanel.find('.notes-editor').exists()).toBe(false)

    // 点击切换到第二个小节 102
    const outlineButtons = wrapper.findAll('.outline-item')
    expect(outlineButtons).toHaveLength(2)
    await outlineButtons[1].trigger('click')
    await flushPromises()

    expect(String(router.currentRoute.value.query.sectionId)).toBe('102')
    expect(router.currentRoute.value.hash).toBe('#section-102')

    wrapper.unmount()
  })
})
