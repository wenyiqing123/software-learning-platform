import { beforeEach, describe, expect, it, vi } from 'vitest'
import router from './router'

const learning = vi.hoisted(() => ({ currentUser: vi.fn() }))
vi.mock('./api/client', () => ({ learningApi: learning }))

beforeEach(async () => {
  localStorage.clear()
  learning.currentUser.mockReset()
  await router.push('/login')
})

describe('role-aware router', () => {
  it('lands students on dashboard and blocks admin pages', async () => {
    localStorage.setItem('slp-access-token', 'student-token')
    localStorage.setItem('slp-user-role', 'STUDENT')
    await router.push('/admin/content')
    expect(router.currentRoute.value.path).toBe('/dashboard')
    expect(router.currentRoute.value.query.notice).toBe('student')
  })

  it('lands administrators on teaching dashboard and blocks student pages', async () => {
    localStorage.setItem('slp-access-token', 'admin-token')
    localStorage.setItem('slp-user-role', 'ADMIN')
    await router.push('/practice')
    expect(router.currentRoute.value.path).toBe('/teaching-dashboard')
    expect(router.currentRoute.value.query.notice).toBe('admin')
  })

  it('uses the current account role when no cached role exists', async () => {
    localStorage.setItem('slp-access-token', 'admin-token')
    learning.currentUser.mockResolvedValue({ role: 'ADMIN', username: 'admin', displayName: '管理员' })
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/teaching-dashboard')
    expect(localStorage.getItem('slp-user-role')).toBe('ADMIN')
  })
})
