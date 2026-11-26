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

    // 摘录到笔记草稿（直接弹出记笔记弹窗并带入引用文本）
    ;(document.body.querySelector('[data-testid="selection-to-note"]') as HTMLButtonElement).click()
    await flushPromises()
    const noteDialog = document.body.querySelector('[data-testid="note-composer-dialog"]')
    expect(noteDialog).not.toBeNull()
    const noteTextarea = noteDialog!.querySelector('[aria-label="笔记内容"]') as HTMLTextAreaElement
    expect(noteTextarea.value).toContain('> 表达系统功能需求')
    ;(document.body.querySelector('[data-testid="note-composer-close"]') as HTMLButtonElement).click()
    await flushPromises()
    expect(document.body.querySelector('[data-testid="note-composer-dialog"]')).toBeNull()

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

  it('exposes prominent top and footer section bookmark buttons synced with learning notes panel', async () => {
    http.put.mockImplementation(async (path: string, body: { bookmarked?: boolean }) => {
      if (path === '/learning/bookmarks') return { data: { targetType: 'SECTION', targetId: 101, bookmarked: Boolean(body.bookmarked) } }
      return { data: {} }
    })

    const { wrapper } = await mountCourseView({ courseId: '1', sectionId: '101' })
    const topBtn = wrapper.get('[data-testid="course-section-bookmark"]')
    const footerBtn = wrapper.get('[data-testid="course-footer-bookmark"]')
    expect(topBtn.text()).toContain('收藏本节')
    expect(footerBtn.text()).toContain('收藏本节')

    await topBtn.trigger('click')
    await flushPromises()

    expect(http.put).toHaveBeenCalledWith('/learning/bookmarks', { sectionId: 101, bookmarked: true })
    expect(topBtn.text()).toContain('已收藏')
    expect(footerBtn.text()).toContain('已收藏本节')
    expect(wrapper.get('[data-testid="course-bookmark-notice"]').text()).toContain('已收藏本节')
    expect(wrapper.get('.notes-bookmark').classes()).toContain('active')

    await footerBtn.trigger('click')
    await flushPromises()

    expect(http.put).toHaveBeenLastCalledWith('/learning/bookmarks', { sectionId: 101, bookmarked: false })
    expect(topBtn.text()).toContain('收藏本节')
    expect(wrapper.get('.notes-bookmark').classes()).not.toContain('active')
    wrapper.unmount()
  })

  it('exposes top and footer note buttons that reflect count and open the teleported note dialog', async () => {
    const emptyMount = await mountCourseView({ courseId: '1', sectionId: '101' })
    expect(document.body.querySelector('[data-testid="note-composer-dialog"]')).toBeNull()
    expect(emptyMount.wrapper.get('[data-testid="course-section-note"]').text()).toContain('记笔记')
    expect(emptyMount.wrapper.get('[data-testid="course-footer-note"]').text()).toContain('记一条笔记')

    await emptyMount.wrapper.get('[data-testid="course-footer-note"]').trigger('click')
    await flushPromises()
    const footerDialog = document.body.querySelector('[data-testid="note-composer-dialog"]')
    expect(footerDialog).not.toBeNull()
    expect(footerDialog!.textContent).toContain('用例建模基础')
    expect(footerDialog!.querySelector('[aria-label="笔记内容"]')).not.toBeNull()
    ;(document.body.querySelector('[data-testid="note-composer-close"]') as HTMLButtonElement).click()
    await flushPromises()
    emptyMount.wrapper.unmount()

    http.get.mockImplementation(async (path: string) => {
      if (path === '/courses') return { data: [{ id: 1, name: '软件工程导论', term: '2026秋', description: '软件工程核心基础' }] }
      if (path === '/courses/1/study') return { data: structuredClone(mockStudyData) }
      if (path === '/learning/notes') {
        return {
          data: [{ id: 7, sectionId: 101, knowledgePointId: 11, title: '边界提醒', content: '先画系统边界', updatedAt: '2026-11-26 19:00' }],
        }
      }
      if (path === '/learning/bookmarks') return { data: { bookmarked: false } }
      throw new Error('unexpected path: ' + path)
    })

    const { wrapper } = await mountCourseView({ courseId: '1', sectionId: '101' })
    expect(document.body.querySelector('[data-testid="note-composer-dialog"]')).toBeNull()
    expect(wrapper.get('[data-testid="course-section-note"]').text()).toContain('记笔记 (1)')
    expect(wrapper.get('[data-testid="course-footer-note"]').text()).toContain('记笔记 (1)')

    await wrapper.get('[data-testid="course-section-note"]').trigger('click')
    await flushPromises()
    const topDialog = document.body.querySelector('[data-testid="note-composer-dialog"]')
    expect(topDialog).not.toBeNull()
    expect(topDialog!.textContent).toContain('先画系统边界')
    expect(topDialog!.querySelector('[aria-label="笔记内容"]')).not.toBeNull()
    expect(topDialog!.querySelector('[data-testid="note-preset-tag-需求分析"]')).not.toBeNull()
    ;(topDialog!.querySelector('[data-testid="note-preset-tag-易错点"]') as HTMLButtonElement).click()
    await flushPromises()
    expect((topDialog!.querySelector('[aria-label="笔记标签"]') as HTMLInputElement).value).toBe('易错点')
    wrapper.unmount()
  })
})

afterEach(() => {
  Object.defineProperty(window, 'innerHeight', { value: 768, configurable: true })
  Object.defineProperty(window, 'scrollY', { value: 0, configurable: true })
  Object.defineProperty(document.documentElement, 'clientHeight', { value: 768, configurable: true })
  Object.defineProperty(document.documentElement, 'scrollHeight', { value: 0, configurable: true })
})
