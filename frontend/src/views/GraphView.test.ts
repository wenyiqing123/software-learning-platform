import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import GraphView from './GraphView.vue'

const graphApi = vi.hoisted(() => ({
  instances: [] as Array<{ on: ReturnType<typeof vi.fn>; render: ReturnType<typeof vi.fn>; destroy: ReturnType<typeof vi.fn> }>,
  graph: vi.fn(),
  knowledgePoints: vi.fn(),
  learningPath: vi.fn(),
}))

vi.mock('@antv/g6', () => ({
  Graph: class {
    on = vi.fn()
    render = vi.fn().mockResolvedValue(undefined)
    destroy = vi.fn()
    constructor() { graphApi.instances.push(this) }
  },
}))
vi.mock('../components/ThreeGraphCanvas.vue', () => ({ default: { template: '<div data-testid="three-graph" />', props: ['nodes', 'edges', 'selectedId'] } }))
vi.mock('../api/client', () => ({ learningApi: graphApi }))

async function renderGraphView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/knowledge', component: GraphView },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/practice', component: { template: '<div />' } },
    ],
  })
  await router.push('/knowledge?view=path&courseId=1&knowledgePointId=3')
  await router.isReady()
  const wrapper = mount(GraphView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

async function renderGraphMode() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/knowledge', component: GraphView }],
  })
  await router.push('/knowledge?entity=需求分析')
  await router.isReady()
  const wrapper = mount(GraphView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.resetAllMocks()
  graphApi.instances = []
  graphApi.graph.mockResolvedValue({
    nodes: [{ id: '需求分析', label: '需求分析' }, { id: '系统设计', label: '系统设计' }],
    edges: [{ source: '需求分析', target: '系统设计', label: '引出' }],
    evidence: [], available: true,
  })
  graphApi.knowledgePoints.mockResolvedValue([{ id: 3, courseId: 1, name: '实现', graphEntity: '实现', mastery: 0, sortOrder: 3 }])
  graphApi.learningPath.mockResolvedValue({ targetId: 3, courseId: 1, nodes: [], cycleDetected: false })
})
afterEach(() => vi.restoreAllMocks())

describe('knowledge graph view modes', () => {
  it('does not load graph evidence in path mode and recreates the canvas when returning', async () => {
    const wrapper = await renderGraphView()
    expect(graphApi.graph).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('先修学习路径')

    await wrapper.get('[role="tablist"] button:first-child').trigger('click')
    await flushPromises()

    expect(graphApi.graph).toHaveBeenCalledWith('需求分析')
    expect(graphApi.instances).toHaveLength(1)
    expect(graphApi.instances[0].render).toHaveBeenCalledOnce()
    wrapper.unmount()
    expect(graphApi.instances[0].destroy).toHaveBeenCalledOnce()
  })

  it('switches to the real 3D viewport and exposes interaction analysis', async () => {
    const wrapper = await renderGraphMode()
    await wrapper.get('button[aria-label="3D 图谱"]').trigger('click')
    expect(wrapper.find('[data-testid="three-graph"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('交互分析')
    expect(wrapper.text()).toContain('平均证据分')
    wrapper.unmount()
  })

  it('honestly displays offline/error state without fabricating mock nodes or triples', async () => {
    graphApi.graph.mockRejectedValueOnce(new Error('Network error'))
    const wrapper = await renderGraphMode()
    expect(wrapper.text()).toContain('知识服务暂时不可用，请确认 Python Bridge 与 NebulaGraph 已启动。')
    expect(wrapper.text()).toContain('0 个实体')
    expect(wrapper.text()).toContain('0 条关系')
    wrapper.unmount()
  })
})
