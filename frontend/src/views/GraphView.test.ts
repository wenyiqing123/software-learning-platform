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
  const wrapper = await renderGraphWithRouter()
  return wrapper
}

async function renderGraphWithRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/knowledge', component: GraphView },
      { path: '/tutor', component: { template: '<div />' } },
    ],
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

  it('supports 2-hop cumulative exploration and dual-entity bridge path discovery', async () => {
    graphApi.graph.mockImplementation(async (target: string) => {
      if (target === '系统设计') {
        return {
          nodes: [{ id: '系统设计', label: '系统设计' }, { id: '架构模式', label: '架构模式' }],
          edges: [{ source: '系统设计', target: '架构模式', label: '采用' }],
          evidence: [], available: true,
        }
      }
      return {
        nodes: [{ id: '需求分析', label: '需求分析' }, { id: '系统设计', label: '系统设计' }],
        edges: [{ source: '需求分析', target: '系统设计', label: '引出' }],
        evidence: [], available: true,
      }
    })

    const wrapper = await renderGraphMode()
    await wrapper.get('[data-testid="graph-hop-2"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('3 个实体')
    expect(wrapper.text()).toContain('2 条关系')

    await wrapper.get('[data-testid="graph-bridge-input"]').setValue('架构模式')
    await wrapper.get('[data-testid="graph-bridge-btn"]').trigger('click')
    await flushPromises()

    const bridgeBox = wrapper.get('[data-testid="graph-bridge-result"]')
    expect(bridgeBox.text()).toContain('需求分析')
    expect(bridgeBox.text()).toContain('系统设计')
    expect(bridgeBox.text()).toContain('架构模式')
    wrapper.unmount()
  })

  it('supports dual-concept comparison mode, shared-node focus, tutor prefill, same-concept guard, and honest offline state', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/knowledge', component: GraphView },
        { path: '/tutor', component: { template: '<div />' } },
      ],
    })
    await router.push('/knowledge?entity=需求分析')
    await router.isReady()

    graphApi.graph.mockImplementation(async (target: string) => {
      if (target === '结构化分析SA') {
        return {
          available: true,
          nodes: [{ id: '结构化分析SA', label: '结构化分析SA' }, { id: '需求分析', label: '需求分析' }, { id: '数据流图DFD', label: '数据流图DFD' }],
          edges: [
            { source: '结构化分析SA', target: '需求分析', label: '属于' },
            { source: '结构化分析SA', target: '数据流图DFD', label: '使用' },
          ],
          evidence: [],
        }
      }
      if (target === '面向对象分析OOA') {
        return {
          available: true,
          nodes: [{ id: '面向对象分析OOA', label: '面向对象分析OOA' }, { id: '需求分析', label: '需求分析' }, { id: '用例图', label: '用例图' }],
          edges: [
            { source: '面向对象分析OOA', target: '需求分析', label: '属于' },
            { source: '面向对象分析OOA', target: '用例图', label: '使用' },
          ],
          evidence: [],
        }
      }
      return {
        available: true,
        nodes: [{ id: '需求分析', label: '需求分析' }],
        edges: [{ source: '需求分析', target: '系统设计', label: '引出' }],
        evidence: [],
      }
    })

    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    const wrapper = mount(GraphView, { global: { plugins: [router] } })
    await flushPromises()

    await wrapper.get('[data-testid="graph-mode-compare"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="concept-comparison-panel"]').exists()).toBe(true)

    await wrapper.get('[data-testid="compare-preset-sa-ooa"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="compare-col-shared"]').text()).toContain('需求分析')
    expect(wrapper.get('[data-testid="compare-col-exclusive"]').text()).toContain('数据流图DFD')
    expect(wrapper.get('[data-testid="compare-col-exclusive"]').text()).toContain('用例图')

    // 同概念校验拦截，不触发图谱请求
    const callsBeforeSame = graphApi.graph.mock.calls.length
    await wrapper.get('[data-testid="compare-right-input"]').setValue('结构化分析SA')
    await wrapper.get('[data-testid="compare-submit-btn"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="compare-same-guard"]').text()).toContain('两个不同的概念')
    expect(graphApi.graph.mock.calls.length).toBe(callsBeforeSame)

    // 重新切回预设并跳转助教预填，严禁自动请求 LLM
    await wrapper.get('[data-testid="compare-preset-sa-ooa"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="compare-ask-tutor"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/tutor')
    expect(String(router.currentRoute.value.query.prompt || '')).toContain('结构化分析SA')
    expect(String(router.currentRoute.value.query.prompt || '')).toContain('面向对象分析OOA')
    expect(fetchSpy).not.toHaveBeenCalled()

    // 返回并聚焦共同节点至主图谱 + 验证 503 诚实离线降级
    await router.push('/knowledge?entity=需求分析')
    await flushPromises()
    await wrapper.get('[data-testid="compare-focus-shared-需求分析"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="knowledge-graph"]').exists()).toBe(true)

    graphApi.graph.mockRejectedValue(new Error('503 unavailable'))
    await wrapper.get('[data-testid="graph-mode-compare"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="compare-offline-alert"]').text()).toContain('知识图谱服务暂时不可用')
    expect(wrapper.find('[data-testid="compare-matrix"]').exists()).toBe(false)

    fetchSpy.mockRestore()
    wrapper.unmount()
  })

  it('supports deterministic KG triple cloze mode with honest sparse/offline guard and entity refocus', async () => {
    // 默认 mock 仅 1 条边：切换关系辨析页签应诚实提示三元组不足
    const sparseWrapper = await renderGraphMode()
    await sparseWrapper.get('[data-testid="graph-mode-quiz"]').trigger('click')
    await flushPromises()
    expect(sparseWrapper.get('[data-testid="kg-quiz-unavailable"]').text()).toContain('至少需要 4 条')
    sparseWrapper.unmount()

    // 真实返回 5 条三元组时，支持作答核对证据与回跳图谱聚焦
    graphApi.graph.mockResolvedValue({
      available: true,
      nodes: [
        { id: '需求分析', label: '需求分析' },
        { id: '数据流图DFD', label: '数据流图DFD' },
        { id: '用例图', label: '用例图' },
        { id: '软件需求规格说明书', label: '软件需求规格说明书' },
        { id: '系统设计', label: '系统设计' },
      ],
      edges: [
        { source: '需求分析', target: '数据流图DFD', label: '使用' },
        { source: '需求分析', target: '用例图', label: '包含' },
        { source: '需求分析', target: '软件需求规格说明书', label: '输出' },
        { source: '需求分析', target: '系统设计', label: '引出' },
        { source: '系统设计', target: '软件需求规格说明书', label: '依据' },
      ],
      evidence: [
        { subject: '需求分析', predicate: '使用', object: '数据流图DFD', score: 0.95 },
        { subject: '需求分析', predicate: '包含', object: '用例图', score: 0.9 },
        { subject: '需求分析', predicate: '输出', object: '软件需求规格说明书', score: 0.88 },
        { subject: '需求分析', predicate: '引出', object: '系统设计', score: 0.86 },
        { subject: '系统设计', predicate: '依据', object: '软件需求规格说明书', score: 0.84 },
      ],
    })

    const richWrapper = await renderGraphMode()
    await richWrapper.get('[data-testid="graph-mode-quiz"]').trigger('click')
    await flushPromises()

    expect(richWrapper.find('[data-testid="kg-quiz-card"]').exists()).toBe(true)
    await richWrapper.get('[data-testid="kg-quiz-option-A"]').trigger('click')
    await richWrapper.get('[data-testid="kg-quiz-submit"]').trigger('click')
    await flushPromises()

    expect(richWrapper.get('[data-testid="kg-quiz-feedback"]').text()).toContain('溯源三元组：')
    expect(richWrapper.get('[data-testid="kg-quiz-score"]').text()).toContain('已核对 1/')

    await richWrapper.get('[data-testid="kg-quiz-focus-node"]').trigger('click')
    await flushPromises()
    expect(richWrapper.find('[data-testid="knowledge-graph"]').exists()).toBe(true)
    richWrapper.unmount()
  })
})
