import { beforeEach, describe, expect, it, vi } from 'vitest'
import router, { clearRoleCache } from './router'

const learning = vi.hoisted(() => ({ currentUser: vi.fn() }))
vi.mock('./api/client', () => ({ learningApi: learning }))

const STUDENT_ROUTES = [
  '/dashboard',
  '/courses',
  '/knowledge',
  '/mastery-map',
  '/tutor',
  '/practice',
  '/adaptive-practice',
  '/wrong-book',
  '/review',
  '/study-plan',
  '/micro-lessons',
  '/evaluation',
  '/learning-report',
  '/recommendations',
  '/experiment-tasks',
]

const ADMIN_CANONICAL_ROUTES = [
  '/teaching-dashboard',
  '/admin/content',
  '/admin/experiments',
  '/experiments',
]

const ADMIN_LEGACY_ROUTES = [
  '/admin',
  '/admin/teaching-dashboard',
  '/admin/question-bank',
  '/admin/micro-lessons',
  '/admin/diagnostics',
  '/admin/experiment-tasks',
  '/admin/evaluations',
]

beforeEach(async () => {
  localStorage.clear()
  clearRoleCache()
  learning.currentUser.mockReset()
  await router.push('/login')
})

describe('role-aware router', () => {
  it('lands students on dashboard and blocks admin pages', async () => {
    localStorage.setItem('slp-access-token', 'student-token')
    localStorage.setItem('slp-user-role', 'STUDENT')
    learning.currentUser.mockResolvedValue({ role: 'STUDENT', username: 'student' })
    await router.push('/admin/content')
    expect(router.currentRoute.value.path).toBe('/dashboard')
    expect(router.currentRoute.value.query.notice).toBe('student')
  })

  it('lands administrators on teaching dashboard and blocks student pages', async () => {
    localStorage.setItem('slp-access-token', 'admin-token')
    localStorage.setItem('slp-user-role', 'ADMIN')
    learning.currentUser.mockResolvedValue({ role: 'ADMIN', username: 'admin' })
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

  it('redirects unauthenticated users to /login and allows /login without token', async () => {
    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/login')
    await router.push('/teaching-dashboard')
    expect(router.currentRoute.value.path).toBe('/login')
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('redirects authenticated users on /login to their role home', async () => {
    localStorage.setItem('slp-access-token', 'student-token')
    learning.currentUser.mockResolvedValue({ role: 'STUDENT', username: 'student' })
    await router.push({ path: '/login', query: { auth: '1' } })
    expect(router.currentRoute.value.path).toBe('/dashboard')

    clearRoleCache()
    localStorage.setItem('slp-access-token', 'admin-token')
    learning.currentUser.mockResolvedValue({ role: 'ADMIN', username: 'admin' })
    await router.push({ path: '/login', query: { auth: '2' } })
    expect(router.currentRoute.value.path).toBe('/teaching-dashboard')
  })

  it('parameterized matrix: redirects student from all admin canonical routes', async () => {
    localStorage.setItem('slp-access-token', 'student-token')
    learning.currentUser.mockResolvedValue({ role: 'STUDENT', username: 'student' })

    for (const route of ADMIN_CANONICAL_ROUTES) {
      await router.push(route)
      expect(router.currentRoute.value.path).toBe('/dashboard')
      expect(router.currentRoute.value.query.notice).toBe('student')
    }
  })

  it('parameterized matrix: redirects student from all admin legacy routes', async () => {
    localStorage.setItem('slp-access-token', 'student-token')
    learning.currentUser.mockResolvedValue({ role: 'STUDENT', username: 'student' })

    for (const route of ADMIN_LEGACY_ROUTES) {
      await router.push(route)
      expect(router.currentRoute.value.path).toBe('/dashboard')
      expect(router.currentRoute.value.query.notice).toBe('student')
    }
  })

  it('parameterized matrix: redirects admin from all 15 student routes', async () => {
    localStorage.setItem('slp-access-token', 'admin-token')
    learning.currentUser.mockResolvedValue({ role: 'ADMIN', username: 'admin' })

    for (const route of STUDENT_ROUTES) {
      await router.push(route)
      expect(router.currentRoute.value.path).toBe('/teaching-dashboard')
      expect(router.currentRoute.value.query.notice).toBe('admin')
    }
  })

  it('preserves query parameters on legacy admin route redirects for admin', async () => {
    localStorage.setItem('slp-access-token', 'admin-token')
    learning.currentUser.mockResolvedValue({ role: 'ADMIN', username: 'admin' })

    const expectations: Array<{ from: string; query: Record<string, string>; targetPath: string; expectedQuery: Record<string, string> }> = [
      { from: '/admin', query: { ref: 'mail' }, targetPath: '/teaching-dashboard', expectedQuery: { ref: 'mail' } },
      { from: '/admin/teaching-dashboard', query: { view: 'kpi' }, targetPath: '/teaching-dashboard', expectedQuery: { view: 'kpi' } },
      { from: '/admin/question-bank', query: { courseId: '10' }, targetPath: '/admin/content', expectedQuery: { tab: 'question-bank', courseId: '10' } },
      { from: '/admin/micro-lessons', query: { filter: 'draft' }, targetPath: '/admin/content', expectedQuery: { tab: 'micro-lessons', filter: 'draft' } },
      { from: '/admin/diagnostics', query: { q: 'sql' }, targetPath: '/admin/content', expectedQuery: { tab: 'diagnosis', q: 'sql' } },
      { from: '/admin/experiment-tasks', query: { taskId: '3' }, targetPath: '/admin/experiments', expectedQuery: { tab: 'tasks', taskId: '3' } },
      { from: '/admin/evaluations', query: { page: '2' }, targetPath: '/admin/experiments', expectedQuery: { tab: 'evaluation', page: '2' } },
    ]

    for (const exp of expectations) {
      await router.push({ path: exp.from, query: exp.query })
      expect(router.currentRoute.value.path).toBe(exp.targetPath)
      for (const [k, v] of Object.entries(exp.expectedQuery)) {
        expect(router.currentRoute.value.query[k]).toBe(v)
      }
    }
  })

  it('caches the resolved role in memory per access-token to prevent repeated network calls', async () => {
    localStorage.setItem('slp-access-token', 'stable-token')
    learning.currentUser.mockResolvedValue({ role: 'STUDENT', username: 'student' })

    await router.push('/dashboard')
    expect(learning.currentUser).toHaveBeenCalledTimes(1)

    await router.push('/courses')
    // 同一 token 且未失效，应使用内存缓存
    expect(learning.currentUser).toHaveBeenCalledTimes(1)

    // Token 改变时应触发重新请求
    localStorage.setItem('slp-access-token', 'new-token')
    learning.currentUser.mockResolvedValue({ role: 'ADMIN', username: 'admin' })
    await router.push('/teaching-dashboard')
    expect(learning.currentUser).toHaveBeenCalledTimes(2)
  })
})
