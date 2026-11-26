<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, BrainCircuit, Clock3, Network, RotateCcw, Target } from '@lucide/vue'
import { learningApi, type ReviewUrgency } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
import {
  effectiveMasteryPercent,
  normalizeReviewUrgency,
  peakMasteryPercent,
  retentionAlertLabel,
  RETENTION_RULE_SUMMARY,
} from '../domain/masteryRetention'

interface Recommendation {
  id: number
  knowledgePointId?: number
  title: string
  reason: string
  kind: string
  score: number
  knowledgePoint?: string
  peakMastery?: number
  effectiveMastery?: number
  daysSinceLastPractice?: number
  reviewUrgency?: ReviewUrgency
}
const router = useRouter()
const cards = ref<Recommendation[]>([])
const loading = ref(true)
const error = ref('')
const icons = [BrainCircuit, RotateCcw, Network, Target]
let requestId = 0
const hasCards = computed(() => !error.value && cards.value.length > 0)
async function reload() {
  const request = ++requestId
  loading.value = true
  error.value = ''
  try {
    const response: Recommendation[] = await learningApi.recommendations()
    if (request !== requestId) return
    if (!Array.isArray(response)) throw new Error('Invalid recommendation response')
    cards.value = response
  } catch {
    if (request === requestId) { cards.value = []; error.value = '个性化推荐服务暂不可用，请检查连接后重试。' }
  } finally {
    if (request === requestId) loading.value = false
  }
}
function pointId(item: Recommendation) {
  const id = item.knowledgePointId ?? item.id
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}
function practice(item: Recommendation) {
  const id = pointId(item)
  if (id !== undefined) void router.push({ path: '/practice', query: { knowledgePointId: String(id) } })
}
function graph(item: Recommendation) {
  if (item.knowledgePoint) void router.push({ path: '/knowledge', query: { entity: item.knowledgePoint } })
}
function refreshRecommendations() { void reload() }
onMounted(() => {
  void reload()
  window.addEventListener('learning:changed', refreshRecommendations)
})
onBeforeUnmount(() => {
  requestId++
  window.removeEventListener('learning:changed', refreshRecommendations)
})
</script>

<template>
  <div class="page-stack">
    <SectionHeading eyebrow="基于你的学习记录" title="为你推荐" description="基于掌握度、错题与先修关系的可解释规则推荐。"><button class="button button-outline" :disabled="loading" @click="reload"><RotateCcw :size="15" />{{ loading ? '正在读取' : '刷新推荐' }}</button></SectionHeading>
    <FeedbackState v-if="loading" loading loading-title="正在读取学习建议" loading-text="正在整理当前账户的掌握度、错题与先修关系。" />
    <FeedbackState v-else-if="error" :error="error" error-title="暂时无法确认推荐内容" retry-label="重试" @retry="reload" />
    <section v-else-if="hasCards" class="recommendation-list" aria-label="学习建议">
      <article v-for="(item, index) in cards" :key="item.id" class="recommendation-row">
        <span class="rec-icon"><component :is="icons[index % icons.length]" :size="18" /></span>
        <div class="rec-copy">
          <div>
            <span class="recommend-kind">{{ item.kind || '学习建议' }}</span>
            <span
              v-if="retentionAlertLabel(item)"
              class="rec-retention-badge"
              :class="'urgency-' + normalizeReviewUrgency(item).toLowerCase()"
            >
              <Clock3 :size="12" aria-hidden="true" />
              {{ retentionAlertLabel(item) }}
            </span>
            <span v-if="typeof item.effectiveMastery === 'number'" class="rec-retention-meta">
              保持 {{ effectiveMasteryPercent(item) }}% / 峰值 {{ peakMasteryPercent(item) }}%
            </span>
            <span v-if="Number.isFinite(item.score)" class="rec-score" title="仅用于推荐排序，不代表掌握度">排序分 {{ item.score }}</span>
          </div>
          <h3>{{ item.title }}</h3><p>{{ item.reason }}</p>
        </div>
        <div class="rec-actions">
          <button class="button button-dark button-small" :disabled="pointId(item) === undefined" @click="practice(item)">针对练习 <ArrowRight :size="14" /></button>
          <button class="button button-outline button-small" :disabled="!item.knowledgePoint" @click="graph(item)">查看图谱 <Network :size="14" /></button>
          <RouterLink v-if="normalizeReviewUrgency(item) !== 'FRESH'" class="button button-outline button-small" to="/review">间隔复习</RouterLink>
        </div>
      </article>
    </section>
    <FeedbackState v-else empty empty-title="当前暂无学习建议" empty-text="可以先完成课程练习，再刷新查看服务端推荐。" empty-action-label="开始练习" @action="router.push('/practice')" />
    <div v-if="hasCards" class="recommend-formula"><strong>可解释规则推荐依据</strong><span>{{ RETENTION_RULE_SUMMARY }} 排序分仅作优先级参考，不代表已验证的学习成效。</span></div>
  </div>
</template>

<style scoped>
.rec-retention-badge{display:inline-flex;align-items:center;gap:4px;padding:3px 8px;border-radius:var(--radius-pill);font-size:12px;font-weight:600}
.rec-retention-badge.urgency-fading{color:var(--warning-strong);background:var(--warning-soft)}
.rec-retention-badge.urgency-due{color:var(--danger);background:var(--danger-soft)}
.rec-retention-meta{color:var(--text-muted);font-size:12px;font-variant-numeric:tabular-nums}
</style>
