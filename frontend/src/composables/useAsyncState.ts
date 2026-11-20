import { ref } from 'vue'

export function useAsyncState<T>(loader: () => Promise<T>, initial: T) {
  const data = ref(initial) as { value: T }
  const loading = ref(false)
  const error = ref('')

  async function reload() {
    loading.value = true
    error.value = ''
    try {
      data.value = await loader()
      return data.value
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '暂时无法连接学习服务'
      return null
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, reload }
}
