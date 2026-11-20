import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ExperimentTaskAdminView from './ExperimentTaskAdminView.vue'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../api/client', () => ({ api: http, apiError: vi.fn(() => '任务管理请求失败') }))

beforeEach(() => {
  vi.resetAllMocks()
  http.get.mockResolvedValue({ data: [{ id: 9, title: '需求分析任务', description: '完成服务器实验', experimentType: 'METHODS', question: '数据流图中加工如何命名？', status: 'DRAFT', groupCount: 0, assignmentCount: 0 }] })
  http.post.mockResolvedValue({ data: { id: 9, title: '需求分析任务', description: '完成服务器实验', experimentType: 'METHODS', question: '数据流图中加工如何命名？', status: 'DRAFT', groupCount: 0, assignmentCount: 0 } })
  http.put.mockResolvedValue({ data: { id: 9, title: '需求分析任务', description: '完成服务器实验', experimentType: 'METHODS', question: '数据流图中加工如何命名？', status: 'OPEN', groupCount: 0, assignmentCount: 0 } })
})

describe('ExperimentTaskAdminView', () => {
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
    expect(http.put).toHaveBeenCalledWith('/admin/experiment-tasks/9/status', { status: 'OPEN' })
    wrapper.unmount()
  })
})
