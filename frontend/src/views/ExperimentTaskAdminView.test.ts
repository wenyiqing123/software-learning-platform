import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ExperimentTaskAdminView from './ExperimentTaskAdminView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../api/client', () => ({
  api: http,
  apiError: vi.fn((err: any, fallback: string) => err?.response?.data?.message || fallback)
}))

async function renderWithRouter(path = '/admin/experiment-tasks') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/experiment-tasks', component: ExperimentTaskAdminView },
      { path: '/other', component: { template: '<div>Other</div>' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ExperimentTaskAdminView, {
    global: { plugins: [router] },
  })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  http.get.mockImplementation((url: string) => {
    if (url === '/admin/experiment-tasks') {
      return Promise.resolve({
        data: [{
          id: 9,
          title: '需求分析任务',
          description: '完成服务器实验',
          experimentType: 'METHODS',
          question: '数据流图中加工如何命名？',
          status: 'DRAFT',
          groupCount: 1,
          assignmentCount: 0
        }]
      })
    }
    if (url === '/admin/experiment-tasks/9/groups') {
      return Promise.resolve({
        data: [{
          id: 31,
          taskId: 9,
          name: '三元组对照组',
          conditionKey: 'TRIPLES',
          sortOrder: 1,
          taskOrder: 'A→B'
        }]
      })
    }
    return Promise.resolve({ data: [] })
  })
  http.post.mockResolvedValue({
    data: {
      id: 9,
      title: '需求分析任务',
      description: '完成服务器实验',
      experimentType: 'METHODS',
      question: '数据流图中加工如何命名？',
      status: 'DRAFT',
      groupCount: 0,
      assignmentCount: 0
    }
  })
  http.put.mockResolvedValue({
    data: {
      id: 9,
      title: '需求分析任务',
      description: '完成服务器实验',
      experimentType: 'METHODS',
      question: '数据流图中加工如何命名？',
      status: 'OPEN',
      groupCount: 0,
      assignmentCount: 0
    }
  })
})
afterEach(() => vi.restoreAllMocks())

