import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ChangePasswordDialog from './ChangePasswordDialog.vue'

const authMocks = vi.hoisted(() => ({
  changePassword: vi.fn(),
  resetSession: vi.fn(),
  resetLearningMetrics: vi.fn(),
}))

vi.mock('../api/client', () => ({
  learningApi: {
    changePassword: authMocks.changePassword,
  },
  apiError: vi.fn((err: any, fallback: string) => err?.response?.data?.message || fallback),
}))

vi.mock('../state/session', () => ({
  resetSession: authMocks.resetSession,
}))

vi.mock('../composables/useLearningMetrics', () => ({
  resetLearningMetrics: authMocks.resetLearningMetrics,
}))

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>Home</div>' } },
      { path: '/login', component: { template: '<div>Login</div>' } },
    ],
  })
}

describe('ChangePasswordDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('rejects empty fields and shows validation error without sending request', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(ChangePasswordDialog, {
      global: { plugins: [router] },
      props: { modelValue: true },
    })

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(authMocks.changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请完整填写所有密码字段')
  })

  it('rejects mismatch between new password and confirm password', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(ChangePasswordDialog, {
      global: { plugins: [router] },
      props: { modelValue: true },
    })

    await wrapper.find('#current-password-input').setValue('oldPassword123')
    await wrapper.find('#new-password-input').setValue('newPassword123')
    await wrapper.find('#confirm-password-input').setValue('differentPass123')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(authMocks.changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('两次输入的新密码不一致')
  })

  it('rejects new password shorter than 8 characters', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(ChangePasswordDialog, {
      global: { plugins: [router] },
      props: { modelValue: true },
    })

    await wrapper.find('#current-password-input').setValue('oldPassword123')
    await wrapper.find('#new-password-input').setValue('short12')
    await wrapper.find('#confirm-password-input').setValue('short12')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(authMocks.changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('新密码长度至少需要 8 个字符')
  })

  it('handles 401 error gracefully without exposing password and clears current password while keeping new password', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()

    authMocks.changePassword.mockRejectedValueOnce({
      response: { status: 401, data: { message: '当前密码不正确' } },
    })

    const wrapper = mount(ChangePasswordDialog, {
      global: { plugins: [router] },
      props: { modelValue: true },
    })

    const currentInput = wrapper.find('#current-password-input')
    const newInput = wrapper.find('#new-password-input')
    const confirmInput = wrapper.find('#confirm-password-input')

    await currentInput.setValue('wrongOldPass123')
    await newInput.setValue('validNewPass123')
    await confirmInput.setValue('validNewPass123')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(authMocks.changePassword).toHaveBeenCalledWith({
      currentPassword: 'wrongOldPass123',
      newPassword: 'validNewPass123',
    })

    // 错误信息呈现且不回显明文密码
    const errorText = wrapper.find('.dialog-error').text()
    expect(errorText).toContain('当前密码不正确')
    expect(errorText).not.toContain('wrongOldPass123')
    expect(errorText).not.toContain('validNewPass123')

    // 当前旧密码被清空，新密码与确认密码保留
    expect((currentInput.element as HTMLInputElement).value).toBe('')
    expect((newInput.element as HTMLInputElement).value).toBe('validNewPass123')
    expect((confirmInput.element as HTMLInputElement).value).toBe('validNewPass123')
  })

  it('handles successful password change by clearing tokens, resetting session, and replacing route to login', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()

    localStorage.setItem('slp-access-token', 'test-access-token')
    localStorage.setItem('slp-refresh-token', 'test-refresh-token')
    localStorage.setItem('slp-user-name', 'testuser')
    localStorage.setItem('slp-user-role', 'STUDENT')

    authMocks.changePassword.mockResolvedValueOnce(undefined)

    const wrapper = mount(ChangePasswordDialog, {
      global: { plugins: [router] },
      props: { modelValue: true },
    })

    await wrapper.find('#current-password-input').setValue('currentPass123')
    await wrapper.find('#new-password-input').setValue('brandNewPass123')
    await wrapper.find('#confirm-password-input').setValue('brandNewPass123')

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(authMocks.changePassword).toHaveBeenCalledWith({
      currentPassword: 'currentPass123',
      newPassword: 'brandNewPass123',
    })

    // 校验 4 个 localStorage key 被清理
    expect(localStorage.getItem('slp-access-token')).toBeNull()
    expect(localStorage.getItem('slp-refresh-token')).toBeNull()
    expect(localStorage.getItem('slp-user-name')).toBeNull()
    expect(localStorage.getItem('slp-user-role')).toBeNull()

    // 校验状态清理方法被调用
    expect(authMocks.resetSession).toHaveBeenCalled()
    expect(authMocks.resetLearningMetrics).toHaveBeenCalled()

    // 校验导航至 login
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('cancels and closes without sending API requests', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(ChangePasswordDialog, {
      global: { plugins: [router] },
      props: { modelValue: true },
    })

    await wrapper.find('#current-password-input').setValue('draftPass123')
    const cancelBtn = wrapper.findAll('button').find(b => b.text().includes('取消'))
    expect(cancelBtn).toBeDefined()
    await cancelBtn!.trigger('click')

    expect(authMocks.changePassword).not.toHaveBeenCalled()
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })
})
