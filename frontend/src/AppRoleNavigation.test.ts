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
    expect(wrapper.find('.student-sections-strip').exists()).toBe(false)
    expect(wrapper.findAll('.nav-sublist').length).toBeGreaterThan(0)
    expect(wrapper.findAll('.nav-subitem').length).toBeGreaterThan(0)
    wrapper.unmount()
  })

  it('auto-collapses main sidebar on /tutor, hides help box, supports manual toggle and hover flyout submenus', async () => {
    session.user.value = { id: 2, username: 'student', displayName: '学生', role: 'STUDENT' }
    const routes = [
      { path: '/dashboard', component: { template: '<div />' } },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/tutor', component: { template: '<div />' } },
      { path: '/practice', component: { template: '<div />' } },
      { path: '/study-plan', component: { template: '<div />' } },
      { path: '/experiment-tasks', component: { template: '<div />' } },
    ]
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tutor')
    await router.isReady()
    const wrapper = mount(App, { attachTo: document.body, global: { plugins: [router] } })
    await flushPromises()

    try {
      expect(wrapper.find('.app-shell.is-sidebar-collapsed').exists()).toBe(true)
      expect(wrapper.find('.help-box').exists()).toBe(false)
      expect(wrapper.find('.student-sections-strip').exists()).toBe(false)

      const firstNavItem = wrapper.find('.primary-nav .nav-item')
      await firstNavItem.trigger('mouseenter')
      await flushPromises()

      const flyout = document.body.querySelector('[data-testid="sidebar-flyout-menu"]')
      expect(flyout).not.toBeNull()
      expect(flyout?.querySelectorAll('.nav-flyout-item').length ?? 0).toBeGreaterThan(0)

      await wrapper.get('[data-testid="sidebar-collapse-toggle"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('.app-shell.is-sidebar-collapsed').exists()).toBe(false)

      await wrapper.get('[data-testid="sidebar-collapse-toggle"]').trigger('click')
      await flushPromises()
      expect(wrapper.find('.app-shell.is-sidebar-collapsed').exists()).toBe(true)

      await router.push('/dashboard')
      await flushPromises()
      expect(wrapper.find('.app-shell.is-sidebar-collapsed').exists()).toBe(false)
    } finally {
      wrapper.unmount()
    }
  })

  it('syncs clickable breadcrumbs with active menu hierarchy and switches pages directly', async () => {
    session.user.value = { id: 2, username: 'student', displayName: '学生', role: 'STUDENT' }
    const routes = [
      { path: '/dashboard', component: { template: '<div />' } },
      { path: '/courses', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/mastery-map', component: { template: '<div />' } },
      { path: '/tutor', component: { template: '<div />' } },
      { path: '/micro-lessons', component: { template: '<div />' } },
    ]
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/tutor?courseId=3')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router] } })
    await flushPromises()

    const breadcrumb = wrapper.get('.breadcrumb')
    expect(breadcrumb.text()).toContain('学习空间')
    expect(breadcrumb.text()).toContain('图谱与助教')
    expect(breadcrumb.text()).toContain('AI 学习助教')

    await wrapper.get('[data-testid="breadcrumb-page-switcher"]').trigger('click')
    await flushPromises()
    const menu = wrapper.get('[data-testid="breadcrumb-menu"]')
    expect(menu.text()).toContain('知识图谱')
    expect(menu.text()).toContain('掌握图谱')
    expect(menu.text()).toContain('证据微课')

    const knowledgeLink = menu.findAll('a').find(link => link.text().includes('知识图谱'))
    expect(knowledgeLink).toBeDefined()
    await knowledgeLink!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/knowledge')
    expect(wrapper.get('.breadcrumb').text()).toContain('知识图谱')
    expect(wrapper.find('[data-testid="breadcrumb-menu"]').exists()).toBe(false)

    await router.push('/courses')
    await flushPromises()
    expect(wrapper.get('.breadcrumb').text()).toContain('概览与课程')
    expect(wrapper.get('.breadcrumb').text()).toContain('课程阅读')

    await wrapper.get('.breadcrumb-root').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/dashboard')
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

  it('closes topbar status and user menus when clicking outside or switching between them', async () => {
    session.user.value = { id: 1, username: 'admin', displayName: '课程管理员', role: 'ADMIN' }
    const routes = [{ path: '/teaching-dashboard', component: { template: '<div data-testid="outside-area" />' } }]
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/teaching-dashboard')
    await router.isReady()
    const wrapper = mount(App, { attachTo: document.body, global: { plugins: [router] } })
    await flushPromises()

    await wrapper.get('.system-status-button').trigger('click')
    expect(wrapper.find('.system-status-panel').exists()).toBe(true)

    // 点击面板内部不会误关闭
    await wrapper.get('.system-status-panel .system-status-heading').trigger('click')
    expect(wrapper.find('.system-status-panel').exists()).toBe(true)

    // 点击页面其他区域立刻关闭系统依赖状态弹窗
    await wrapper.get('[data-testid="outside-area"]').trigger('click')
    expect(wrapper.find('.system-status-panel').exists()).toBe(false)

    // 再次打开依赖弹窗，再点击用户菜单，两者应互斥关闭并切为用户菜单
    await wrapper.get('.system-status-button').trigger('click')
    expect(wrapper.find('.system-status-panel').exists()).toBe(true)
    await wrapper.get('.user-menu').trigger('click')
    expect(wrapper.find('.system-status-panel').exists()).toBe(false)
    expect(wrapper.find('.user-menu-dropdown').exists()).toBe(true)

    // 点击用户菜单外页面区域立刻关闭用户菜单弹窗
    await wrapper.get('[data-testid="outside-area"]').trigger('click')
    expect(wrapper.find('.user-menu-dropdown').exists()).toBe(false)

    wrapper.unmount()
  })
})
