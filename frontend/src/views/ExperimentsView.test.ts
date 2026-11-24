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
  getFixedExperimentSuite: vi.fn(),
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

beforeEach(() => {
  vi.resetAllMocks()
  experimentApi.listExperiments.mockResolvedValue([])
  experimentApi.runExperiment.mockResolvedValue(savedRun())
  experimentApi.experimentAssignments.mockResolvedValue([])
  experimentApi.studentAssignments.mockResolvedValue([])
  experimentApi.getFixedExperimentSuite.mockResolvedValue({
    suiteVersion: '2026.1',
    cases: [
      { caseId: 'REQ_ACCEPTANCE', question: '下列哪条需求最容易通过明确的验收测试验证？', sourceSection: '从真实问题到可验证的需求', knowledgePoint: '需求分析' },
      { caseId: 'DFD_BALANCE', question: '将 DFD 中的一个加工分解为子图时，应优先检查什么？', sourceSection: '用数据流图解释业务数据如何变换', knowledgePoint: '数据流图DFD' },
      { caseId: 'UML_ACTOR', question: '在软件系统的 UML 用例图中，“支付平台”可以作为参与者的条件是什么？', sourceSection: '围绕参与者目标组织 UML 用例', knowledgePoint: '用例图' },
      { caseId: 'SRS_TRACE', question: '为需求 REQ-BOOK-03 建立到设计项与测试用例的对应关系，主要有什么作用？', sourceSection: '把需求规格变成设计与验收的共同依据', knowledgePoint: '软件需求规格说明书' },
      { caseId: 'DFD_STORE', question: 'DFD 中的“订单记录”数据存储表示什么？', sourceSection: '从逻辑数据集合走向一致的数据设计', knowledgePoint: '数据存储' },
      { caseId: 'LIFE_INCREMENT', question: '团队先交付航班查询，随后加入预订和退款功能，这主要体现什么？', sourceSection: '按风险与反馈安排软件生命周期活动', knowledgePoint: '软件生命周期' },
      { caseId: 'MAINT_ADAPT', question: '为兼容支付平台新版本协议而修改接口，主要属于哪类维护？', sourceSection: '让交付后的改变保持可控', knowledgePoint: '软件维护' },
      { caseId: 'ACCEPTANCE_SCENARIOS', question: '对“创建机票预订订单”进行验收时，哪些场景值得覆盖？', sourceSection: '贯穿需求、建模与验证的机票预订案例', knowledgePoint: '机票预定系统' },
    ]
  })
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

describe('fixed experiment suite manifest (M3-T12)', () => {
  it('renders 8 fixed manifest cases with metadata and selects all by default in admin view', async () => {
    const wrapper = await renderExperiments()
    const suiteSection = wrapper.find('.fixed-suite-section')
    expect(suiteSection.exists()).toBe(true)
    expect(suiteSection.text()).toContain('固定实验案例目录')
    expect(suiteSection.text()).toContain('版本 2026.1')

    const cards = suiteSection.findAll('.fixed-case-card')
    expect(cards).toHaveLength(8)
    expect(cards[0].text()).toContain('REQ_ACCEPTANCE')
    expect(cards[0].text()).toContain('从真实问题到可验证的需求 · 需求分析')
    expect(cards[0].text()).toContain('下列哪条需求最容易通过明确的验收测试验证？')

    // 默认全选
    const checkboxes = suiteSection.findAll('.case-select-label input[type="checkbox"]')
    expect(checkboxes).toHaveLength(8)
    expect(checkboxes.every(cb => (cb.element as HTMLInputElement).checked)).toBe(true)
    expect(suiteSection.text()).toContain('已选中 8 / 8 个基准案例')

    // 案例卡片内绝不包含任何答案、解析或参考答案文本框
    expect(cards[0].find('textarea').exists()).toBe(false)
    expect(cards[0].text()).not.toContain('参考答案')
    expect(cards[0].text()).not.toContain('标准答案')
  })

  it('allows deselecting all cases and selecting individual cases', async () => {
    const wrapper = await renderExperiments()
    const suiteSection = wrapper.find('.fixed-suite-section')

    // 点击取消选择
    const deselectBtn = suiteSection.findAll('.suite-header-actions button')[1]
    expect(deselectBtn.text()).toBe('取消选择')
    await deselectBtn.trigger('click')
    await flushPromises()
    expect(suiteSection.text()).toContain('已选中 0 / 8 个基准案例')

    // 勾选第一个
    const firstCheckbox = suiteSection.findAll('.case-select-label input[type="checkbox"]')[0]
    ;(firstCheckbox.element as HTMLInputElement).checked = true
    await firstCheckbox.trigger('change')
    await flushPromises()
    expect(suiteSection.text()).toContain('已选中 1 / 8 个基准案例')
  })

  it('fills single question input when clicking "填入单题输入"', async () => {
    const wrapper = await renderExperiments()
    const suiteSection = wrapper.find('.fixed-suite-section')
    const fillBtn = suiteSection.findAll('.fill-btn')[0]
    await fillBtn.trigger('click')

    const questionTextarea = wrapper.get('#experiment-question').element as HTMLTextAreaElement
    expect(questionTextarea.value).toBe('下列哪条需求最容易通过明确的验收测试验证？')
  })

  it('renders explicit error notice with retry button when fixed suite fails to load', async () => {
    experimentApi.getFixedExperimentSuite.mockRejectedValueOnce(new Error('500 Internal Error: 服务端题库不可用'))
    const wrapper = await renderExperiments()
    expect(wrapper.find('.fixed-suite-section [role="alert"]').text()).toContain('500 Internal Error: 服务端题库不可用')

    // 点击重试恢复
    await wrapper.get('.retry-btn').trigger('click')
    await flushPromises()
    expect(wrapper.find('.fixed-suite-section [role="alert"]').exists()).toBe(false)
    expect(wrapper.findAll('.fixed-case-card')).toHaveLength(8)
  })

  it('completely hides fixed suite section in student task mode', async () => {
    const wrapper = await renderStudentExperiments('/experiment-run?taskId=101')
    expect(wrapper.find('.fixed-suite-section').exists()).toBe(false)
  })
})

describe('serial batch runner (M3-T13)', () => {
  it('runs selected cases serially with empty rubric, continues on error, and reports accurate counts', async () => {
    const wrapper = await renderExperiments()
    const suiteSection = wrapper.find('.fixed-suite-section')

    // 取消选择，仅选前 2 个案例
    await suiteSection.findAll('.suite-header-actions button')[1].trigger('click')
    await flushPromises()
    const checkboxes = suiteSection.findAll('.case-select-label input[type="checkbox"]')
    ;(checkboxes[0].element as HTMLInputElement).checked = true
    await checkboxes[0].trigger('change')
    ;(checkboxes[1].element as HTMLInputElement).checked = true
    await checkboxes[1].trigger('change')
    await flushPromises()

    // 模拟第一个成功，第二个抛异常
    experimentApi.runExperiment
      .mockResolvedValueOnce({
        id: 'run-batch-1',
        question: '下列哪条需求最容易通过明确的验收测试验证？',
        createdAt: '2026-11-24T12:00:00Z',
        results: [{ mode: 'DIRECT', answer: '验证性需求', latencyMs: 30 }],
        ratings: {},
        evaluation: { source: 'SERVER_AI_SERVICE', metricsByMode: {} }
      })
      .mockRejectedValueOnce(new Error('AI 模型超时'))

    // 触发批量运行
    const batchRunBtn = suiteSection.get('.batch-run-btn')
    expect(batchRunBtn.text()).toContain('串行批量运行 (2)')
    await batchRunBtn.trigger('click')
    await flushPromises()

    // 验证调用了 2 次，且 referenceAnswer 与 expectedEntities 留空
    expect(experimentApi.runExperiment).toHaveBeenCalledTimes(2)
    expect(experimentApi.runExperiment).toHaveBeenNthCalledWith(1, expect.objectContaining({
      question: '下列哪条需求最容易通过明确的验收测试验证？',
      referenceAnswer: '',
      expectedEntities: []
    }))
    expect(experimentApi.runExperiment).toHaveBeenNthCalledWith(2, expect.objectContaining({
      question: '将 DFD 中的一个加工分解为子图时，应优先检查什么？',
      referenceAnswer: '',
      expectedEntities: []
    }))

    // 验证批次结果展示与状态汇总
    expect(suiteSection.text()).toContain('批次运行完成：成功 1 / 失败 1')
    const batchRows = suiteSection.findAll('.batch-item-row')
    expect(batchRows).toHaveLength(2)
    expect(batchRows[0].classes()).toContain('success')
    expect(batchRows[1].classes()).toContain('failed')
    expect(batchRows[1].text()).toContain('AI 模型超时')

    // 验证重试失败项只重试第 2 项
    experimentApi.runExperiment.mockResolvedValueOnce({
      id: 'run-batch-2-retry',
      question: '将 DFD 中的一个加工分解为子图时，应优先检查什么？',
      createdAt: '2026-11-24T12:01:00Z',
      results: [{ mode: 'DIRECT', answer: '输入输出流平衡', latencyMs: 25 }],
      ratings: {},
      evaluation: { source: 'SERVER_AI_SERVICE', metricsByMode: {} }
    })
    const retryFailedBtn = suiteSection.get('.retry-failed-btn')
    expect(retryFailedBtn.text()).toContain('重试失败项 (1)')
    await retryFailedBtn.trigger('click')
    await flushPromises()

    expect(experimentApi.runExperiment).toHaveBeenCalledTimes(3)
    expect(suiteSection.text()).toContain('批次运行完成：成功 2 / 失败 0')
  })

  it('cancels remaining unsent items safely when stopping batch run', async () => {
    const wrapper = await renderExperiments()
    const suiteSection = wrapper.find('.fixed-suite-section')

    // 点击批量运行
    const batchRunBtn = suiteSection.findAll('.batch-runner-buttons button')[0]
    const promise = batchRunBtn.trigger('click')

    // 触发取消
    ;(wrapper.vm as any).cancelBatchRun()
    await promise
    await flushPromises()

    // 验证未发出的项保持在队列中，不会无限发送
    expect(experimentApi.runExperiment.mock.calls.length).toBeLessThan(8)
  })
})
