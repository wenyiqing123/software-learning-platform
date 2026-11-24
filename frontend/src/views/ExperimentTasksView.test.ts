import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ExperimentTasksView from './ExperimentTasksView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../api/client', () => ({ api: http, apiError: vi.fn(() => '实验任务请求失败') }))

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockResolvedValue({
    data: [{
      id: 21,
      taskId: 9,
      groupId: 3,
      status: 'ASSIGNED',
      title: '需求分析任务',
      description: '完成服务器实验',
      experimentType: 'METHODS',
      question: '数据流图中加工如何命名？',
      groupName: '三元组组',
      conditionKey: 'TRIPLES',
      taskStatus: 'OPEN',
      taskOrder: 'B→A'
    }]
  })
})

describe('ExperimentTasksView', () => {
  it('renders assigned task question without leaking admin lists or identities', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/experiment-tasks', component: ExperimentTasksView }] })
    await router.push('/experiment-tasks'); await router.isReady()
    const wrapper = mount(ExperimentTasksView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.find('.task-question').text()).toBe('数据流图中加工如何命名？')
    expect(wrapper.text()).toContain('顺序 B→A')
    expect(wrapper.text()).not.toContain('管理员')
    expect(wrapper.text()).not.toContain('admin')
    wrapper.unmount()
  })

  it('navigates directly to evaluation consent page with server-authoritative B→A order without premature post', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/experiment-tasks', component: ExperimentTasksView },
        { path: '/evaluation', component: { template: '<div />' } }
      ]
    })
    await router.push('/experiment-tasks'); await router.isReady()
    const wrapper = mount(ExperimentTasksView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.get('button[aria-label="开始实验任务 21"]').trigger('click')
    await flushPromises()

    // 严禁在 consent 前 POST /start
    expect(http.post).not.toHaveBeenCalled()
    // 携带真实 B→A 顺序与 assignmentId / taskId 导航
    expect(router.currentRoute.value.fullPath).toContain('/evaluation')
    expect(router.currentRoute.value.query.taskOrder).toBe('B→A')
    expect(router.currentRoute.value.query.assignmentId).toBe('21')
    expect(router.currentRoute.value.query.taskId).toBe('9')
    wrapper.unmount()
  })

  it('displays "顺序未记录" and disables start button when taskOrder is null or missing', async () => {
    http.get.mockResolvedValueOnce({
      data: [{
        id: 22,
        taskId: 10,
        groupId: 4,
        status: 'ASSIGNED',
        title: '旧遗留任务',
        description: '未指定顺序',
        experimentType: 'METHODS',
        question: '用例图中参与者条件？',
        groupName: '对照组',
        conditionKey: 'DIRECT',
        taskStatus: 'OPEN',
        taskOrder: null
      }]
    })

    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/experiment-tasks', component: ExperimentTasksView }] })
    await router.push('/experiment-tasks'); await router.isReady()
    const wrapper = mount(ExperimentTasksView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('顺序未记录')
    const startBtn = wrapper.get('button[aria-label="开始实验任务 22"]')
    expect(startBtn.attributes('disabled')).toBeDefined()
    expect(startBtn.text()).toContain('顺序未记录（已禁用）')
    wrapper.unmount()
  })
})
