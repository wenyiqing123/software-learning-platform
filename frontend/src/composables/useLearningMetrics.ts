import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { learningApi } from '../api/client'

export interface KnowledgePointMetric { id: number; courseId?: number; name: string; graphEntity: string; mastery?: number | null; sortOrder: number }
export interface DailyActivity { day: string; attempts: number }
export interface OverviewMetric { mastery: number; completed: number; wrongCount: number; accuracy: number; activity: DailyActivity[] }

const points = ref<KnowledgePointMetric[]>([])
const overview = ref<OverviewMetric | null>(null)
const loading = ref(false)
const error = ref('')
let ownerToken: string | null = null
let requestId = 0
let pending: Promise<void> | null = null

export function weeklyActivity(activity: DailyActivity[], now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now)
  const part = (type: string) => Number(parts.find(item => item.type === type)?.value)
  const today = Date.UTC(part('year'), part('month') - 1, part('day'))
  const counts = new Map(activity.map(item => [item.day, item.attempts]))
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today - (6 - index) * 86_400_000)
    const key = date.toISOString().slice(0, 10)
    const attempts = counts.get(key)
    return { key, label: ['日', '一', '二', '三', '四', '五', '六'][date.getUTCDay()],
      count: typeof attempts === 'number' && Number.isFinite(attempts) ? Math.max(0, attempts) : 0 }
  })
}

export function resetLearningMetrics() {
  requestId++
  ownerToken = null
  pending = null
  overview.value = null
  points.value = []
  loading.value = false
  error.value = ''
}

function load(role?: Readonly<Ref<string | undefined>>): Promise<void> {
  if (role?.value === 'ADMIN') { resetLearningMetrics(); return Promise.resolve() }
  const token = localStorage.getItem('slp-access-token')
  if (!token) { resetLearningMetrics(); return Promise.resolve() }
  if (token === ownerToken && pending) return pending
  if (token !== ownerToken) { overview.value = null; points.value = [] }
  ownerToken = token
  const request = ++requestId
  loading.value = true
  error.value = ''
  pending = (async () => {
    const [summary, knowledge] = await Promise.allSettled([learningApi.overview(), learningApi.knowledgePoints()])
    if (request !== requestId) return
    if (localStorage.getItem('slp-access-token') !== token) { pending = null; return load() }
    overview.value = summary.status === 'fulfilled' ? summary.value : null
    points.value = knowledge.status === 'fulfilled' && Array.isArray(knowledge.value) ? knowledge.value : []
    const failures = []
    if (summary.status === 'rejected' || !overview.value) failures.push('学习统计暂不可用')
    if (knowledge.status === 'rejected') failures.push('知识点暂不可用')
    error.value = failures.length ? failures.join('，') + '，请检查连接后重试。' : ''
    loading.value = false
    pending = null
  })()
  return pending
}

export function useLearningMetrics(role?: Readonly<Ref<string | undefined>>) {
  const reload = () => load(role)
  const onStorage = (event: StorageEvent) => {
    if (event.key === null || event.key === 'slp-access-token' || event.key === 'slp-user-role') void load(role)
  }
  onMounted(() => {
    void load(role)
    window.addEventListener('learning:changed', reload)
    window.addEventListener('storage', onStorage)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('learning:changed', reload)
    window.removeEventListener('storage', onStorage)
  })
  if (role) watch(role, () => void load(role))
  return { points, overview, loading, error, reload }
}
