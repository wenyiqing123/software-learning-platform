import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import NotesAndBookmarksView from './NotesAndBookmarksView.vue'

const apiMock = vi.hoisted(() => ({
  notes: vi.fn(),
  updateNote: vi.fn(),
  deleteNote: vi.fn(),
  bookmarksList: vi.fn(),
  setBookmark: vi.fn(),
}))

vi.mock('../api/notes', () => apiMock)
vi.mock('../api/client', () => ({
  apiError: (_cause: unknown, fallback: string) => fallback,
}))

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/notes', component: NotesAndBookmarksView },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/practice', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
    ],
  })
  await router.push('/notes')
  await router.isReady()
  return { wrapper: mount(NotesAndBookmarksView, { global: { plugins: [router] } }), router }
}

beforeEach(() => {
  vi.resetAllMocks()
  apiMock.notes.mockResolvedValue([
    {
      id: 11,
      sectionId: 5,
      knowledgePointId: 2,
      title: '数据流图分解原则',
      content: '父图与子图的输入输出数据流必须守恒。',
      tags: ['数据流图', '易错点'],
      updatedAt: '2026-11-26T10:30:00',
    },
    {
      id: 12,
      sectionId: 6,
      title: '需求规格说明书',
      content: '应保持无歧义性与可验证性。',
      tags: ['需求分析'],
      updatedAt: '2026-11-26T09:15:00',
    },
  ])
  apiMock.bookmarksList.mockResolvedValue([
    {
      targetType: 'SECTION',
      targetId: 5,
      title: '2.1 数据流图建模',
      courseId: 1,
      courseName: '软件工程',
      knowledgePointId: 2,
      knowledgePointName: '数据流图',
      graphEntity: '数据流图DFD',
    },
  ])
  apiMock.updateNote.mockResolvedValue({})
  apiMock.deleteNote.mockResolvedValue(undefined)
  apiMock.setBookmark.mockResolvedValue({ targetType: 'SECTION', targetId: 5, bookmarked: false })
})

describe('NotesAndBookmarksView', () => {
  it('filters notes by keyword and tag, supports inline edit and confirm delete, and manages bookmarks', async () => {
    const { wrapper } = await mountPage()
    await flushPromises()

    expect(wrapper.findAll('[data-testid="notes-hub-card"]')).toHaveLength(2)

    // 按标签筛选
    const tagBtn = wrapper.findAll('.notes-tag-pill').find((btn) => btn.text().includes('#易错点'))
    expect(tagBtn).toBeDefined()
    await tagBtn!.trigger('click')
    expect(wrapper.findAll('[data-testid="notes-hub-card"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('数据流图分解原则')

    // 取消标签筛选后再按关键词搜索
    await tagBtn!.trigger('click')
    await wrapper.get('.notes-hub-search input').setValue('可验证性')
    expect(wrapper.findAll('[data-testid="notes-hub-card"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('需求规格说明书')

    // 清空搜索并编辑笔记
    await wrapper.get('.notes-hub-search input').setValue('')
    await wrapper.get('button[aria-label="编辑笔记 需求规格说明书"]').trigger('click')
    await wrapper.get('textarea[aria-label="编辑笔记正文"]').setValue('更新后的 SRS 规范要求')
    await wrapper.get('.notes-hub-edit-form').trigger('submit')
    await flushPromises()
    expect(apiMock.updateNote).toHaveBeenCalledWith(12, expect.objectContaining({
      content: '更新后的 SRS 规范要求',
    }))

    // 删除笔记弹出 ConfirmDialog 并确认删除
    await wrapper.get('button[aria-label="删除笔记 数据流图分解原则"]').trigger('click')
    const confirmBtn = document.body.querySelector('[data-testid="confirm-dialog-confirm"]') as HTMLButtonElement | null
    expect(confirmBtn).not.toBeNull()
    confirmBtn!.click()
    await flushPromises()
    expect(apiMock.deleteNote).toHaveBeenCalledWith(11)

    // 切换到收藏页签并取消收藏
    await wrapper.get('[data-testid="notes-hub-tab-bookmarks"]').trigger('click')
    expect(wrapper.findAll('[data-testid="bookmark-hub-card"]')).toHaveLength(1)
    await wrapper.get('button[aria-label="取消收藏 2.1 数据流图建模"]').trigger('click')
    await flushPromises()
    expect(apiMock.setBookmark).toHaveBeenCalledWith({ sectionId: 5, bookmarked: false })

    // 导出讲义与打印讲义按钮
    const createObjectURL = vi.fn(() => 'blob:mock-notes')
    const revokeObjectURL = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: createObjectURL })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: revokeObjectURL })
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {})

    await wrapper.get('[data-testid="notes-export-md"]').trigger('click')
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-notes')
    expect(wrapper.text()).toContain('已导出 Markdown 讲义')

    await wrapper.get('[data-testid="notes-print"]').trigger('click')
    expect(printSpy).toHaveBeenCalledTimes(1)

    vi.restoreAllMocks()
    wrapper.unmount()
  })
})
