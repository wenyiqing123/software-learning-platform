import { onBeforeUnmount, onMounted, ref } from 'vue'
import { apiError, learningApi } from '../api/client'
import { mergeAiDependency, type SystemStatus } from '../domain/systemStatus'

const status = ref<SystemStatus | null>(null)
const loading = ref(false)
const error = ref('')
let timer: ReturnType<typeof setInterval> | null = null
let pending: Promise<void> | null = null
let requestId = 0

export function resetSystemStatus() {
  requestId++
  status.value = null
  loading.value = false
  error.value = ''
  pending = null
}

async function load() {
  const token = localStorage.getItem('slp-access-token')
  if (!token) { resetSystemStatus(); return }
  if (pending) return pending
  const request = ++requestId
  loading.value = true
  error.value = ''
  pending = Promise.allSettled([learningApi.systemStatus(), learningApi.dependencies()]).then(([systemResult, aiResult]) => {
    if (request !== requestId || localStorage.getItem('slp-access-token') !== token) return
    if (systemResult.status === 'fulfilled') {
      const ai = aiResult.status === 'fulfilled' ? aiResult.value : null
      status.value = mergeAiDependency(systemResult.value, ai)
      if (aiResult.status === 'rejected') error.value = '大模型配置状态暂不可确认。'
    } else {
      status.value = null
      error.value = apiError(systemResult.reason, '系统依赖状态暂不可用。')
    }
  }).finally(() => {
    loading.value = false
    pending = null
  })
  return pending
}

export function useSystemStatus() {
  const reload = () => load()
  const onStorage = (event: StorageEvent) => {
    if (event.key === null || event.key === 'slp-access-token') {
      requestId++
      pending = null
      void load()
    }
  }
  onMounted(() => {
    void load()
    timer = window.setInterval(() => void load(), 60_000)
    window.addEventListener('storage', onStorage)
  })
  onBeforeUnmount(() => {
    if (timer !== null) window.clearInterval(timer)
    timer = null
    window.removeEventListener('storage', onStorage)
  })
  return { status, loading, error, reload }
}
