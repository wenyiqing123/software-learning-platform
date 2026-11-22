import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TutorMessageList from './TutorMessageList.vue'

describe('TutorMessageList', () => {
  it('renders user messages, assistant markdown and real evidence', () => {
    const wrapper = mount(TutorMessageList, {
      props: {
        messages: [
          { id: 1, role: 'USER', content: '加工如何命名？', mode: 'RANKED' },
          {
            id: 2,
            role: 'ASSISTANT',
            content: '加工命名使用动宾短语。',
            mode: 'RANKED',
            metadata: {
              results: [{ mode: 'RANKED', answer: '加工命名使用动宾短语。', prompt: 'hidden-system-prompt', triples: [{ subject: '加工', predicate: '命名', object: '动宾短语' }] }],
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
    expect(wrapper.text()).toContain('图谱证据')
    expect(wrapper.text()).not.toContain('hidden-system-prompt')
  })

  it('emits a retry request for the failed question', async () => {
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

    expect(wrapper.emitted('retry')).toEqual([['解释数据流图', 'TRIPLES']])
  })
})
