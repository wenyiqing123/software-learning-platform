import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import LearningNotesPanel from './LearningNotesPanel.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock('../api/client', () => ({ api: http, apiError: vi.fn(() => '请求失败') }))

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockImplementation(async (path: string) => {
    if (path === '/learning/notes') return { data: [] }
    if (path === '/learning/bookmarks') return { data: { targetType: 'SECTION', targetId: 101, bookmarked: false } }
    throw new Error('unexpected path: ' + path)
  })
  http.post.mockResolvedValue({ data: { id: 7, sectionId: 101, title: '验收条件', content: '记录验收条件', tags: ['需求'] } })
  http.put.mockResolvedValue({ data: { targetType: 'SECTION', targetId: 101, bookmarked: true } })
})

describe('LearningNotesPanel', () => {
  it('loads private notes, toggles bookmark and saves a note', async () => {
    const wrapper = mount(LearningNotesPanel, { props: { sectionId: 101, knowledgePointId: 2 } })
    await flushPromises()
    await wrapper.get('[aria-label="收藏本节"]').trigger('click')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/learning/bookmarks', { sectionId: 101, bookmarked: true })

    await wrapper.get('[aria-label="笔记标题"]').setValue('验收条件')
    await wrapper.get('[aria-label="笔记内容"]').setValue('记录验收条件')
    await wrapper.get('[aria-label="笔记标签"]').setValue('需求,验收')
    await wrapper.get('form.notes-editor').trigger('submit')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/learning/notes', {
      sectionId: 101, title: '验收条件', content: '记录验收条件', tags: ['需求', '验收'],
    })
    expect(wrapper.text()).toContain('笔记已保存')
    wrapper.unmount()

    http.get.mockImplementation(async (path: string) => {
      if (path === '/learning/notes') return { data: [{ id: 7, sectionId: 101, title: '验收条件', content: '记录验收条件', tags: ['需求'] }] }
      if (path === '/learning/bookmarks') return { data: { targetType: 'SECTION', targetId: 101, bookmarked: true } }
      throw new Error('unexpected path: ' + path)
    })
    http.delete.mockResolvedValue({ data: {} })
    const confirmSpy = vi.spyOn(window, 'confirm')
    const wrapper2 = mount(LearningNotesPanel, { props: { sectionId: 101, knowledgePointId: 2 } })
    await flushPromises()
    await wrapper2.get('[aria-label="删除笔记 7"]').trigger('click')
    await flushPromises()
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(http.delete).not.toHaveBeenCalled()
    ;(document.body.querySelector('[data-testid="confirm-dialog-confirm"]') as HTMLButtonElement | null)?.click()
    await flushPromises()
    expect(http.delete).toHaveBeenCalledWith('/learning/notes/7')
    wrapper2.unmount()
  })
})
