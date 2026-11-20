import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FeedbackState from './FeedbackState.vue'

describe('FeedbackState', () => {
  it('exposes retry for errors and action for empty data', async () => {
    const wrapper = mount(FeedbackState, { props: { error: '服务不可用' } })
    expect(wrapper.text()).toContain('服务不可用')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
    await wrapper.setProps({ error: '', empty: true, emptyActionLabel: '去学习' })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('action')).toHaveLength(1)
  })
})
