import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminDiagnosisView from './AdminDiagnosisView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }))
vi.mock('../api/client', () => ({ api: http }))

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin/diagnostics', component: AdminDiagnosisView }] })
  await router.push('/admin/diagnostics')
  await router.isReady()
  return mount(AdminDiagnosisView, { global: { plugins: [router] } })
}

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockImplementation(async (path: string) => {
    if (path === '/auth/me') return { data: { role: 'ADMIN' } }
    if (path === '/admin/courses') return { data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] }
    if (path === '/admin/knowledge-points') return { data: [{ id: 4, name: '数据流图', enabled: true }, { id: 5, name: '加工命名', enabled: true }] }
    if (path === '/admin/questions') return { data: [{ id: 41, courseId: 1, knowledgePointId: 4, type: 'SINGLE', stem: '加工如何命名？', options: [{ key: 'A', text: '动宾短语' }, { key: 'B', text: '名词短语' }], answer: ['A'], enabled: true }] }
    if (path === '/admin/questions/41/diagnostics') return { data: [] }
    throw new Error('Unexpected API path: ' + path)
  })
  http.put.mockResolvedValue({ data: [] })
})

describe('admin diagnosis configuration', () => {
  it('configures a distractor mapping with an explicit rationale', async () => {
    const wrapper = await mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('错因关联配置')
    await wrapper.get('button[aria-label="添加错因关联"]').trigger('click')
    await wrapper.get('select[aria-label="错因选项 1"]').setValue('B')
    await wrapper.get('select[aria-label="混淆知识点 1"]').setValue('5')
    await wrapper.get('textarea[aria-label="错因匹配依据 1"]').setValue('把加工名称误当成数据对象名称。')
    await wrapper.get('button[aria-label="保存错因关联"]').trigger('click')
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/questions/41/diagnostics', { entries: [{ optionKey: 'B', knowledgePointId: 5, rationale: '把加工名称误当成数据对象名称。' }] })
    wrapper.unmount()
  })

  it('protects unsaved diagnosis drafts on selection change and preserves inputs on cancel', async () => {
    http.get.mockImplementation(async (path: string) => {
      if (path === '/auth/me') return { data: { role: 'ADMIN' } }
      if (path === '/admin/courses') return { data: [{ id: 1, name: '软件工程', term: '2026 秋季' }] }
      if (path === '/admin/knowledge-points') return { data: [{ id: 4, name: '数据流图', enabled: true }] }
      if (path === '/admin/questions') return { data: [
        { id: 41, courseId: 1, knowledgePointId: 4, type: 'SINGLE', stem: '加工命名', options: [{ key: 'A', text: '动宾' }, { key: 'B', text: '名词' }], answer: ['A'], enabled: true },
        { id: 42, courseId: 1, knowledgePointId: 4, type: 'SINGLE', stem: '实体命名', options: [{ key: 'A', text: '名词' }, { key: 'B', text: '动词' }], answer: ['A'], enabled: true },
      ] }
      if (path.startsWith('/admin/questions/')) return { data: [] }
      throw new Error('Unexpected API path: ' + path)
    })

    const confirmSpy = vi.spyOn(window, 'confirm')
    const wrapper = await mountPage()
    await flushPromises()

    // 添加并修改草稿
    await wrapper.get('button[aria-label="添加错因关联"]').trigger('click')
    await wrapper.get('textarea[aria-label="错因匹配依据 1"]').setValue('未保存的依据草稿')

    // 尝试切换题目为 42：取消切换
    confirmSpy.mockReturnValueOnce(false)
    const questionSelect = wrapper.get('select[aria-label="错因题目"]')
    await questionSelect.setValue('42')
    await questionSelect.trigger('change')
    await flushPromises()

    // 依然保留草稿输入
    expect(confirmSpy).toHaveBeenCalled()
    expect(wrapper.get('textarea[aria-label="错因匹配依据 1"]').text()).toContain('')
    expect((wrapper.get('textarea[aria-label="错因匹配依据 1"]').element as HTMLTextAreaElement).value).toBe('未保存的依据草稿')

    wrapper.unmount()
  })
})
