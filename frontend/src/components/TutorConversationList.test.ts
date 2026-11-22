import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import TutorConversationList from './TutorConversationList.vue'

describe('TutorConversationList', () => {
  it('emits session actions without owning persistence', async () => {
    const wrapper = mount(TutorConversationList, {
      props: {
        sessions: [{ id: 7, title: '需求分析' }],
        activeId: 7,
      },
    })

    await wrapper.get('[data-testid="tutor-new-session"]').trigger('click')
    await wrapper.get('[data-testid="tutor-rename-7"]').trigger('click')
    await wrapper.get('[data-testid="tutor-rename-input"]').setValue('数据流图复习')
    await wrapper.get('form.tutor-session-rename').trigger('submit')
    await nextTick()
    await wrapper.get('[data-testid="tutor-delete-7"]').trigger('click')

    expect(wrapper.emitted('create')).toHaveLength(1)
    expect(wrapper.emitted('rename')?.[0]).toEqual([7, '数据流图复习'])
    expect(wrapper.emitted('remove')?.[0]).toEqual([7])
  })

  it('shows an honest empty state when there are no sessions', () => {
    const wrapper = mount(TutorConversationList, { props: { sessions: [], activeId: null } })

    expect(wrapper.text()).toContain('还没有学习会话')
    expect(wrapper.get('[data-testid="tutor-new-session"]').exists()).toBe(true)
  })
})
