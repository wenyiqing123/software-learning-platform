import { readonly, ref } from 'vue'
import { learningApi } from '../api/client'

export interface SessionUser { id: number; username: string; displayName: string; role: string }
const user = ref<SessionUser | null>(null)
const loading = ref(false)
const error = ref('')
let accessToken: string | null = null
let requestId = 0
let pending: Promise<void> | null = null

export function resetSession() {
  requestId++
  accessToken = null
  pending = null
  user.value = null
  localStorage.removeItem('slp-user-role')
  loading.value = false
  error.value = ''
}

export function refreshSession(): Promise<void> {
  const token = localStorage.getItem('slp-access-token')
  if (!token) { resetSession(); return Promise.resolve() }
  if (token === accessToken && pending) return pending
  if (token !== accessToken) user.value = null
  accessToken = token
  const request = ++requestId
  loading.value = true
  error.value = ''
  pending = (async () => {
    try {
      const account: SessionUser = await learningApi.currentUser()
      if (request !== requestId) return
      if (localStorage.getItem('slp-access-token') !== token) { pending = null; return refreshSession() }
      if (!account || !account.username || typeof account.role !== 'string') throw new Error('Invalid account response')
      user.value = account
      localStorage.setItem('slp-user-role', account.role)
    } catch {
      if (request !== requestId) return
      if (localStorage.getItem('slp-access-token') !== token) { pending = null; return refreshSession() }
      user.value = null
      error.value = '无法确认当前账户，请检查连接后重试。'
    } finally {
      if (request === requestId) { loading.value = false; pending = null }
    }
  })()
  return pending
}

export function useSession() {
  return { user: readonly(user), loading: readonly(loading), error: readonly(error), refresh: refreshSession }
}
