import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ExperimentTasksView from './ExperimentTasksView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../api/client', () => ({ api: http, apiError: vi.fn(() => '实验任务请求失败') }))

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockResolvedValue({ data: [{ id: 21, taskId: 9, groupId: 3, userId: 7, status: 'ASSIGNED', title: '需求分析任务', description: '完成服务器实验', experimentType: 'METHODS', question: '数据流图中加工如何命名？', groupName: '三元组组', conditionKey: 'TRIPLES', taskStatus: 'OPEN' }] })
  http.post.mockResolvedValue({ data: { id: 21, taskId: 9, groupId: 3, userId: 7, status: 'IN_PROGRESS', title: '需求分析任务', description: '完成服务器实验', experimentType: 'METHODS', question: '数据流图中加工如何命名？', groupName: '三元组组', conditionKey: 'TRIPLES', taskStatus: 'OPEN' } })
})

describe('ExperimentTasksView', () => {
  it('renders assigned task question without leaking admin lists or identities', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/experiment-tasks', component: ExperimentTasksView }] })
    await router.push('/experiment-tasks'); await router.isReady()
    const wrapper = mount(ExperimentTasksView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.find('.task-question').text()).toBe('数据流图中加工如何命名？')
    expect(wrapper.text()).not.toContain('管理员')
    expect(wrapper.text()).not.toContain('admin')
    wrapper.unmount()
  })

  it('starts a server-managed assignment and navigates to the linked experiment', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/experiment-tasks', component: ExperimentTasksView }, { path: '/evaluation', component: { template: '<div />' } }] })
    await router.push('/experiment-tasks'); await router.isReady()
    const wrapper = mount(ExperimentTasksView, { global: { plugins: [router] } })
    await flushPromises()
    await wrapper.get('button[aria-label="开始实验任务 21"]').trigger('click')
    await flushPromises()
    expect(http.post).toHaveBeenCalledWith('/experiment-tasks/21/start')
    expect(router.currentRoute.value.fullPath).toContain('/evaluation?taskKey=%E9%9C%80%E6%B1%82%E5%88%86%E6%9E%90%E4%BB%BB%E5%8A%A1&conditionKey=TRIPLES&taskOrder=A%E2%86%92B&assignmentId=21&taskId=9')
    wrapper.unmount()
  })
})
