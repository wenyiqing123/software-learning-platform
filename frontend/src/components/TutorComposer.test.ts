import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TutorComposer from './TutorComposer.vue'

describe('TutorComposer', () => {
  const baseProps = {
    question: '',
    loading: false,
    hintLoading: false,
    context: [{ label: '知识点', value: '数据流图' }],
  }

  it('renders no mode selector or compare toggle and supports input and submit', async () => {
    const wrapper = mount(TutorComposer, { props: baseProps })

    expect(wrapper.find('.tutor-mode-selector').exists()).toBe(false)
    expect(wrapper.find('.compare-toggle').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('对比三种方式')
    expect(wrapper.text()).not.toContain('大模型直答')

    await wrapper.get('#question-input').setValue('请举一个例子')
    await wrapper.setProps({ question: '请举一个例子' })
    await wrapper.get('[data-testid="tutor-send"]').trigger('click')

    expect(wrapper.emitted('update:question')?.at(-1)).toEqual(['请举一个例子'])
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('prevents accidental submit during IME composition and allows Enter after composition ends', async () => {
    const wrapper = mount(TutorComposer, { props: { ...baseProps, question: '需求分析' } })
    const textarea = wrapper.get('#question-input')

    // isComposing 属性为 true 时不触发发送
    await textarea.trigger('keydown.enter', { isComposing: true })
    expect(wrapper.emitted('submit')).toBeUndefined()

    // compositionstart 到 compositionend 之间不触发发送
    await textarea.trigger('compositionstart')
    await textarea.trigger('keydown.enter')
    expect(wrapper.emitted('submit')).toBeUndefined()

    await textarea.trigger('compositionend')
    await textarea.trigger('keydown.enter')
    expect(wrapper.emitted('submit')).toHaveLength(1)

    // Shift + Enter 换行不发送
    await textarea.trigger('keydown.enter', { shiftKey: true })
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('shows stop button instead of send while loading', () => {
    const wrapper = mount(TutorComposer, { props: { ...baseProps, question: '正在提问', loading: true } })

    expect(wrapper.find('[data-testid="tutor-send"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="tutor-stop"]').exists()).toBe(true)
  })

  it('renders step-hint switch control cleanly and emits toggle + dismiss-context actions', async () => {
    const wrapper = mount(TutorComposer, { props: { ...baseProps, question: '', stepHintEnabled: false } })

    const hintSwitch = wrapper.get('[data-testid="tutor-hint-toggle"]')
    expect(hintSwitch.attributes('role')).toBe('switch')
    expect(hintSwitch.attributes('aria-checked')).toBe('false')
    expect(hintSwitch.attributes('disabled')).toBeUndefined()

    await hintSwitch.trigger('click')
    expect(wrapper.emitted('update:stepHintEnabled')).toEqual([[true]])

    await wrapper.setProps({ stepHintEnabled: true })
    expect(hintSwitch.attributes('aria-checked')).toBe('true')
    expect(hintSwitch.classes()).toContain('is-active')

    await hintSwitch.trigger('click')
    expect(wrapper.emitted('update:stepHintEnabled')).toEqual([[true], [false]])

    await wrapper.get('.tutor-context-close').trigger('click')
    expect(wrapper.emitted('dismiss-context')).toHaveLength(1)
  })
})
