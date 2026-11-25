import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TutorMessageList from './TutorMessageList.vue'

describe('TutorMessageList', () => {
  it('renders 2x2 welcome cards when message list is empty and emits quick-prompt', async () => {
    const wrapper = mount(TutorMessageList, {
      props: {
        messages: [],
        streamingAnswer: '',
        streamingEvidence: null,
        loading: false,
        error: '',
      },
    })

    expect(wrapper.text()).toContain('软件工程智能学习助教')
    const cards = wrapper.findAll('[data-testid="tutor-quick-prompt"]')
    expect(cards).toHaveLength(4)

    await cards[0].trigger('click')
    expect(wrapper.emitted('quick-prompt')?.[0]?.[0]).toContain('数据流图')
  })

  it('renders user messages, assistant markdown, collapsible evidence, and hides system prompt and mode labels', () => {
    const wrapper = mount(TutorMessageList, {
      props: {
        messages: [
          { id: 1, role: 'USER', content: '加工如何命名？', mode: 'RANKED' },
          {
            id: 2,
            role: 'ASSISTANT',
            content: '[TRIPLES] 加工命名使用动宾短语。',
            mode: 'TRIPLES',
            metadata: {
              results: [
                {
                  mode: 'RANKED',
                  answer: '加工命名使用动宾短语。',
                  prompt: 'hidden-system-prompt',
                  triples: [{ subject: '加工', predicate: '命名', object: '动宾短语' }],
                },
              ],
            },
          },
        ],
        streamingAnswer: '',
        streamingEvidence: null,
        loading: false,
        error: '',
      },
    })

    expect(wrapper.text()).toContain('加工如何命名？')
    expect(wrapper.text()).toContain('加工命名使用动宾短语。')
    expect(wrapper.text()).toContain('已参考 1 条课程图谱证据')
    expect(wrapper.text()).not.toContain('hidden-system-prompt')
    expect(wrapper.text()).not.toContain('RANKED')
    expect(wrapper.text()).not.toContain('TRIPLES')

    const evidenceDetails = wrapper.get('details.evidence-block')
    expect((evidenceDetails.element as HTMLDetailsElement).open).toBe(false)
  })

  it('emits a retry request locked to RANKED mode for failed question', async () => {
    const wrapper = mount(TutorMessageList, {
      props: {
        messages: [],
        streamingAnswer: '',
        streamingEvidence: null,
        loading: false,
        error: 'AI 服务暂不可用',
        retryQuestion: '解释数据流图',
        retryMode: 'TRIPLES',
      },
    })

    await wrapper.get('[data-testid="tutor-retry"]').trigger('click')

    expect(wrapper.emitted('retry')).toEqual([['解释数据流图', 'RANKED']])
  })
})
