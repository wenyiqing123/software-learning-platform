import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import QuestionBankView from './QuestionBankView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../api/client', () => ({ api: http, apiError: vi.fn(() => '题库请求失败') }))
let imported = false

beforeEach(() => {
  vi.resetAllMocks()
  imported = false
  http.get.mockImplementation(async (path: string) => {
    if (path === '/admin/courses') return { data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] }
    if (path === '/admin/question-bank/versions') return { data: [{ id: 11, courseId: 1, courseName: '软件工程', versionName: '第一次导入', status: 'DRAFT', sourceFilename: 'questions.json', questionCount: imported ? 1 : 0 }] }
    throw new Error('unexpected path: ' + path)
  })
  http.post.mockResolvedValue({ data: { id: 11, courseId: 1, courseName: '软件工程', versionName: '第一次导入', status: 'DRAFT', sourceFilename: 'questions.json', questionCount: 0 } })
  http.put.mockResolvedValue({ data: { id: 11, status: 'PUBLISHED', questionCount: 1 } })
})

describe('QuestionBankView', () => {
  it('creates a draft, imports JSON with row-level errors, and publishes it', async () => {
    http.post.mockImplementation(async (path: string) => {
      if (path === '/admin/question-bank/versions') return { data: { id: 11, courseId: 1, courseName: '软件工程', versionName: '第一次导入', status: 'DRAFT', sourceFilename: 'questions.json', questionCount: 0 } }
      if (path === '/admin/question-bank/versions/11/import') {
        imported = true
        return { data: { importId: 21, versionId: 11, totalRows: 2, successCount: 1, errorCount: 1, errors: [{ rowNumber: 2, message: '知识点不存在' }] } }
      }
      throw new Error('unexpected post: ' + path)
    })
    const wrapper = mount(QuestionBankView)
    await flushPromises()
    await wrapper.get('[data-testid="version-name"]').setValue('第一次导入')
    await wrapper.get('[data-testid="source-filename"]').setValue('questions.json')
    await wrapper.get('button[aria-label="创建题库草稿"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="questions-json"]').setValue('[{"courseId":1,"knowledgePointId":2,"type":"SINGLE","stem":"题目","options":[],"answer":["A"],"explanation":"解析","difficulty":2},{"courseId":1}]')
    await wrapper.get('button[aria-label="导入题目"]').trigger('click')
    await flushPromises()
    expect(http.post).toHaveBeenLastCalledWith('/admin/question-bank/versions/11/import', { questions: expect.any(Array) })
    expect(wrapper.text()).toContain('成功 1 条')
    expect(wrapper.text()).toContain('第 2 行')
    await wrapper.get('button[aria-label="发布题库版本 11"]').trigger('click')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/question-bank/versions/11/publish')
    expect(wrapper.text()).toContain('已发布')
    wrapper.unmount()
  })
})
