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
    learning.tutorHint.mockResolvedValueOnce({
      question: '如何划分用例？',
      level: 1,
      content: '思考参与者与系统的交互目标。',
      hasEvidence: false,
      evidence: [],
      nextLevelAvailable: true,
      source: 'MODEL',
    })

    const wrapper = await mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('初次会话')

    // 侧栏收起与展开
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

    // 胶囊内置“分步提示”按钮触发分级提示进对话流
    await wrapper.get('#question-input').setValue('如何划分用例？')
    await wrapper.get('[data-testid="tutor-hint-trigger"]').trigger('click')
    await flushPromises()

    expect(learning.tutorHint).toHaveBeenCalledWith('如何划分用例？', 1, expect.any(Array), expect.any(Array))
    expect(wrapper.text()).toContain('思考参与者与系统的交互目标。')
    expect(wrapper.text()).toContain('分步学习提示 · 第 1 级')
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
})
