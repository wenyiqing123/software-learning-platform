import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ContentSkeleton from './ContentSkeleton.vue'

describe('ContentSkeleton', () => {
  it('renders accessible status semantics and variant-specific layout placeholders', () => {
    const reading = mount(ContentSkeleton, {
      props: { variant: 'reading', label: '正在加载课程内容' },
    })
    const status = reading.get('[data-testid="content-skeleton"]')
    expect(status.attributes('role')).toBe('status')
    expect(status.attributes('aria-busy')).toBe('true')
    expect(status.attributes('aria-label')).toBe('正在加载课程内容')
    expect(reading.find('.skeleton-reading-layout').exists()).toBe(true)
    expect(reading.findAll('.sk-outline-item')).toHaveLength(5)
    reading.unmount()

    const list = mount(ContentSkeleton, {
      props: { variant: 'list', rows: 4, label: '正在加载错题' },
    })
    expect(list.findAll('.skeleton-list-row')).toHaveLength(4)
    list.unmount()

    const flashcard = mount(ContentSkeleton, {
      props: { variant: 'flashcard', label: '正在加载闪卡' },
    })
    expect(flashcard.find('.skeleton-flashcard').exists()).toBe(true)
    flashcard.unmount()
  })
})
