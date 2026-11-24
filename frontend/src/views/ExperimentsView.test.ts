import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ExperimentsView from './ExperimentsView.vue'

const experimentApi = vi.hoisted(() => ({
  listExperiments: vi.fn(),
  runExperiment: vi.fn(),
  rateExperiment: vi.fn(),
  experimentAssignments: vi.fn(),
  studentAssignments: vi.fn(),
}))

vi.mock('../api/client', () => ({
  apiError: vi.fn((e: unknown, fallback = '实验请求失败') => (e instanceof Error ? e.message : (e as any)?.message || fallback)),
  learningApi: experimentApi,
}))

function savedRun() {
  return {
    id: 'run-1',
    question: '数据流图中，加工的命名应采用什么形式？',
    createdAt: '2026-11-14T00:00:00Z',
    results: [],
    ratings: {},
    evaluation: { source: 'SERVER_AI_SERVICE', metricsByMode: {} },
  }
}

async function renderExperiments(path = '/experiments') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/experiments', component: ExperimentsView }]
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ExperimentsView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.resetAllMocks()
  experimentApi.listExperiments.mockResolvedValue([])
  experimentApi.runExperiment.mockResolvedValue(savedRun())
  experimentApi.experimentAssignments.mockResolvedValue([])
  experimentApi.studentAssignments.mockResolvedValue([])
})

afterEach(() => vi.restoreAllMocks())

describe('experiment expected entities', () => {
  it('explains that each field is one answer target and punctuation stays inside it', async () => {
    const wrapper = await renderExperiments()
    expect(wrapper.text()).toContain('每格内容整体作为一个答案实体或短语')
    expect(wrapper.text()).toContain('不是三元组的主语、关系、宾语')
    expect(wrapper.text()).toContain('可包含逗号、顿号等标点')
  })
  it('starts with three separate entity fields and allows adding another', async () => {
    const wrapper = await renderExperiments()
    const inputs = wrapper.findAll('[data-testid="expected-entity-input"]')
    expect(inputs).toHaveLength(3)
    expect(inputs.map(input => (input.element as HTMLInputElement).value)).toEqual(['动宾短语', '', ''])
    expect((wrapper.get('#reference-answer').element as HTMLTextAreaElement).value)
      .toBe('加工命名的语法结构必须采用动词+宾语的动宾短语形式，如校验权限、生成报表')

    await wrapper.get('[aria-label="添加期望实体"]').trigger('click')

    expect(wrapper.findAll('[data-testid="expected-entity-input"]')).toHaveLength(4)
  })

  it('accepts a complete answer target with commas as one entity', async () => {
    const wrapper = await renderExperiments()
    const target = '加工命名的语法结构必须采用动词+宾语的动宾短语形式，如校验权限、生成报表'
    await wrapper.findAll('[data-testid="expected-entity-input"]')[0].setValue(target)

    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(experimentApi.runExperiment).toHaveBeenCalledWith(expect.objectContaining({
      expectedEntities: [target],
    }))
  })

  it('trims entries, ignores blank fields, and submits entities as an array', async () => {
    const wrapper = await renderExperiments()
    const inputs = wrapper.findAll('[data-testid="expected-entity-input"]')
    await inputs[0].setValue(' 数据流图 ')
    await inputs[1].setValue('')
    await inputs[2].setValue('动宾短语')

    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()

    expect(experimentApi.runExperiment).toHaveBeenCalledWith(expect.objectContaining({
      expectedEntities: ['数据流图', '动宾短语'],
      referenceAnswer: '加工命名的语法结构必须采用动词+宾语的动宾短语形式，如校验权限、生成报表',
    }))
  })

  it('does not mix legacy raw-prompt RANKED results into the current strategy summary', async () => {
    experimentApi.listExperiments.mockResolvedValue([
      {
        ...savedRun(),
        id: 'legacy',
        results: [{ mode: 'RANKED', answer: 'legacy', latencyMs: 100, provenance: { promptSource: 'PYTHON_ORIGINAL' } }],
      },
      {
        ...savedRun(),
        id: 'current',
        results: [{ mode: 'RANKED', answer: 'current', latencyMs: 200, provenance: { systemPromptVersion: 'ranked-grounded-v3' } }],
      },
    ])
    const wrapper = await renderExperiments()
    const rankedRow = wrapper.findAll('.experiment-table tbody tr').find(row => row.text().includes('排序评分 + 原始提示词'))

    expect(rankedRow?.text()).toContain('1')
    expect(rankedRow?.text()).toContain('200 ms')
  })

  it('provides accessible query/tabs to isolate run, summary and history without losing data', async () => {
    experimentApi.listExperiments.mockResolvedValue([
      {
        ...savedRun(),
        id: 'run-history-1',
        question: '什么是加工？',
        createdAt: '2026-11-20T10:00:00Z',
        results: [{ mode: 'DIRECT', answer: '加工是变换数据流的操作', latencyMs: 30 }],
        ratings: {},
        evaluation: { source: 'SERVER_AI_SERVICE', experimentType: 'METHODS', metricsByMode: {} }
      }
    ])
    const wrapper = await renderExperiments()

    // 检查 tablist 与 tabs
    const tablist = wrapper.find('[role="tablist"]')
    expect(tablist.exists()).toBe(true)
    const tabs = tablist.findAll('[role="tab"]')
    expect(tabs).toHaveLength(3)
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[0].text()).toContain('对比实验与本次结果')

    // 切换到个人实验统计
    await tabs[1].trigger('click')
    await flushPromises()
    expect(tabs[1].attributes('aria-selected')).toBe('true')
    expect(wrapper.text()).toContain('个人实验统计')

    // 切换到实验历史
    await tabs[2].trigger('click')
    await flushPromises()
    expect(tabs[2].attributes('aria-selected')).toBe('true')
    expect(wrapper.text()).toContain('什么是加工？')

    // 点击历史记录项，切回 run 且展示该记录
    await wrapper.find('.saved-experiment-list button').trigger('click')
    await flushPromises()
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(wrapper.text()).toContain('本次结果')
    expect(wrapper.text()).toContain('加工是变换数据流的操作')
  })
})

