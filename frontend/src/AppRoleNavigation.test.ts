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
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/teaching-dashboard', component: { template: '<div />' } }] })
    await router.push('/teaching-dashboard')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('管理工作台')
    expect(wrapper.text()).toContain('教学总览')
    expect(wrapper.text()).not.toContain('学习闭环')
    wrapper.unmount()
  })
})
