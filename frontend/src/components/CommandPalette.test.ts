import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CommandPalette from './CommandPalette.vue'

const mocks = vi.hoisted(() => ({
  knowledgePoints: vi.fn(),
  notes: vi.fn(),
  sessions: vi.fn(),
}))

vi.mock('../api/client', () => ({
  learningApi: {
    knowledgePoints: mocks.knowledgePoints,
    notes: mocks.notes,
    sessions: mocks.sessions,
  },
}))

describe('CommandPalette', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.knowledgePoints.mockResolvedValue([
      { id: 101, courseId: 1, chapterId: 1, name: '依赖倒置原则', graphEntity: 'DependencyInversionPrinciple', description: '高层模块不应依赖低层模块', sortOrder: 1, mastery: 0.72 },
    ])
    mocks.notes.mockResolvedValue([
      { id: 12, title: '架构权衡笔记', content: '微服务通信成本对比', tags: ['架构', '总结'], createdAt: '', updatedAt: '' },
    ])
    mocks.sessions.mockResolvedValue([
      { id: 7, title: '关于耦合度与内聚度的讨论' },
    ])
  })

  it('lazy-loads student items only when opened, filters matches, and navigates on Enter', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/dashboard', component: { template: '<div />' } },
        { path: '/courses', component: { template: '<div />' } },
        { path: '/notes', component: { template: '<div />' } },
        { path: '/tutor', component: { template: '<div />' } },
      ],
    })
    await router.push('/dashboard')
    await router.isReady()

    const wrapper = mount(CommandPalette, {
      props: { open: false, role: 'STUDENT', isDark: false },
      attachTo: document.body,
      global: { plugins: [router] },
    })

    expect(mocks.knowledgePoints).not.toHaveBeenCalled()

    await wrapper.setProps({ open: true })
    await flushPromises()

    expect(mocks.knowledgePoints).toHaveBeenCalledTimes(1)
    expect(mocks.notes).toHaveBeenCalledTimes(1)
    expect(mocks.sessions).toHaveBeenCalledTimes(1)

    const input = document.body.querySelector('[data-testid="command-palette-input"]') as HTMLInputElement
    expect(input).not.toBeNull()
    input.value = '依赖倒置'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    const options = document.body.querySelectorAll('.command-palette-item')
    expect(options.length).toBeGreaterThan(0)
    expect(options[0].textContent).toContain('依赖倒置原则')

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/courses')
    expect(router.currentRoute.value.query.knowledgePointId).toBe('101')
    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])

    wrapper.unmount()
  })

  it('closes on capture Escape and emits toggle-theme when selecting theme command', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/teaching-dashboard', component: { template: '<div />' } }],
    })
    await router.push('/teaching-dashboard')
    await router.isReady()

    const outerListener = vi.fn()
    window.addEventListener('keydown', outerListener)

    const wrapper = mount(CommandPalette, {
      props: { open: true, role: 'ADMIN', isDark: false },
      attachTo: document.body,
      global: { plugins: [router] },
    })
    await flushPromises()

    expect(mocks.knowledgePoints).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('教学总览')

    const input = document.body.querySelector('[data-testid="command-palette-input"]') as HTMLInputElement
    input.value = '深色'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    const themeOption = document.body.querySelector('.command-palette-item') as HTMLButtonElement
    themeOption.click()
    await flushPromises()
    expect(wrapper.emitted('toggle-theme')).toBeTruthy()
    expect(wrapper.emitted('update:open')?.some(payload => payload[0] === false)).toBe(true)

    await wrapper.setProps({ open: true })
    await flushPromises()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(outerListener).not.toHaveBeenCalled()

    window.removeEventListener('keydown', outerListener)
    wrapper.unmount()
  })

  it('navigates to Feynman self-check activity via action query', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/dashboard', component: { template: '<div />' } },
        { path: '/tutor', component: { template: '<div />' } },
      ],
    })
    await router.push('/dashboard')
    await router.isReady()

    const wrapper = mount(CommandPalette, {
      props: { open: true, role: 'STUDENT', isDark: false },
      attachTo: document.body,
      global: { plugins: [router] },
    })
    await flushPromises()

    const input = document.body.querySelector('[data-testid="command-palette-input"]') as HTMLInputElement
    input.value = '费曼'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/tutor')
    expect(router.currentRoute.value.query.activity).toBe('feynman')
    wrapper.unmount()
  })
})