describe('ExperimentTaskAdminView', () => {
  it('renders H1 by default and H2 when embedded', async () => {
    const standalone = mount(ExperimentTaskAdminView)
    await flushPromises()
    expect(standalone.find('h1').text()).toBe('实验任务管理')
    standalone.unmount()

    const embedded = mount(ExperimentTaskAdminView, { props: { embedded: true } })
    await flushPromises()
    expect(embedded.find('h2').text()).toBe('实验任务管理')
    embedded.unmount()
  })

  it('creates a task and opens it for participants', async () => {
    const wrapper = mount(ExperimentTaskAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="task-title"]').setValue('需求分析任务')
    await wrapper.get('[data-testid="task-question"]').setValue('数据流图中加工如何命名？')
    await wrapper.get('button[aria-label="创建实验任务"]').trigger('click')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/admin/experiment-tasks', expect.objectContaining({ title: '需求分析任务' }))
    await wrapper.get('button[aria-label="开放实验任务 9"]').trigger('click')
    await flushPromises()
    ;(document.body.querySelector('[data-testid="confirm-dialog-confirm"]') as HTMLButtonElement | null)?.click()
    await flushPromises()
    expect(http.put).toHaveBeenCalledWith('/admin/experiment-tasks/9/status', { status: 'OPEN' })
    wrapper.unmount()
  })

  it('fetches and displays groups with order values for task', async () => {
    const wrapper = mount(ExperimentTaskAdminView)
    await flushPromises()
    expect(http.get).toHaveBeenCalledWith('/admin/experiment-tasks/9/groups')
    expect(wrapper.text()).toContain('三元组对照组')
    expect(wrapper.text()).toContain('A→B')
    wrapper.unmount()
  })

  it('creates group with required order values', async () => {
    const wrapper = mount(ExperimentTaskAdminView)
    await flushPromises()

    await wrapper.get('[data-testid="group-name"]').setValue('消融对照组')
    await wrapper.get('[data-testid="group-condition"]').setValue('ABLATION')

    // 未选择 taskOrder 时尝试创建应提示错误并阻止请求
    await wrapper.get('button[aria-label="添加实验组 9"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('必须指定评估顺序')
    expect(http.post).not.toHaveBeenCalledWith(expect.stringContaining('/groups'), expect.anything())

    // 选择 A→B 正常提交
    await wrapper.get('[data-testid="group-task-order"]').setValue('A→B')
    await wrapper.get('button[aria-label="添加实验组 9"]').trigger('click')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/admin/experiment-tasks/9/groups', {
      name: '消融对照组',
      conditionKey: 'ABLATION',
      sortOrder: 1,
      taskOrder: 'A→B'
    })
    wrapper.unmount()
  })

  it('assigns students with parsed unique IDs and confirmation', async () => {
    const wrapper = mount(ExperimentTaskAdminView)
    await flushPromises()

    // 选组并输入逗号和换行混排的 student IDs
    await wrapper.get('[data-testid="assign-group-select"]').setValue('31')
    const textarea = wrapper.get('[data-testid="assign-student-ids"]')
    await textarea.setValue('101, 102\n103')

    await wrapper.get('button[aria-label="派发学生 9"]').trigger('click')
    await flushPromises()

    const confirmBtn = document.body.querySelector('[data-testid="confirm-dialog-confirm"]') as HTMLButtonElement | null
    expect(document.body.textContent).toContain('3 名')
    confirmBtn?.click()
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/admin/experiment-tasks/9/assign', {
      groupId: 31,
      userIds: [101, 102, 103]
    })
    // 成功后输入框清空
    expect((wrapper.get('[data-testid="assign-student-ids"]').element as HTMLTextAreaElement).value).toBe('')
    wrapper.unmount()
  })

  it('cancels assign confirmation without sending requests', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = mount(ExperimentTaskAdminView)
    await flushPromises()

    await wrapper.get('[data-testid="assign-group-select"]').setValue('31')
    await wrapper.get('[data-testid="assign-student-ids"]').setValue('101, 102')

    await wrapper.get('button[aria-label="派发学生 9"]').trigger('click')
    await flushPromises()

    expect(http.post).not.toHaveBeenCalledWith(expect.stringContaining('/assign'), expect.anything())
    wrapper.unmount()
  })

  it('rejects duplicate or invalid IDs and retains input on 400 error', async () => {
    const wrapper = mount(ExperimentTaskAdminView)
    await flushPromises()

    await wrapper.get('[data-testid="assign-group-select"]').setValue('31')
    const textarea = wrapper.get('[data-testid="assign-student-ids"]')

    // 重复 ID
    await textarea.setValue('101, 102, 101')
    await wrapper.get('button[aria-label="派发学生 9"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('重复')
    expect(http.post).not.toHaveBeenCalledWith(expect.stringContaining('/assign'), expect.anything())

    // 非法 ID
    await textarea.setValue('101, -5')
    await wrapper.get('button[aria-label="派发学生 9"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('正整数')

    // 后端返回 400 失败时保留输入
    http.post.mockRejectedValueOnce({ response: { data: { message: '实验任务只能分配给学生账号' } } })
    await textarea.setValue('101, 102')
    await wrapper.get('button[aria-label="派发学生 9"]').trigger('click')
    await flushPromises()
    ;(document.body.querySelector('[data-testid="confirm-dialog-confirm"]') as HTMLButtonElement | null)?.click()
    await flushPromises()

    expect(wrapper.text()).toContain('只能分配给学生账号')
    expect((wrapper.get('[data-testid="assign-student-ids"]').element as HTMLTextAreaElement).value).toBe('101, 102')
    wrapper.unmount()
  })

  it('protects unsaved task draft on route leave and preserves inputs when user cancels', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    const { wrapper, router } = await renderWithRouter()

    await wrapper.get('[data-testid="task-title"]').setValue('未保存任务')
    await wrapper.get('[data-testid="task-question"]').setValue('未保存问题描述')

    // 1. 用户取消离开：留在原路由，输入字段保留
    confirmSpy.mockReturnValueOnce(false)
    await router.push('/other')
    expect(confirmSpy).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/admin/experiment-tasks')
    expect((wrapper.get('[data-testid="task-title"]').element as HTMLInputElement).value).toBe('未保存任务')
    expect((wrapper.get('[data-testid="task-question"]').element as HTMLTextAreaElement).value).toBe('未保存问题描述')

    // 2. 用户确认离开：正常跳转
    confirmSpy.mockReturnValueOnce(true)
    await router.push('/other')
    expect(router.currentRoute.value.path).toBe('/other')
    wrapper.unmount()
  })

  it('preserves dirty state and fields on failed create, and resets baseline to clean on success', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    const { wrapper, router } = await renderWithRouter()

    // 1. 模拟创建失败
    http.post.mockImplementationOnce((url: string) => {
      if (url === '/admin/experiment-tasks') {
        return Promise.reject(new Error('创建失败模拟异常'))
      }
      return Promise.resolve({ data: {} })
    })

    await wrapper.get('[data-testid="task-title"]').setValue('异常任务')
    await wrapper.get('[data-testid="task-question"]').setValue('异常问题')
    await wrapper.get('button[aria-label="创建实验任务"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('实验任务创建失败')
    expect((wrapper.get('[data-testid="task-title"]').element as HTMLInputElement).value).toBe('异常任务')
    expect((wrapper.get('[data-testid="task-question"]').element as HTMLTextAreaElement).value).toBe('异常问题')

    // 失败后依然保持 dirty，路由离开会提示确认
    confirmSpy.mockReturnValueOnce(false)
    await router.push('/other')
    expect(confirmSpy).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/admin/experiment-tasks')

    // 2. 模拟创建成功
    http.post.mockResolvedValueOnce({
      data: {
        id: 10,
        title: '新任务',
        description: '',
        experimentType: 'METHODS',
        question: '新问题',
        status: 'DRAFT',
        groupCount: 0,
        assignmentCount: 0,
      },
    })
    await wrapper.get('button[aria-label="创建实验任务"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('实验任务草稿已创建')
    expect((wrapper.get('[data-testid="task-title"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('[data-testid="task-question"]').element as HTMLTextAreaElement).value).toBe('')

    // 成功后 baseline 重置为 clean，路由离开无需确认
    confirmSpy.mockClear()
    await router.push('/other')
    expect(confirmSpy).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/other')
    wrapper.unmount()
  })

  it('picks enabled student accounts on demand and appends deduplicated IDs', async () => {
    const baseGet = http.get.getMockImplementation()!
    http.get.mockImplementation((url: string, config?: unknown) => {
      if (url === '/admin/users') {
        return Promise.resolve({
          data: {
            items: [
              { id: 101, username: 'stu101', displayName: '赵同学', role: 'STUDENT', enabled: true },
              { id: 205, username: 'stu205', displayName: '钱同学', role: 'STUDENT', enabled: true },
            ],
            total: 2,
          },
        })
      }
      return baseGet(url, config as never)
    })

    const wrapper = mount(ExperimentTaskAdminView)
    await flushPromises()
    await wrapper.get('[data-testid="assign-student-ids"]').setValue('101')
    await wrapper.get('[data-testid="pick-students-9"]').trigger('click')
    await flushPromises()

    expect(http.get).toHaveBeenCalledWith('/admin/users', { params: { role: 'STUDENT', enabled: true, size: 100 } })
    const checkboxes = wrapper.findAll('[data-testid="student-picker-9"] input[type="checkbox"]')
    expect(checkboxes).toHaveLength(2)
    await checkboxes[0].setValue(true)
    await checkboxes[1].setValue(true)
    await wrapper.get('[data-testid="student-picker-9"] button.secondary-button').trigger('click')

    expect((wrapper.get('[data-testid="assign-student-ids"]').element as HTMLTextAreaElement).value).toBe('101, 205')
    wrapper.unmount()
  })
})
