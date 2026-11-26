import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import MicroLessonAdminView from './MicroLessonAdminView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../api/client', () => ({ api: http, apiError: vi.fn(() => '微课请求失败') }))
let generated = false

beforeEach(() => {
  vi.resetAllMocks(); vi.spyOn(window, 'confirm').mockReturnValue(true); generated = false
  http.get.mockImplementation(async (path: string) => {
    if (path === '/admin/courses') return { data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] }
    if (path === '/admin/micro-lessons/courses/1/sections') return { data: [{ id: 21, chapterName: '需求分析', title: '理解数据流图', knowledgePointName: '数据流图 DFD' }] }
    if (path === '/admin/micro-lessons') return { data: generated ? [{ id: 11, courseId: 1, sectionId: 21, title: '理解数据流图', status: 'DRAFT', generatedText: '只根据证据生成的摘要。', evidence: { tripleCount: 1 } }] : [] }
    throw new Error('unexpected path: ' + path)
  })
  http.post.mockImplementation(async () => { generated = true; return { data: { id: 11, courseId: 1, sectionId: 21, title: '理解数据流图', status: 'DRAFT', generatedText: '只根据证据生成的摘要。', evidence: { tripleCount: 1 } } } })
  http.put.mockImplementation(async (path: string, body?: any) => {
    if (path === '/admin/micro-lessons/11') {
      return { data: { id: 11, courseId: 1, sectionId: 21, title: body?.title || '理解数据流图', status: 'DRAFT', generatedText: body?.generatedText || '新正文', evidence: { tripleCount: 1 } } }
    }
    return { data: { id: 11, status: 'PUBLISHED', generatedText: '只根据证据生成的摘要。', evidence: { tripleCount: 1 } } }
  })
})

afterEach(() => vi.restoreAllMocks())

describe('MicroLessonAdminView', () => {
  it('renders H1 by default and H2 when embedded', async () => {
    const standalone = mount(MicroLessonAdminView)
    await flushPromises()
    expect(standalone.find('h1').text()).toBe('证据微课草稿')
    expect(standalone.find('h2').text()).not.toBe('证据微课草稿')
    standalone.unmount()

    const embedded = mount(MicroLessonAdminView, { props: { embedded: true } })
    await flushPromises()
    expect(embedded.find('h2').text()).toBe('证据微课草稿')
    embedded.unmount()
  })

  it('generates an evidence-bound draft and publishes it explicitly', async () => {
    const wrapper = mount(MicroLessonAdminView)
    await flushPromises()
    await wrapper.get('button[aria-label="生成证据微课草稿"]').trigger('click')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/admin/micro-lessons/generate', { courseId: 1, sectionId: 21 })
    expect(wrapper.text()).toContain('只根据证据生成的摘要')

    await wrapper.get('button[aria-label="编辑微课 11"]').trigger('click')
    await wrapper.get('input[aria-label="编辑微课标题 11"]').setValue('数据流图精选微课')
    await wrapper.get('textarea[aria-label="编辑微课正文 11"]').setValue('修订后的 Markdown 正文')
    await wrapper.get('form.draft-editor').trigger('submit')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/micro-lessons/11', { title: '数据流图精选微课', generatedText: '修订后的 Markdown 正文' })

    await wrapper.get('button[aria-label="发布微课 11"]').trigger('click')
    await flushPromises()
    ;(document.body.querySelector('[data-testid="confirm-dialog-confirm"]') as HTMLButtonElement | null)?.click()
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/micro-lessons/11/publish')
    expect(wrapper.text()).toContain('已发布')
    wrapper.unmount()
  })
})
