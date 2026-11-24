import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from './App.vue'

const session = vi.hoisted(() => ({
  user: { value: { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' } },
  loading: { value: false },
  error: { value: '' },
  refresh: vi.fn(),
}))
vi.mock('./state/session', () => ({ useSession: () => session, resetSession: vi.fn() }))
vi.mock('./composables/useLearningMetrics', () => ({
  useLearningMetrics: () => ({ overview: { value: null }, loading: { value: false }, error: { value: '' }, reload: vi.fn() }),
  resetLearningMetrics: vi.fn(),
}))
vi.mock('./composables/useSystemStatus', () => ({
  useSystemStatus: () => ({ status: { value: null }, loading: { value: false }, error: { value: '' }, reload: vi.fn() }),
}))
vi.mock('./api/client', () => ({ learningApi: { logout: vi.fn() } }))

describe('App role navigation', () => {
  it('renders the administrator navigation instead of an empty sidebar', async () => {
    session.user.value = { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' }
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/teaching-dashboard', component: { template: '<div />' } }] })
    await router.push('/teaching-dashboard')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('管理工作台')
    expect(wrapper.text()).toContain('教学总览')
    expect(wrapper.text()).not.toContain('学习闭环')
    expect(wrapper.text()).not.toContain('概览与课程')
    expect(wrapper.find('.student-sections-strip').exists()).toBe(false)
    const navItems = wrapper.findAll('.primary-nav .nav-item')
    expect(navItems).toHaveLength(3)
    wrapper.unmount()
  })

  it('renders exactly five primary navigation items for student and does not render admin items', async () => {
    session.user.value = { id: 2, username: 'student', displayName: '学生', role: 'STUDENT' }
    const routes = [
      { path: '/dashboard', component: { template: '<div />' } },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/practice', component: { template: '<div />' } },
      { path: '/study-plan', component: { template: '<div />' } },
      { path: '/experiment-tasks', component: { template: '<div />' } },
    ]
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/dashboard')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router] } })
    await flushPromises()

    const navItems = wrapper.findAll('.primary-nav .nav-item')
    expect(navItems).toHaveLength(5)
    expect(wrapper.text()).toContain('概览与课程')
    expect(wrapper.text()).toContain('图谱与助教')
    expect(wrapper.text()).toContain('练习与复习')
    expect(wrapper.text()).toContain('计划与推荐')
    expect(wrapper.text()).toContain('实验与评估')
    expect(wrapper.text()).not.toContain('管理工作台')
    expect(wrapper.text()).not.toContain('教学总览')
    expect(wrapper.find('.student-sections-strip').exists()).toBe(true)
    wrapper.unmount()
  })

  it('highlights the corresponding top-level group for each subroute', async () => {
    session.user.value = { id: 2, username: 'student', displayName: '学生', role: 'STUDENT' }
    const routes = [
      { path: '/dashboard', component: { template: '<div />' } },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/mastery-map', component: { template: '<div />' } },
      { path: '/tutor', component: { template: '<div />' } },
      { path: '/micro-lessons', component: { template: '<div />' } },
      { path: '/practice', component: { template: '<div />' } },
      { path: '/adaptive-practice', component: { template: '<div />' } },
      { path: '/wrong-book', component: { template: '<div />' } },
      { path: '/review', component: { template: '<div />' } },
      { path: '/study-plan', component: { template: '<div />' } },
      { path: '/recommendations', component: { template: '<div />' } },
      { path: '/experiment-tasks', component: { template: '<div />' } },
      { path: '/evaluation', component: { template: '<div />' } },
      { path: '/learning-report', component: { template: '<div />' } },
    ]
    const testCases = [
      { route: '/courses', expectedActiveIndex: 0, expectedLabel: '概览与课程' },
      { route: '/tutor', expectedActiveIndex: 1, expectedLabel: '图谱与助教' },
      { route: '/review', expectedActiveIndex: 2, expectedLabel: '练习与复习' },
      { route: '/recommendations', expectedActiveIndex: 3, expectedLabel: '计划与推荐' },
      { route: '/evaluation', expectedActiveIndex: 4, expectedLabel: '实验与评估' },
    ]

    for (const { route, expectedActiveIndex, expectedLabel } of testCases) {
      const router = createRouter({ history: createMemoryHistory(), routes })
      await router.push(route)
      await router.isReady()
      const wrapper = mount(App, { global: { plugins: [router] } })
      await flushPromises()

      const navItems = wrapper.findAll('.primary-nav .nav-item')
      expect(navItems).toHaveLength(5)
      expect(navItems[expectedActiveIndex].classes()).toContain('active')
      expect(navItems[expectedActiveIndex].attributes('aria-current')).toBe('page')
      expect(navItems[expectedActiveIndex].text()).toContain(expectedLabel)

      // 验证其他项不是 active
      navItems.forEach((item, idx) => {
        if (idx !== expectedActiveIndex) {
          expect(item.classes()).not.toContain('active')
          expect(item.attributes('aria-current')).toBeUndefined()
        }
      })
      wrapper.unmount()
    }
  })
})
