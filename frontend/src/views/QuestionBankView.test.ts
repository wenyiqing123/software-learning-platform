import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import QuestionBankView from './QuestionBankView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../api/client', () => ({ api: http, apiError: vi.fn(() => '题库请求失败') }))
let imported = false

async function renderWithRouter(path = '/admin/question-bank') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/question-bank', component: QuestionBankView },
      { path: '/admin/courses', component: { template: '<div>Courses</div>' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(QuestionBankView, {
    global: { plugins: [router] },
  })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  imported = false
  http.get.mockImplementation(async (path: string) => {
    if (path === '/admin/courses') return { data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] }
    if (path === '/admin/question-bank/versions') return { data: [{ id: 11, courseId: 1, courseName: '软件工程', versionName: '第一次导入', status: 'DRAFT', sourceFilename: 'questions.json', questionCount: imported ? 1 : 0 }] }
    throw new Error('unexpected path: ' + path)
  })
  http.post.mockResolvedValue({ data: { id: 11, courseId: 1, courseName: '软件工程', versionName: '第一次导入', status: 'DRAFT', sourceFilename: 'questions.json', questionCount: 0 } })
  http.put.mockResolvedValue({ data: { id: 11, status: 'PUBLISHED', questionCount: 1 } })
})

afterEach(() => vi.restoreAllMocks())

describe('QuestionBankView', () => {
  it('requires confirmation before publishing a populated draft', async () => {
    imported = true
    const confirm = vi.spyOn(window, 'confirm')
    const wrapper = mount(QuestionBankView)
    await flushPromises()
    await wrapper.get('button[aria-label="发布题库版本 11"]').trigger('click')
    await flushPromises()
    expect(confirm).not.toHaveBeenCalled()
    expect(document.body.querySelector('[data-testid="confirm-dialog"]')?.textContent).toContain('发布后将归档当前课程的旧版本')
    ;(document.body.querySelector('[data-testid="confirm-dialog-cancel"]') as HTMLButtonElement | null)?.click()
    await flushPromises()
    expect(http.put).not.toHaveBeenCalled()
    confirm.mockRestore()
    wrapper.unmount()
  })

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
    ;(document.body.querySelector('[data-testid="confirm-dialog-confirm"]') as HTMLButtonElement | null)?.click()
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/question-bank/versions/11/publish')
    expect(wrapper.text()).toContain('已发布')
    wrapper.unmount()
  })

  it('protects unsaved draft on route leave and preserves inputs when user cancels', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    const { wrapper, router } = await renderWithRouter()

    await wrapper.get('[data-testid="version-name"]').setValue('2026新题库')
    await wrapper.get('[data-testid="source-filename"]').setValue('test.json')

    // 1. 用户取消离开：拦截跳转，留在原路径，并保留输入 payload
    confirmSpy.mockReturnValueOnce(false)
    await router.push('/admin/courses')
    expect(confirmSpy).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/admin/question-bank')
    expect((wrapper.get('[data-testid="version-name"]').element as HTMLInputElement).value).toBe('2026新题库')
    expect((wrapper.get('[data-testid="source-filename"]').element as HTMLInputElement).value).toBe('test.json')

    // 2. 用户确认离开：允许跳转
    confirmSpy.mockReturnValueOnce(true)
    await router.push('/admin/courses')
    expect(router.currentRoute.value.path).toBe('/admin/courses')
    wrapper.unmount()
  })

  it('preserves dirty state and payload on failure, and resets baseline on success', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    const { wrapper, router } = await renderWithRouter()

    // 1. 创建草稿失败：保留 dirty 和表单字段
    http.post.mockRejectedValueOnce(new Error('创建异常'))
    await wrapper.get('[data-testid="version-name"]').setValue('失败版本')
    await wrapper.get('button[aria-label="创建题库草稿"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('题库请求失败')
    expect((wrapper.get('[data-testid="version-name"]').element as HTMLInputElement).value).toBe('失败版本')

    // 失败保留 dirty，路由离开仍会要求确认
    confirmSpy.mockReturnValueOnce(false)
    await router.push('/admin/courses')
    expect(confirmSpy).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/admin/question-bank')

    // 2. 创建草稿成功：清空输入并重置 baseline
    http.post.mockResolvedValueOnce({
      data: { id: 12, courseId: 1, courseName: '软件工程', versionName: '成功版本', status: 'DRAFT', sourceFilename: '', questionCount: 0 },
    })
    await wrapper.get('button[aria-label="创建题库草稿"]').trigger('click')
    await flushPromises()
    expect((wrapper.get('[data-testid="version-name"]').element as HTMLInputElement).value).toBe('')

    // 此时表单 clean，路由离开无需确认
    confirmSpy.mockClear()
    await router.push('/admin/courses')
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/admin/courses')

    // 切回题库页面
    await router.push('/admin/question-bank')
    await flushPromises()

    // 3. 导入失败：保留 dirty 和 JSON 内容
    await wrapper.get('[data-testid="questions-json"]').setValue('[{"courseId":1}]')
    http.post.mockRejectedValueOnce(new Error('导入网络错误'))
    await wrapper.get('button[aria-label="导入题目"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('题库请求失败')
    expect((wrapper.get('[data-testid="questions-json"]').element as HTMLTextAreaElement).value).toBe('[{"courseId":1}]')

    // 4. 导入成功：更新 baseline 为 clean
    http.post.mockResolvedValueOnce({
      data: { importId: 22, versionId: 11, totalRows: 1, successCount: 1, errorCount: 0, errors: [] },
    })
    await wrapper.get('button[aria-label="导入题目"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('题目批次已导入')

    // 成功后 baseline 已更新，离开无需确认
    confirmSpy.mockClear()
    await router.push('/admin/courses')
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/admin/courses')

    wrapper.unmount()
  })
})
