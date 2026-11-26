import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
    if (path === '/courses/1/study') return { data: structuredClone(mockStudyData) }
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

  it('automatically marks an unread section read when scrolling to the bottom of window', async () => {
    http.put.mockResolvedValue({ data: {} })
    const { wrapper } = await mountCourseView({ courseId: '1', sectionId: '102' })

    const setScrollState = (scrollY: number, scrollHeight = 1800, innerHeight = 900) => {
      Object.defineProperty(window, 'innerHeight', { value: innerHeight, configurable: true })
      Object.defineProperty(window, 'scrollY', { value: scrollY, configurable: true })
      Object.defineProperty(document.documentElement, 'clientHeight', { value: innerHeight, configurable: true })
      Object.defineProperty(document.documentElement, 'scrollHeight', { value: scrollHeight, configurable: true })
    }
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
      cb(0)
      return 1
    })

    try {
      setScrollState(300)
      window.dispatchEvent(new Event('scroll'))
      await flushPromises()
      expect(http.put).not.toHaveBeenCalled()

      setScrollState(900)
      window.dispatchEvent(new Event('scroll'))
      await flushPromises()
      expect(http.put).toHaveBeenCalledTimes(1)
      expect(http.put).toHaveBeenCalledWith('/courses/1/study/sections/102/progress', { completed: true })
      expect(wrapper.find('.auto-read-status').text()).toContain('已滚动至页面底部')
      expect(wrapper.text()).toContain('2 / 2')

      // 停留在底部再次触发 scroll 不会重复提交
      window.dispatchEvent(new Event('scroll'))
      await flushPromises()
      expect(http.put).toHaveBeenCalledTimes(1)
    } finally {
      vi.unstubAllGlobals()
      wrapper.unmount()
    }
  })

  it('prevents immediate re-mark after manual undo near bottom until scrolling back up', async () => {
    http.put.mockResolvedValue({ data: {} })
    const { wrapper } = await mountCourseView({ courseId: '1', sectionId: '102' })

    const setScrollState = (scrollY: number, scrollHeight = 1800, innerHeight = 900) => {
      Object.defineProperty(window, 'innerHeight', { value: innerHeight, configurable: true })
      Object.defineProperty(window, 'scrollY', { value: scrollY, configurable: true })
      Object.defineProperty(document.documentElement, 'clientHeight', { value: innerHeight, configurable: true })
      Object.defineProperty(document.documentElement, 'scrollHeight', { value: scrollHeight, configurable: true })
    }
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
      cb(0)
      return 1
    })

    try {
      setScrollState(900)
      window.dispatchEvent(new Event('scroll'))
      await flushPromises()
      expect(http.put).toHaveBeenCalledTimes(1)

      // 用户在底部手动撤销已读
      await wrapper.find('.study-footer button.button-outline').trigger('click')
      await flushPromises()
      expect(http.put).toHaveBeenNthCalledWith(2, '/courses/1/study/sections/102/progress', { completed: false })

      // 停留在底部轻微抖动不自动重新标为已读
      setScrollState(895)
      window.dispatchEvent(new Event('scroll'))
      await flushPromises()
      expect(http.put).toHaveBeenCalledTimes(2)

      // 向上滚离底部后再重新滚回底部，允许再次自动标为已读
      setScrollState(200)
      window.dispatchEvent(new Event('scroll'))
      await flushPromises()
      setScrollState(900)
      window.dispatchEvent(new Event('scroll'))
      await flushPromises()
      expect(http.put).toHaveBeenNthCalledWith(3, '/courses/1/study/sections/102/progress', { completed: true })
    } finally {
      vi.unstubAllGlobals()
      wrapper.unmount()
    }
  })

  it('shows sticky scroll progress, section TOC navigation, and selection floating toolbar actions', async () => {
    const { wrapper, router } = await mountCourseView({ courseId: '1', sectionId: '101' })

    const bar = wrapper.get('[data-testid="reading-progress-bar"]')
    expect(bar.text()).toContain('本节阅读')

    const toc = wrapper.get('[data-testid="section-toc"]')
    expect(toc.text()).toContain('学习目标')
    expect(toc.text()).toContain('引言')
    await toc.findAll('.section-toc-item')[1].trigger('click')
    expect(toc.findAll('.section-toc-item')[1].classes()).toContain('active')

    // 模拟在阅读区划词选中
    const article = wrapper.get('article.study-reading').element
    const textNode = article.querySelector('.reading-block')!.firstChild || article
    const range = {
      getBoundingClientRect: () => ({ top: 200, left: 120, width: 100, height: 20 }),
    }
    vi.spyOn(window, 'getSelection').mockReturnValue({
      isCollapsed: false,
      toString: () => '表达系统功能需求',
      anchorNode: textNode,
      focusNode: textNode,
      rangeCount: 1,
      getRangeAt: () => range as any,
      removeAllRanges: vi.fn(),
    } as unknown as Selection)

    document.dispatchEvent(new Event('selectionchange'))
    await flushPromises()

    const toolbar = document.body.querySelector('[data-testid="course-selection-toolbar"]')
    expect(toolbar).not.toBeNull()

    // 摘录到笔记草稿（不刷新页面，直接展开右侧草稿）
    ;(document.body.querySelector('[data-testid="selection-to-note"]') as HTMLButtonElement).click()
    await flushPromises()
    const noteTextarea = wrapper.get('[aria-label="笔记内容"]').element as HTMLTextAreaElement
    expect(noteTextarea.value).toContain('> 表达系统功能需求')

    // 再次选中并跳转到 AI 助教（携带 prompt 查询参数，不自动提交）
    document.dispatchEvent(new Event('selectionchange'))
    await flushPromises()
    ;(document.body.querySelector('[data-testid="selection-to-tutor"]') as HTMLButtonElement).click()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/tutor')
    expect(String(router.currentRoute.value.query.prompt)).toContain('表达系统功能需求')

    vi.restoreAllMocks()
    wrapper.unmount()
  })
})

afterEach(() => {
  Object.defineProperty(window, 'innerHeight', { value: 768, configurable: true })
  Object.defineProperty(window, 'scrollY', { value: 0, configurable: true })
  Object.defineProperty(document.documentElement, 'clientHeight', { value: 768, configurable: true })
  Object.defineProperty(document.documentElement, 'scrollHeight', { value: 0, configurable: true })
})
