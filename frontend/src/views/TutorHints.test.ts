import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TutorView from './TutorView.vue'

const learning = vi.hoisted(() => ({
  sessions: vi.fn(),
  tutorHint: vi.fn(),
  saveMessage: vi.fn(),
  createSession: vi.fn(),
  sessionMessages: vi.fn(),
  deleteSession: vi.fn(),
  streamAsk: vi.fn(),
  compare: vi.fn(),
}))
vi.mock('../api/client', () => ({ learningApi: learning, apiError: (_e: unknown, fallback: string) => fallback }))

async function mountTutor() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/tutor', component: TutorView }] })
  await router.push('/tutor')
  await router.isReady()
  return mount(TutorView, { global: { plugins: [router] } })
}

beforeEach(() => {
  vi.resetAllMocks()
  localStorage.removeItem('slp-tutor-step-hint-sessions')
  learning.sessions.mockResolvedValue([])
  learning.createSession.mockResolvedValue({ id: 1, title: '提示会话' })
  learning.tutorHint.mockResolvedValue({
    question: '加工如何命名',
    level: 1,
    content: '思考方向：先定位处理对象和关系。',
    hasEvidence: true,
    evidence: [{ subject: '加工', predicate: '命名', object: '动宾短语', score: 0.9 }],
    nextLevelAvailable: true,
    source: 'GRAPH_DIRECTION',
  })
  learning.saveMessage.mockImplementation(async (_id: number, payload: any) => ({ id: Date.now(), ...payload }))
})

describe('tutor layered hints', () => {
  it('enables session step-hint toggle and routes submitted questions through hint flow without exposing a prompt', async () => {
    const wrapper = await mountTutor()
    await flushPromises()

    const hintToggle = wrapper.get('[data-testid="tutor-hint-toggle"]')
    await hintToggle.trigger('click')
    expect(hintToggle.attributes('aria-checked')).toBe('true')
    expect(learning.tutorHint).not.toHaveBeenCalled()

    await wrapper.get('#question-input').setValue('加工如何命名')
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')
    await flushPromises()

    expect(learning.streamAsk).not.toHaveBeenCalled()
    expect(learning.tutorHint).toHaveBeenCalledWith('加工如何命名', 1, [], [])
    expect(wrapper.text()).toContain('分步学习提示 · 第 1 级')
    expect(wrapper.text()).toContain('思考方向')
    expect(wrapper.text()).toContain('动宾短语')
    expect(wrapper.text()).toContain('展开第 2 级提示')
    expect(wrapper.text()).not.toContain('查看本次提示词')
    wrapper.unmount()
  })
})
