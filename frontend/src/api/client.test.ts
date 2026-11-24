import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { api, shouldRefreshAfterUnauthorized } from './client'

describe('401 refresh eligibility', () => {
  it('refreshes a protected current-user request', () => {
    expect(shouldRefreshAfterUnauthorized('/auth/me')).toBe(true)
  })

  it.each(['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'])('%s does not recursively trigger refresh', (url) => {
    expect(shouldRefreshAfterUnauthorized(url)).toBe(false)
  })
})

describe('single-flight refresh regression', () => {
  const originalAdapter = api.defaults.adapter

  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    api.defaults.adapter = originalAdapter
  })

  it('coalesces 5 concurrent 401 requests into exactly one refresh call and replays all with new token', async () => {
    localStorage.setItem('slp-access-token', 'expired-token')
    localStorage.setItem('slp-refresh-token', 'valid-refresh-token')

    let refreshCallCount = 0
    let resolveRefreshPromise: (value: any) => void
    const refreshDeferred = new Promise((resolve) => {
      resolveRefreshPromise = resolve
    })

    vi.spyOn(axios, 'post').mockImplementation((url) => {
      if (url === '/api/auth/refresh') {
        refreshCallCount++
        return refreshDeferred as any
      }
      return Promise.reject(new Error('Unexpected url: ' + url))
    })

    const replayedHeaders: string[] = []
    let totalInitialCalls = 0

    api.defaults.adapter = async (config) => {
      const isRetried = Boolean((config as { _slpRetried?: boolean })._slpRetried)
      if (!isRetried) {
        totalInitialCalls++
        const error = new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, {
          status: 401,
          statusText: 'Unauthorized',
          headers: {},
          config,
          data: { message: 'Token expired' },
        } as any)
        throw error
      }

      replayedHeaders.push(String(config.headers?.Authorization || ''))
      return {
        data: { success: true, url: config.url },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }

    // 5 个并发请求同时触发 401
    const promises = [
      api.get('/courses'),
      api.get('/knowledge'),
      api.get('/practice'),
      api.get('/recommendations'),
      api.get('/tutor/sessions'),
    ]

    // 稍作等待，确保 5 个请求全部发起并遇到 401，等待同一个 refresh promise
    await new Promise((r) => setTimeout(r, 20))

    expect(totalInitialCalls).toBe(5)
    expect(refreshCallCount).toBe(1) // Single-flight: 仅发出一次 refresh 请求

    // 解锁 refresh 成功返回
    resolveRefreshPromise!({
      data: {
        accessToken: 'brand-new-access-token',
        refreshToken: 'brand-new-refresh-token',
      },
    })

    const responses = await Promise.all(promises)

    expect(responses).toHaveLength(5)
    expect(refreshCallCount).toBe(1)
    expect(replayedHeaders).toHaveLength(5)
    replayedHeaders.forEach((header) => {
      expect(header).toBe('Bearer brand-new-access-token')
    })

    expect(localStorage.getItem('slp-access-token')).toBe('brand-new-access-token')
    expect(localStorage.getItem('slp-refresh-token')).toBe('brand-new-refresh-token')
  })

  it('clears session once and rejects all concurrent requests when refresh fails with 401', async () => {
    localStorage.setItem('slp-access-token', 'expired-token')
    localStorage.setItem('slp-refresh-token', 'revoked-refresh-token')
    localStorage.setItem('slp-user-name', 'testuser')
    localStorage.setItem('slp-user-role', 'STUDENT')

    vi.spyOn(axios, 'post').mockImplementation((url) => {
      if (url === '/api/auth/refresh') {
        const error = new axios.AxiosError('Refresh token expired', 'ERR_BAD_REQUEST', {} as any, null, {
          status: 401,
          statusText: 'Unauthorized',
          headers: {},
          config: {} as any,
          data: { message: 'Invalid refresh token' },
        } as any)
        return Promise.reject(error)
      }
      return Promise.reject(new Error('Unexpected'))
    })

    api.defaults.adapter = async (config) => {
      const error = new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, {
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
        config,
        data: {},
      } as any)
      throw error
    }

    const requests = [
      api.get('/courses'),
      api.get('/knowledge'),
      api.get('/practice'),
    ]

    const results = await Promise.allSettled(requests)

    expect(results).toHaveLength(3)
    results.forEach((res) => {
      expect(res.status).toBe('rejected')
    })

    // 校验所有认证 key 被清理
    expect(localStorage.getItem('slp-access-token')).toBeNull()
    expect(localStorage.getItem('slp-refresh-token')).toBeNull()
    expect(localStorage.getItem('slp-user-name')).toBeNull()
    expect(localStorage.getItem('slp-user-role')).toBeNull()
  })

  it('does not recursively retry when replayed request itself returns 401', async () => {
    localStorage.setItem('slp-access-token', 'initial-token')
    localStorage.setItem('slp-refresh-token', 'valid-refresh')

    let refreshCallCount = 0
    vi.spyOn(axios, 'post').mockImplementation(async (url) => {
      if (url === '/api/auth/refresh') {
        refreshCallCount++
        return {
          data: {
            accessToken: 'refreshed-token',
            refreshToken: 'refreshed-refresh',
          },
        }
      }
      throw new Error('Unexpected')
    })

    let requestExecutionCount = 0
    api.defaults.adapter = async (config) => {
      requestExecutionCount++
      const error = new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, {
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
        config,
        data: {},
      } as any)
      throw error
    }

    await expect(api.get('/protected-resource')).rejects.toThrow()

    // 恰好执行 2 次：1 次初次请求 + 1 次刷新重试，随后停止，绝不循环
    expect(requestExecutionCount).toBe(2)
    expect(refreshCallCount).toBe(1)
  })
})
