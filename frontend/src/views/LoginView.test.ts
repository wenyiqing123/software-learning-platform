import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import LoginView from './LoginView.vue'

const learning = vi.hoisted(() => ({ login: vi.fn(), register: vi.fn() }))
vi.mock('../api/client', () => ({ learningApi: learning }))

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/login', component: LoginView }, { path: '/', component: { template: '<div />' } }] })
  await router.push('/login'); await router.isReady()
  return mount(LoginView, { global: { plugins: [router] } })
}

describe('LoginView', () => {
  beforeEach(() => { vi.resetAllMocks() })

  it('presents the learning route without exposing credentials or static demo accounts', async () => {
    const wrapper = await mountPage()
    expect(wrapper.text()).toContain('把知识关系，变成下一步学习')
    expect(wrapper.text()).toContain('理解课程')
    expect(wrapper.text()).not.toMatch(/(password|token|sk-|管理员密码)/i)
    expect(wrapper.find('.login-page').exists()).toBe(true)
    expect(wrapper.find('.login-form').exists()).toBe(true)
    await wrapper.find('button[type="button"]').trigger('click')
    expect(wrapper.text()).toContain('创建学习账户')
    expect(wrapper.find('input[autocomplete="nickname"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