describe('student task restricted mode', () => {
  async function renderStudentExperiments(path = '/experiment-run?taskId=101&assignmentId=5') {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/experiment-run', component: ExperimentsView, props: { studentTaskMode: true } },
        { path: '/experiments', component: ExperimentsView }
      ]
    })
    await router.push(path)
    await router.isReady()
    const wrapper = mount(ExperimentsView, {
      props: { studentTaskMode: true },
      global: { plugins: [router] }
    })
    await flushPromises()
    return wrapper
  }

  it('hides rubric, free editor, history tabs, export buttons, and rating controls in studentTaskMode', async () => {
    experimentApi.experimentAssignments.mockResolvedValue([
      { id: 5, taskId: 101, title: '实验任务-数据流图', question: '数据流图中加工的命名应采用什么形式？', status: 'IN_PROGRESS' }
    ])
    const wrapper = await renderStudentExperiments('/experiment-run?taskId=101&assignmentId=5')

    // 只读题干呈现
    expect(wrapper.text()).toContain('指派实验题干（只读）')
    expect(wrapper.text()).toContain('数据流图中加工的命名应采用什么形式？')

    // 自由输入与各编辑项隐藏
    expect(wrapper.find('#experiment-question').exists()).toBe(false)
    expect(wrapper.find('#reference-answer').exists()).toBe(false)
    expect(wrapper.find('[data-testid="expected-entity-input"]').exists()).toBe(false)
    expect(wrapper.find('#experiment-type').exists()).toBe(false)
    expect(wrapper.find('.ablation-control').exists()).toBe(false)

    // 导出与管理历史/统计隐藏
    expect(wrapper.find('.experiment-export-actions').exists()).toBe(false)
    expect(wrapper.find('[role="tablist"]').exists()).toBe(false)
    expect(wrapper.find('.saved-experiment-list').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('个人实验统计')

    // 运行按钮处于只读学生任务态
    const runBtn = wrapper.get('button.button-dark')
    expect(runBtn.text()).toContain('运行问答对比')
    expect(runBtn.attributes('disabled')).toBeUndefined()
  })

  it('sends exact { taskId } without rubric or question parameters when running in student mode', async () => {
    experimentApi.experimentAssignments.mockResolvedValue([
      { id: 5, taskId: 101, title: '实验任务-数据流图', question: '数据流图中加工的命名应采用什么形式？', status: 'IN_PROGRESS' }
    ])
    experimentApi.runExperiment.mockResolvedValue({
      id: 'run-student-1',
      question: '数据流图中加工的命名应采用什么形式？',
      createdAt: '2026-11-20T12:00:00Z',
      results: [
        { mode: 'DIRECT', answer: '采用动宾短语形式', latencyMs: 45 },
        { mode: 'TRIPLES', answer: '采用动词加宾语的动宾短语形式', latencyMs: 80 }
      ],
      ratings: {},
      evaluation: { source: 'SERVER_AI_SERVICE', metricsByMode: {} }
    })

    const wrapper = await renderStudentExperiments('/experiment-run?taskId=101&assignmentId=5')
    await wrapper.get('button.button-dark').trigger('click')
    await flushPromises()

    // 严格断言请求体：只发送 taskId: 101，严禁带 question / referenceAnswer / expectedEntities / experimentType / includeAblations
    expect(experimentApi.runExperiment).toHaveBeenCalledWith({ taskId: 101 })

    // 验证结果渲染且隐藏人工评分下拉框
    expect(wrapper.text()).toContain('采用动宾短语形式')
    expect(wrapper.find('.rating-select').exists()).toBe(false)
  })

  it('renders real error message when student has no matched assignment or when assignment request fails', async () => {
    // 场景 1：未匹配到指派
    experimentApi.experimentAssignments.mockResolvedValue([])
    const wrapperNoMatch = await renderStudentExperiments('/experiment-run?taskId=999')
    expect(wrapperNoMatch.find('[role="alert"]').text()).toContain('未找到指派给您的有效实验任务')

    // 场景 2：接口 403 权限拒绝
    experimentApi.experimentAssignments.mockRejectedValue(new Error('403 Forbidden: 学生无权访问该资源'))
    const wrapperForbidden = await renderStudentExperiments('/experiment-run?taskId=101')
    expect(wrapperForbidden.find('[role="alert"]').text()).toContain('403 Forbidden: 学生无权访问该资源')
  })
})
