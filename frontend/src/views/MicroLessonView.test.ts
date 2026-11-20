import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import MicroLessonView from './MicroLessonView.vue'

const http = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api/client', () => ({ api: http, apiError: vi.fn(() => '微课暂时无法加载') }))

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockImplementation(async (path: string) => {
    if (path === '/courses') return { data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] }
    if (path === '/courses/1/study') return { data: { chapters: [{ id: 2, name: '需求分析', sections: [{ id: 21, title: '理解数据流图' }] }] } }
    if (path === '/courses/1/study/sections/21/micro-lesson') return { data: { id: 11, title: '理解数据流图', status: 'PUBLISHED', generatedText: '加工表示数据变换。', objectives: ['识别加工'], triples: [{ subject: '加工', predicate: '命名', object: '动宾短语', score: 0.9 }], commonErrors: [], selfTests: [], prerequisites: [], evidence: { graphEntity: '数据流图DFD', tripleCount: 1 } } }
    throw new Error('unexpected path: ' + path)
  })
})

describe('MicroLessonView', () => {
  it('shows only the published evidence and learning structure', async () => {
    const wrapper = mount(MicroLessonView)
    await flushPromises()
    expect(wrapper.text()).toContain('加工表示数据变换')
    expect(wrapper.text()).toContain('加工 — 命名 — 动宾短语')
    expect(wrapper.text()).toContain('来源图谱实体：数据流图DFD')
    wrapper.unmount()
  })
})
