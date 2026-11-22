import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TutorComposer from './TutorComposer.vue'

describe('TutorComposer', () => {
  const baseProps = {
    question: '',
    mode: 'RANKED',
    compareAll: false,
    loading: false,
    context: [{ label: '知识点', value: '数据流图' }],
    quickPrompts: ['解释这个概念', '给我一道练习题'],
    modes: [],
  }

  it('emits quick prompt, input and submit actions', async () => {
    const wrapper = mount(TutorComposer, { props: baseProps })

    await wrapper.get('[data-testid="tutor-quick-prompt"]').trigger('click')
    await wrapper.get('#question-input').setValue('请举一个例子')
    await wrapper.setProps({ question: '请举一个例子' })
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')

    expect(wrapper.emitted('quick-prompt')).toEqual([['解释这个概念']])
    expect(wrapper.emitted('update:question')?.at(-1)).toEqual(['请举一个例子'])
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('shows stop instead of send while loading', () => {
    const wrapper = mount(TutorComposer, { props: { ...baseProps, question: '正在提问', loading: true } })

    expect(wrapper.find('[data-testid="tutor-send"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="tutor-stop"]').exists()).toBe(true)
  })
})
