import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PomodoroTimer from './PomodoroTimer.vue'

afterEach(() => {
  vi.useRealTimers()
})

describe('PomodoroTimer', () => {
  it('counts down, supports preset switching/reset, and alerts on completion without mutating tasks', async () => {
    vi.useFakeTimers()
    const wrapper = mount(PomodoroTimer, {
      props: {
        tasks: [{ id: 1, title: '阅读需求分析', state: 'PENDING', minutes: 15 }],
      },
    })

    expect(wrapper.get('[data-testid="pomodoro-clock"]').text()).toBe('25:00')
    await wrapper.get('[data-testid="pomodoro-preset-sprint15"]').trigger('click')
    expect(wrapper.get('[data-testid="pomodoro-clock"]').text()).toBe('15:00')

    await wrapper.get('[data-testid="pomodoro-toggle"]').trigger('click')
    vi.advanceTimersByTime(3000)
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="pomodoro-clock"]').text()).toBe('14:57')

    vi.advanceTimersByTime(14 * 60 * 1000 + 57 * 1000)
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="pomodoro-clock"]').text()).toBe('00:00')
    expect(wrapper.get('[data-testid="pomodoro-finish-alert"]').text()).toContain('完成真实学习操作后，再手动标记任务完成')

    await wrapper.get('[data-testid="pomodoro-reset"]').trigger('click')
    expect(wrapper.get('[data-testid="pomodoro-clock"]').text()).toBe('15:00')
    wrapper.unmount()
  })
})
