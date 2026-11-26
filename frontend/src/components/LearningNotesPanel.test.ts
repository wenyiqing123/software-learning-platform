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
    expect(wrapper.emitted('bookmark-change')?.some((payload) => payload[0] === true)).toBe(true)
    expect(wrapper.emitted('notes-count-change')?.some((payload) => payload[0] === 0)).toBe(true)

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
      if (path === '/learning/notes') return { data: [{ id: 7, sectionId: 101, title: '验收条件', content: '记录验收条件', tags: ['需求', '验收'] }] }
      if (path === '/learning/bookmarks') return { data: { targetType: 'SECTION', targetId: 101, bookmarked: true } }
      throw new Error('unexpected path: ' + path)
    })
    http.delete.mockResolvedValue({ data: {} })
    const confirmSpy = vi.spyOn(window, 'confirm')
    const wrapper2 = mount(LearningNotesPanel, { props: { sectionId: 101, knowledgePointId: 2 } })
    await flushPromises()
    await wrapper2.get('[aria-label="编辑笔记 7"]').trigger('click')
    await wrapper2.get('form.notes-editor').trigger('submit')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/learning/notes/7', {
      sectionId: 101, title: '验收条件', content: '记录验收条件', tags: ['需求', '验收'],
    })

    await wrapper2.get('[aria-label="删除笔记 7"]').trigger('click')
    await flushPromises()
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(http.delete).not.toHaveBeenCalled()
    ;(document.body.querySelector('[data-testid="confirm-dialog-confirm"]') as HTMLButtonElement | null)?.click()
    await flushPromises()
    expect(http.delete).toHaveBeenCalledWith('/learning/notes/7')
    wrapper2.unmount()
  })

  it('opens teleported note dialog via openComposer and respects Escape layering with ConfirmDialog', async () => {
    http.get.mockImplementation(async (path: string) => {
      if (path === '/learning/notes') return { data: [{ id: 7, sectionId: 101, title: '验收条件', content: '记录验收条件', tags: ['需求'] }] }
      if (path === '/learning/bookmarks') return { data: { targetType: 'SECTION', targetId: 101, bookmarked: false } }
      throw new Error('unexpected path: ' + path)
    })

    const wrapper = mount(LearningNotesPanel, {
      props: { sectionId: 101, knowledgePointId: 2, sectionTitle: '用例建模基础', startCollapsed: true },
    })
    await flushPromises()
    expect(document.body.querySelector('[data-testid="note-composer-dialog"]')).toBeNull()

    await (wrapper.vm as unknown as { openComposer: () => Promise<void> }).openComposer()
    await flushPromises()

    const dialog = document.body.querySelector('[data-testid="note-composer-dialog"]')
    expect(dialog).not.toBeNull()
    expect(dialog!.textContent).toContain('课程笔记 · 用例建模基础')
    expect(document.body.style.overflow).toBe('hidden')

    ;(dialog!.querySelector('[aria-label="删除笔记 7"]') as HTMLButtonElement).click()
    await flushPromises()
    expect(document.body.querySelector('[data-testid="confirm-dialog"]')).not.toBeNull()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await flushPromises()
    expect(document.body.querySelector('[data-testid="confirm-dialog"]')).toBeNull()
    expect(document.body.querySelector('[data-testid="note-composer-dialog"]')).not.toBeNull()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await flushPromises()
    expect(document.body.querySelector('[data-testid="note-composer-dialog"]')).toBeNull()
    expect(document.body.style.overflow).toBe('')
    wrapper.unmount()
  })

  it('adds and toggles quick system tags inside the note composer dialog', async () => {
    const wrapper = mount(LearningNotesPanel, {
      props: {
        sectionId: 101,
        knowledgePointId: 2,
        knowledgePointName: '用例图',
        sectionTitle: '用例建模基础',
        startCollapsed: true,
      },
    })
    await flushPromises()

    await (wrapper.vm as unknown as { openComposer: () => Promise<void> }).openComposer()
    await flushPromises()

    const dialog = document.body.querySelector('[data-testid="note-composer-dialog"]')
    expect(dialog).not.toBeNull()

    const kpChip = dialog!.querySelector('[data-testid="note-preset-tag-用例图"]') as HTMLButtonElement
    const coreChip = dialog!.querySelector('[data-testid="note-preset-tag-核心概念"]') as HTMLButtonElement
    const pitfallChip = dialog!.querySelector('[data-testid="note-preset-tag-易错点"]') as HTMLButtonElement
    const tagInput = dialog!.querySelector('[aria-label="笔记标签"]') as HTMLInputElement
    const contentInput = dialog!.querySelector('[aria-label="笔记内容"]') as HTMLTextAreaElement

    expect(kpChip).not.toBeNull()
    expect(coreChip).not.toBeNull()
    expect(pitfallChip).not.toBeNull()

    kpChip.click()
    coreChip.click()
    pitfallChip.click()
    await flushPromises()
    expect(tagInput.value).toBe('用例图, 核心概念, 易错点')
    expect(coreChip.getAttribute('aria-pressed')).toBe('true')

    coreChip.click()
    await flushPromises()
    expect(tagInput.value).toBe('用例图, 易错点')
    expect(coreChip.getAttribute('aria-pressed')).toBe('false')

    contentInput.value = '快速添加系统标签测试'
    contentInput.dispatchEvent(new Event('input'))
    await flushPromises()
    ;(dialog!.querySelector('form.notes-editor') as HTMLFormElement).dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/learning/notes', {
      sectionId: 101,
      title: '',
      content: '快速添加系统标签测试',
      tags: ['用例图', '易错点'],
    })
    wrapper.unmount()
  })
})
