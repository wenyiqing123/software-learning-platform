import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import PrerequisitePathPanel from './PrerequisitePathPanel.vue'

const api = vi.hoisted(() => ({ knowledgePoints: vi.fn(), learningPath: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: api }))

const points = [
  { id: 1, courseId: 1, name: '需求分析', graphEntity: '需求分析', mastery: 80 },
  { id: 2, courseId: 1, name: '系统设计', graphEntity: '系统设计', mastery: 20 },
  { id: 3, courseId: 1, name: '实现', graphEntity: '实现', mastery: 0 },
]
const learningPath = {
  targetId: 3,
  courseId: 1,
  cycleDetected: false,
  nodes: [
    { id: 1, courseId: 1, chapterId: 10, name: '需求分析', graphEntity: '需求分析', mastery: 80, prerequisiteIds: [], status: 'MASTERED' },
    { id: 2, courseId: 1, chapterId: 10, name: '系统设计', graphEntity: '系统设计', mastery: 20, prerequisiteIds: [1], status: 'IN_PROGRESS' },
    { id: 3, courseId: 1, chapterId: 11, name: '实现', graphEntity: '实现', mastery: 0, prerequisiteIds: [2], status: 'BLOCKED' },
  ],
}

async function renderPanel() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/practice', component: { template: '<div />' } },
    ],
  })
  await router.push('/knowledge')
  await router.isReady()
  const wrapper = mount(PrerequisitePathPanel, { props: { courseId: 1, initialTargetId: 3 }, global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.resetAllMocks()
  api.knowledgePoints.mockResolvedValue(points)
  api.learningPath.mockResolvedValue(learningPath)
})
afterEach(() => vi.restoreAllMocks())

describe('prerequisite learning path', () => {
  it('shows ordered mastery states and links to the matching course, quiz, and graph entity', async () => {
    const wrapper = await renderPanel()

    expect(api.learningPath).toHaveBeenCalledWith(3)
    expect(wrapper.findAll('[data-testid="path-node"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('已掌握')
    expect(wrapper.text()).toContain('学习中')
    expect(wrapper.text()).toContain('先修未完成')
    expect(wrapper.text()).toContain('80%')
    expect(wrapper.get('a[aria-label="学习系统设计课程内容"]').attributes('href'))
      .toContain('/courses?courseId=1&knowledgePointId=2')
    expect(wrapper.get('a[aria-label="练习实现"]').attributes('href'))
      .toContain('/practice?knowledgePointId=3&courseId=1')
    expect(wrapper.get('a[aria-label="查看实现图谱"]').attributes('href'))
      .toContain('/knowledge?entity=%E5%AE%9E%E7%8E%B0')
    wrapper.unmount()
  })

  it('explains cycles and allows retry after a path request fails', async () => {
    api.learningPath.mockRejectedValueOnce(new Error('network'))
    const wrapper = await renderPanel()
    expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法读取先修路径')
    api.learningPath.mockResolvedValueOnce({ ...learningPath, cycleDetected: true })
    await wrapper.get('button[aria-label="重试读取先修路径"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('先修关系存在循环')
    wrapper.unmount()
  })

  it('renders all four semantic statuses and displays non-color indicator for current target', async () => {
    api.learningPath.mockResolvedValueOnce({
      targetId: 4,
      courseId: 1,
      cycleDetected: false,
      nodes: [
        { id: 1, courseId: 1, chapterId: 10, name: '需求分析', graphEntity: '需求分析', mastery: 100, prerequisiteIds: [], status: 'MASTERED' },
        { id: 2, courseId: 1, chapterId: 10, name: '数据流图', graphEntity: '数据流图DFD', mastery: 50, prerequisiteIds: [1], status: 'IN_PROGRESS' },
        { id: 3, courseId: 1, chapterId: 11, name: '用例建模', graphEntity: '用例图', mastery: 0, prerequisiteIds: [2], status: 'UP_NEXT' },
        { id: 4, courseId: 1, chapterId: 11, name: '系统设计', graphEntity: '系统设计', mastery: 0, prerequisiteIds: [3], status: 'BLOCKED' },
      ],
    })
    const wrapper = await renderPanel()
    expect(wrapper.text()).toContain('已掌握')
    expect(wrapper.text()).toContain('学习中')
    expect(wrapper.text()).toContain('下一步')
    expect(wrapper.text()).toContain('先修未完成')
    expect(wrapper.find('.path-target-badge').exists()).toBe(true)
    expect(wrapper.find('.path-target-badge').text()).toContain('当前目标')
    expect(wrapper.find('.path-target').classes()).toContain('path-blocked')
    wrapper.unmount()
  })
})
