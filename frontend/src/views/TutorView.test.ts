import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TutorView from './TutorView.vue'

const learning = vi.hoisted(() => ({
  sessions: vi.fn(),
  createSession: vi.fn(),
  renameSession: vi.fn(),
  saveMessage: vi.fn(),
  streamAsk: vi.fn(),
  sessionMessages: vi.fn(),
  tutorHint: vi.fn(),
  deleteSession: vi.fn(),
  knowledgePoints: vi.fn(),
}))
vi.mock('../api/client', () => ({
  learningApi: learning,
  apiError: vi.fn((err: any, fallback?: string) => err?.message || fallback || '请求失败'),
}))

async function mountPage(path = '/tutor') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/tutor', component: TutorView },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/practice', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  return mount(TutorView, { global: { plugins: [router] } })
}

describe('TutorView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    localStorage.removeItem('slp-tutor-step-hint-sessions')
    Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: 1440 })
    learning.sessions.mockResolvedValue([])
    learning.createSession.mockResolvedValue({ id: 1, title: '学习会话' })
    learning.renameSession.mockResolvedValue({ id: 1, title: '重命名会话' })
    learning.sessionMessages.mockResolvedValue([])
    learning.knowledgePoints.mockResolvedValue([])
    learning.saveMessage.mockImplementation(async (_id: number, payload: any) => ({ id: Date.now(), ...payload }))
    learning.streamAsk.mockImplementation(async (_question: string, _mode: string, onEvent: (name: string, payload: any) => void) => {
      onEvent('knowledge', { triples: [{ subject: '加工', predicate: '命名', object: '动宾短语' }] })
      onEvent('delta', { content: '加工命名使用动宾短语。' })
      return {
        mode: 'RANKED',
        answer: '加工命名使用动宾短语。',
        triples: [{ subject: '加工', predicate: '命名', object: '动宾短语' }],
        prompt: ['server', 'only', 'constraint'].join('-'),
      }
    })
  })

  it('removes mode selector and compare mode while locking backend requests to RANKED without leaking system prompt', async () => {
    const wrapper = await mountPage()
    await flushPromises()

    // 页面不存在三档位选择、对比模式及策略说明区
    expect(wrapper.find('.tutor-mode-selector').exists()).toBe(false)
    expect(wrapper.find('.compare-toggle').exists()).toBe(false)
    expect(wrapper.find('.tutor-aside').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('大模型直答')
    expect(wrapper.text()).not.toContain('三元组增强')
    expect(wrapper.text()).not.toContain('对比三种方式')
    expect(wrapper.text()).not.toContain('RANKED')

    await wrapper.get('#question-input').setValue('加工如何命名？')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()

    expect(learning.streamAsk).toHaveBeenCalledTimes(1)
    expect(learning.streamAsk.mock.calls[0][0]).toBe('加工如何命名？')
    expect(learning.streamAsk.mock.calls[0][1]).toBe('RANKED')
    expect(learning.saveMessage).toHaveBeenCalledWith(1, expect.objectContaining({ mode: 'RANKED' }))

    expect(wrapper.text()).toContain('加工命名使用动宾短语。')
    expect(wrapper.text()).toContain('已参考 1 条课程图谱证据')
    expect(wrapper.text()).not.toContain('server-only-constraint')
    wrapper.unmount()
  })

  it('keeps route learning context visible without sending automatically', async () => {
    const wrapper = await mountPage('/tutor?entity=数据流图&courseId=3&sectionId=5&knowledgePointId=8')
    await flushPromises()

    expect(wrapper.text()).toContain('数据流图')
    expect(wrapper.text()).toContain('课程3')
    expect(wrapper.text()).toContain('小节5')
    expect(wrapper.text()).toContain('知识点8')
    expect(learning.streamAsk).not.toHaveBeenCalled()
    expect((wrapper.get('#question-input').element as HTMLTextAreaElement).value).toContain('数据流图')
    wrapper.unmount()
  })

  it('supports 2x2 quick prompt fill without sending automatically', async () => {
    const wrapper = await mountPage('/tutor?entity=用例图')
    await flushPromises()
    const promptButtons = wrapper.findAll('[data-testid="tutor-quick-prompt"]')
    expect(promptButtons).toHaveLength(4)
    await promptButtons[0].trigger('click')
    expect(learning.streamAsk).not.toHaveBeenCalled()
    expect((wrapper.get('#question-input').element as HTMLTextAreaElement).value).toContain('用例图')
    wrapper.unmount()
  })

  it('prevents IME composing Enter submit and supports stop generation + honest failure retry', async () => {
    let capturedSignal: AbortSignal | undefined
    let rejectStream!: (reason?: unknown) => void
    learning.streamAsk.mockImplementationOnce((_q: string, _m: string, onEvent: (n: string, p: any) => void, signal?: AbortSignal) => {
      capturedSignal = signal
      onEvent('delta', { content: '先看输入输出' })
      return new Promise((_resolve, reject) => {
        rejectStream = reject
        signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      })
    })

    const wrapper = await mountPage()
    await flushPromises()

    const textarea = wrapper.get('#question-input')
    await textarea.setValue('需求如何核验？')
    // 中文输入法组合态按 Enter 不应发送
    await textarea.trigger('keydown.enter', { isComposing: true })
    expect(learning.streamAsk).not.toHaveBeenCalled()

    // 点击发送开始流式生成
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="tutor-stop"]').exists()).toBe(true)

    // 停止生成
    await wrapper.get('[data-testid="tutor-stop"]').trigger('click')
    await flushPromises()
    expect(capturedSignal?.aborted).toBe(true)
    expect(wrapper.text()).toContain('已停止生成')
    expect(wrapper.text()).toContain('先看输入输出')

    // 模拟 503 服务离线异常与诚实错误重试
    learning.streamAsk.mockRejectedValueOnce(new Error('知识检索服务不可用（503 KNOWLEDGE_UNAVAILABLE）'))
    await wrapper.get('#question-input').setValue('什么是数据存储？')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('503 KNOWLEDGE_UNAVAILABLE')
    expect(wrapper.find('[data-testid="tutor-retry"]').exists()).toBe(true)

    // 重试成功
    await wrapper.get('[data-testid="tutor-retry"]').trigger('click')
    await flushPromises()
    expect(learning.streamAsk).toHaveBeenLastCalledWith('什么是数据存储？', 'RANKED', expect.any(Function), expect.any(AbortSignal), expect.any(Object))
    expect(wrapper.text()).toContain('加工命名使用动宾短语。')
    void rejectStream
    wrapper.unmount()
  })

  it('supports sidebar collapse, session switching, legacy mode messages, and integrated step-by-step hints', async () => {
    learning.sessions.mockResolvedValueOnce([{ id: 10, title: '初次会话' }])
    learning.sessionMessages.mockResolvedValueOnce([
      { id: 1, role: 'USER', content: '什么是用例图？', mode: 'TRIPLES' },
      {
        id: 2,
        role: 'ASSISTANT',
        content: '[TRIPLES] 用例图描述参与者与系统功能。',
        mode: 'TRIPLES',
        metadata: {
          results: [
            { mode: 'TRIPLES', answer: '用例图描述参与者与系统功能。', triples: [{ subject: '用例图', predicate: '描述', object: '系统功能' }] },
          ],
        },
      },
    ])
    learning.tutorHint
      .mockResolvedValueOnce({
        question: '如何划分用例？',
        level: 1,
        content: '思考参与者与系统的交互目标。',
        hasEvidence: false,
        evidence: [],
        nextLevelAvailable: true,
        source: 'MODEL',
      })
      .mockResolvedValueOnce({
        question: '用例之间包含什么关系？',
        level: 1,
        content: '区分包含、扩展与泛化三种关系。',
        hasEvidence: false,
        evidence: [],
        nextLevelAvailable: true,
        source: 'MODEL',
      })

    const wrapper = await mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('初次会话')

    // 1440px 无上下文时默认展开，支持收起与展开
    expect(wrapper.find('.tutor-sidebar-pane.is-collapsed').exists()).toBe(false)
    await wrapper.get('[data-testid="tutor-collapse-sidebar"]').trigger('click')
    expect(wrapper.find('.tutor-sidebar-pane.is-collapsed').exists()).toBe(true)
    await wrapper.get('[data-testid="tutor-expand-sidebar"]').trigger('click')
    expect(wrapper.find('.tutor-sidebar-pane.is-collapsed').exists()).toBe(false)

    // 加载历史旧模式消息平滑展示正文与证据、隐藏旧模式名
    await wrapper.get('.tutor-session-select').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('用例图描述参与者与系统功能。')
    expect(wrapper.text()).toContain('已参考 1 条课程图谱证据')
    expect(wrapper.text()).not.toContain('TRIPLES')

    // 开启当前对话的分步提示开关，连续提问均生效，新建对话或切换后独立隔离
    const hintToggle = wrapper.get('[data-testid="tutor-hint-toggle"]')
    expect(hintToggle.attributes('aria-checked')).toBe('false')
    await hintToggle.trigger('click')
    expect(hintToggle.attributes('aria-checked')).toBe('true')

    await wrapper.get('#question-input').setValue('如何划分用例？')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()

    expect(learning.tutorHint).toHaveBeenCalledWith('如何划分用例？', 1, expect.any(Array), expect.any(Array))
    expect(learning.streamAsk).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('思考参与者与系统的交互目标。')
    expect(wrapper.text()).toContain('分步学习提示 · 第 1 级')
    expect(hintToggle.attributes('aria-checked')).toBe('true')

    // 同一对话下再次提问，开关仍持续生效
    await wrapper.get('#question-input').setValue('用例之间包含什么关系？')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()

    expect(learning.tutorHint).toHaveBeenCalledTimes(2)
    expect(learning.tutorHint).toHaveBeenLastCalledWith('用例之间包含什么关系？', 1, expect.any(Array), expect.any(Array))
    expect(learning.streamAsk).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('区分包含、扩展与泛化三种关系。')

    // 新建对话默认关闭开关，提问走普通图谱增强直答流
    await wrapper.get('[data-testid="tutor-new-session"]').trigger('click')
    await flushPromises()
    expect(hintToggle.attributes('aria-checked')).toBe('false')
    await wrapper.get('#question-input').setValue('需求分析包含哪些阶段？')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()
    expect(learning.streamAsk).toHaveBeenCalledTimes(1)

    // 切回原会话 10 自动恢复该对话的开启状态，关闭后再问回归直答流
    const targetSessionBtn = wrapper.findAll('.tutor-session-select').find(btn => btn.text().includes('初次会话'))
    expect(targetSessionBtn).toBeDefined()
    await targetSessionBtn!.trigger('click')
    await flushPromises()
    expect(hintToggle.attributes('aria-checked')).toBe('true')

    await hintToggle.trigger('click')
    expect(hintToggle.attributes('aria-checked')).toBe('false')
    await wrapper.get('#question-input').setValue('关闭后直接解答')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()
    expect(learning.streamAsk).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('ignores duplicate request-hint emits when level 2 and 3 hints are already present in the current turn', async () => {
    learning.sessions.mockResolvedValueOnce([{ id: 20, title: '提示防重会话' }])
    learning.sessionMessages.mockResolvedValueOnce([
      { id: 1, role: 'USER', content: '数据流图怎么画？', mode: 'RANKED' },
      {
        id: 2,
        role: 'ASSISTANT',
        content: '第1级提示',
        mode: 'TUTOR_HINT_1',
        metadata: { hint: { question: '数据流图怎么画？', level: 1, content: '提示1', hasEvidence: false, evidence: [], nextLevelAvailable: true, source: 'MODEL' } },
      },
      {
        id: 3,
        role: 'ASSISTANT',
        content: '第2级提示',
        mode: 'TUTOR_HINT_2',
        metadata: { hint: { question: '数据流图怎么画？', level: 2, content: '提示2', hasEvidence: false, evidence: [], nextLevelAvailable: true, source: 'MODEL' } },
      },
      {
        id: 4,
        role: 'ASSISTANT',
        content: '第3级提示',
        mode: 'TUTOR_HINT_3',
        metadata: { hint: { question: '数据流图怎么画？', level: 3, content: '提示3', hasEvidence: false, evidence: [], nextLevelAvailable: false, source: 'MODEL' } },
      },
    ])

    const wrapper = await mountPage()
    await flushPromises()
    await wrapper.get('.tutor-session-select').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.tutor-hint-next-row button')).toHaveLength(0)

    const listComp = wrapper.findComponent({ name: 'TutorMessageList' })
    listComp.vm.$emit('request-hint', '数据流图怎么画？', 2, 1)
    listComp.vm.$emit('request-hint', '数据流图怎么画？', 3, 2)
    await flushPromises()

    expect(learning.tutorHint).not.toHaveBeenCalled()
    expect(wrapper.findAll('.tutor-message-row.is-assistant')).toHaveLength(3)
    wrapper.unmount()
  })

  it('forwards route context entities on streamAsk and tutorHint until dismissed', async () => {
    learning.tutorHint.mockResolvedValue({
      question: '加工如何命名？',
      level: 1,
      content: '思考加工命名',
      hasEvidence: false,
      evidence: [],
      nextLevelAvailable: true,
      source: 'MODEL',
    })

    const wrapper = await mountPage('/tutor?entity=加工&courseId=3')
    await flushPromises()

    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()
    expect(learning.streamAsk).toHaveBeenLastCalledWith(
      expect.any(String),
      'RANKED',
      expect.any(Function),
      expect.any(AbortSignal),
      expect.objectContaining({ entities: ['加工'] })
    )

    await wrapper.get('[data-testid="tutor-hint-toggle"]').trigger('click')
    await wrapper.get('#question-input').setValue('加工如何命名？')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()
    expect(learning.tutorHint).toHaveBeenLastCalledWith('加工如何命名？', 1, expect.any(Array), expect.any(Array), ['加工'])

    await wrapper.get('.tutor-context-close').trigger('click')
    await wrapper.get('#question-input').setValue('关闭标签后再问')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()
    expect(learning.tutorHint).toHaveBeenLastCalledWith('关闭标签后再问', 1, expect.any(Array), expect.any(Array))
    wrapper.unmount()
  })

  it('prefills question and quick prompts for point-only context on exact match without auto-submitting', async () => {
    learning.knowledgePoints.mockResolvedValueOnce([
      { id: 42, courseId: 1, chapterId: 2, name: '软件架构风格', graphEntity: '架构风格', sortOrder: 1, mastery: 80 },
    ])

    const wrapper = await mountPage('/tutor?courseId=1&knowledgePointId=42')
    await flushPromises()

    expect(learning.knowledgePoints).toHaveBeenCalledWith(1)
    expect(learning.streamAsk).not.toHaveBeenCalled()

    const input = wrapper.get('#question-input').element as HTMLTextAreaElement
    expect(input.value).toContain('软件架构风格')

    // 可编辑验证
    await wrapper.get('#question-input').setValue('软件架构风格有哪些具体类型？')
    expect((wrapper.get('#question-input').element as HTMLTextAreaElement).value).toBe('软件架构风格有哪些具体类型？')

    // quick prompts 包含解析后的知识点名称
    expect(wrapper.text()).toContain('软件架构风格')
    wrapper.unmount()
  })

  it('handles unknown point id gracefully without making up names or auto-submitting', async () => {
    learning.knowledgePoints.mockResolvedValueOnce([
      { id: 42, courseId: 1, chapterId: 2, name: '软件架构风格', graphEntity: '架构风格', sortOrder: 1, mastery: 80 },
    ])

    const wrapper = await mountPage('/tutor?courseId=1&knowledgePointId=999')
    await flushPromises()

    expect(learning.streamAsk).not.toHaveBeenCalled()
    const input = wrapper.get('#question-input').element as HTMLTextAreaElement
    expect(input.value).toBe('')
    wrapper.unmount()
  })

  it('handles knowledgePoints API failure gracefully without crashing or making up names', async () => {
    learning.knowledgePoints.mockRejectedValueOnce(new Error('Network error'))

    const wrapper = await mountPage('/tutor?courseId=1&knowledgePointId=42')
    await flushPromises()

    expect(learning.streamAsk).not.toHaveBeenCalled()
    const input = wrapper.get('#question-input').element as HTMLTextAreaElement
    expect(input.value).toBe('')
    wrapper.unmount()
  })

  it('prioritizes route.entity over knowledgePointId and does not overwrite existing question', async () => {
    learning.knowledgePoints.mockResolvedValueOnce([
      { id: 42, courseId: 1, chapterId: 2, name: '软件架构风格', graphEntity: '架构风格', sortOrder: 1, mastery: 80 },
    ])

    const wrapper = await mountPage('/tutor?entity=白盒测试&courseId=1&knowledgePointId=42')
    await flushPromises()

    expect(learning.streamAsk).not.toHaveBeenCalled()
    const input = wrapper.get('#question-input').element as HTMLTextAreaElement
    expect(input.value).toContain('白盒测试')
    expect(input.value).not.toContain('软件架构风格')
    wrapper.unmount()
  })

  it('auto-collapses session sidebar by default on narrower screens or context-driven entry', async () => {
    // 1280px 便携视口下默认折叠以保留宽敞对话空间
    window.innerWidth = 1280
    const narrowWrapper = await mountPage('/tutor')
    await flushPromises()
    expect(narrowWrapper.find('.tutor-sidebar-pane.is-collapsed').exists()).toBe(true)
    await narrowWrapper.get('[data-testid="tutor-expand-sidebar"]').trigger('click')
    expect(narrowWrapper.find('.tutor-sidebar-pane.is-collapsed').exists()).toBe(false)
    narrowWrapper.unmount()

    // 1440px 大屏但带课程/实体上下文跳转时默认折叠聚焦问答
    window.innerWidth = 1440
    const contextWrapper = await mountPage('/tutor?entity=需求分析')
    await flushPromises()
    expect(contextWrapper.find('.tutor-sidebar-pane.is-collapsed').exists()).toBe(true)
    contextWrapper.unmount()
  })
})
