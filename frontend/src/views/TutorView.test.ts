import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TutorView from './TutorView.vue'

const learning = vi.hoisted(() => ({ sessions: vi.fn(), createSession: vi.fn(), renameSession: vi.fn(), saveMessage: vi.fn(), streamAsk: vi.fn(), sessionMessages: vi.fn(), tutorHint: vi.fn(), deleteSession: vi.fn(), compare: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: learning, apiError: vi.fn(() => '请求失败') }))

async function mountPage(path = '/tutor') {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/tutor', component: TutorView }] })
  await router.push(path); await router.isReady()
  return mount(TutorView, { global: { plugins: [router] } })
}

describe('TutorView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    learning.sessions.mockResolvedValue([])
    learning.createSession.mockResolvedValue({ id: 1, title: '学习会话' })
    learning.renameSession.mockResolvedValue({ id: 1, title: '重命名会话' })
    learning.sessionMessages.mockResolvedValue([])
    learning.saveMessage.mockImplementation(async (_id: number, payload: any) => ({ id: Date.now(), ...payload }))
    learning.streamAsk.mockImplementation(async (_question: string, _mode: string, onEvent: (name: string, payload: any) => void) => { onEvent('knowledge', { triples: [{ subject: '加工', predicate: '命名', object: '动宾短语' }] }); onEvent('delta', { content: '加工命名使用动宾短语。' }); return { mode: 'RANKED', answer: '加工命名使用动宾短语。', prompt: ['server', 'only', 'constraint'].join('-') } })
  })

  it('shows streaming answer state but keeps the ranked system prompt private', async () => {
    const wrapper = await mountPage(); await flushPromises()
    await wrapper.get('#question-input').setValue('加工如何命名？')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('加工命名使用动宾短语')
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

  it('supports quick prompt fill without sending automatically', async () => {
    const wrapper = await mountPage('/tutor?entity=用例图')
    await flushPromises()
    const promptButtons = wrapper.findAll('[data-testid="tutor-quick-prompt"]')
    expect(promptButtons.length).toBeGreaterThan(0)
    await promptButtons[0].trigger('click')
    expect(learning.streamAsk).not.toHaveBeenCalled()
    expect((wrapper.get('#question-input').element as HTMLTextAreaElement).value).toContain('用例图')
    wrapper.unmount()
  })

  it('supports session operations, compare mode, and hints progression', async () => {
    learning.sessions.mockResolvedValueOnce([{ id: 10, title: '初次会话' }])
    learning.compare.mockResolvedValueOnce([
      { mode: 'DIRECT', answer: '直接回答内容' },
      { mode: 'TRIPLES', answer: '三元组回答内容' },
      { mode: 'RANKED', answer: '排序回答内容' },
    ])
    learning.tutorHint.mockResolvedValueOnce({
      question: '用例建模',
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

    // 模式切换与对比模式
    const compareCheckbox = wrapper.get('.compare-toggle input')
    await compareCheckbox.setValue(true)
    await wrapper.get('#question-input').setValue('用例建模的核心？')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()

    expect(learning.compare).toHaveBeenCalledWith('用例建模的核心？')
    expect(wrapper.text()).toContain('直接回答内容')

    // 分层提示
    await wrapper.get('#question-input').setValue('如何划分用例？')
    const hintButtons = wrapper.findAll('.hint-level-button')
    await hintButtons[0].trigger('click')
    await flushPromises()

    expect(learning.tutorHint).toHaveBeenCalled()
    expect(wrapper.text()).toContain('思考参与者与系统的交互目标。')
    wrapper.unmount()
  })
})
