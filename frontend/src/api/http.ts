import axios from 'axios'

export const api = axios.create({ baseURL: '/api', timeout: 20_000, headers: { 'Content-Type': 'application/json' } })

let refreshInFlight: Promise<string> | null = null

function clearSession() {
  for (const key of ['slp-access-token', 'slp-refresh-token', 'slp-user-name']) localStorage.removeItem(key)
  window.dispatchEvent(new Event('session:changed'))
  if (window.location.pathname !== '/login') window.location.assign('/login')
}

export function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem('slp-refresh-token')
  if (!refreshToken) { clearSession(); return Promise.reject(new Error('请重新登录')) }
  refreshInFlight ??= axios.post('/api/auth/refresh', { refreshToken }, { timeout: 10_000 }).then(({ data }) => {
    localStorage.setItem('slp-access-token', data.accessToken)
    localStorage.setItem('slp-refresh-token', data.refreshToken)
    return data.accessToken as string
  }).catch((error) => {
    if (error.response?.status === 401 || error.response?.status === 403) clearSession()
    throw error
  }).finally(() => { refreshInFlight = null })
  return refreshInFlight
}

export function shouldRefreshAfterUnauthorized(url?: string): boolean {
  if (!url) return false
  const endpoint = url.split('?')[0]
  return !['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'].includes(endpoint)
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('slp-access-token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

api.interceptors.response.use((response) => response, async (error) => {
  const config = error.config as (typeof error.config & { _slpRetried?: boolean }) | undefined
  if (error.response?.status !== 401 || !config || config._slpRetried || !shouldRefreshAfterUnauthorized(config.url)) return Promise.reject(error)
  config._slpRetried = true
  const token = await refreshAccessToken()
  config.headers.Authorization = 'Bearer ' + token
  return api(config)
})

export function apiError(error: unknown, fallback = '请求未完成，请稍后重试'): string {
  if (axios.isAxiosError(error)) return error.response?.data?.message || (error.code === 'ECONNABORTED' ? '请求耗时较长，请稍后重试' : fallback)
  return error instanceof Error ? error.message : fallback
}
