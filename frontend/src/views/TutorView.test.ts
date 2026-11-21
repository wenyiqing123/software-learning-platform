import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import TutorView from './TutorView.vue'

const learning = vi.hoisted(() => ({ sessions: vi.fn(), createSession: vi.fn(), saveMessage: vi.fn(), streamAsk: vi.fn(), sessionMessages: vi.fn(), tutorHint: vi.fn(), deleteSession: vi.fn(), compare: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: learning, apiError: vi.fn(() => '请求失败') }))

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/tutor', component: TutorView }] })
  await router.push('/tutor'); await router.isReady()
  return mount(TutorView, { global: { plugins: [router] } })
}

describe('TutorView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    learning.sessions.mockResolvedValue([])
    learning.createSession.mockResolvedValue({ id: 1, title: '学习会话' })
    learning.saveMessage.mockImplementation(async (_id: number, payload: any) => ({ id: Date.now(), ...payload }))
    learning.streamAsk.mockImplementation(async (_question: string, _mode: string, onEvent: (name: string, payload: any) => void) => { onEvent('knowledge', { triples: [{ subject: '加工', predicate: '命名', object: '动宾短语' }] }); onEvent('delta', { content: '加工命名使用动宾短语。' }); return { mode: 'RANKED', answer: '加工命名使用动宾短语。', prompt: '[GROUNDED_ANSWER_V3] hidden' } })
  })

  it('shows streaming answer state but keeps the ranked system prompt private', async () => {
    const wrapper = await mountPage(); await flushPromises()
    await wrapper.get('#question-input').setValue('加工如何命名？')
    await wrapper.get('button.button-dark').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('加工命名使用动宾短语')
    expect(wrapper.text()).not.toContain('GROUNDED_ANSWER_V3')
    expect(wrapper.text()).not.toContain('hidden')
    wrapper.unmount()
  })
})
