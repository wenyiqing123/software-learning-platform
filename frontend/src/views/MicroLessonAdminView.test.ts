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
    if (path === '/courses/1/study') return { data: { chapters: [{ id: 2, name: '需求分析', sections: [{ id: 21, title: '理解数据流图', knowledgePointName: '数据流图 DFD', objectives: ['识别加工'] }] }] } }
    if (path === '/admin/micro-lessons') return { data: generated ? [{ id: 11, courseId: 1, sectionId: 21, title: '理解数据流图', status: 'DRAFT', generatedText: '只根据证据生成的摘要。', evidence: { tripleCount: 1 } }] : [] }
    throw new Error('unexpected path: ' + path)
  })
  http.post.mockImplementation(async () => { generated = true; return { data: { id: 11, courseId: 1, sectionId: 21, title: '理解数据流图', status: 'DRAFT', generatedText: '只根据证据生成的摘要。', evidence: { tripleCount: 1 } } } })
  http.put.mockResolvedValue({ data: { id: 11, status: 'PUBLISHED', generatedText: '只根据证据生成的摘要。', evidence: { tripleCount: 1 } } })
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
    await wrapper.get('button[aria-label="发布微课 11"]').trigger('click')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/micro-lessons/11/publish')
    expect(wrapper.text()).toContain('已发布')
    wrapper.unmount()
  })
})
