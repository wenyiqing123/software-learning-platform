<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, BrainCircuit, CircleAlert, Network, RotateCcw, Sparkles, Target } from '@lucide/vue'
import { learningApi } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'

interface Recommendation { id: number; knowledgePointId?: number; title: string; reason: string; kind: string; score: number; knowledgePoint?: string }
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
    <section class="recommend-overview"><div><span class="recommend-big-icon"><Sparkles :size="20" /></span><p class="eyebrow">可解释规则推荐</p><h2>{{ loading ? '正在读取推荐' : error ? '推荐暂不可用' : hasCards ? '从一个知识点开始巩固' : '当前暂无推荐记录' }}</h2><p>建议依据当前账户的掌握度、错题、练习记录与先修关系生成；排序分只用于安排优先级，不代表学习效果。</p></div><div class="recommend-signal"><span><Target :size="16" />按薄弱点练习</span><span><Network :size="16" />探索先修关系</span></div></section>
    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16" />{{ error }}<button class="text-button" @click="reload">重试</button></div>
    <FeedbackState v-if="loading" loading loading-title="正在读取学习建议" loading-text="正在整理当前账户的掌握度、错题与先修关系。" />
    <FeedbackState v-else-if="error" :error="error" error-title="暂时无法确认推荐内容" retry-label="重试" @retry="reload" />
    <section v-else-if="hasCards" class="recommendation-list"><article v-for="(item, index) in cards" :key="item.id" class="recommendation-row"><span class="rec-number">{{ String(index + 1).padStart(2, '0') }}</span><span class="rec-icon"><component :is="icons[index % icons.length]" :size="19" /></span><div class="rec-copy"><div><span class="recommend-kind">{{ item.kind || '学习建议' }}</span><span v-if="Number.isFinite(item.score)" class="rec-score" title="仅用于推荐排序，不代表掌握度">排序分 {{ item.score }}</span></div><h3>{{ item.title }}</h3><p :title="item.reason">{{ item.reason }}</p></div><div class="rec-actions"><button class="button button-dark button-small" :disabled="pointId(item) === undefined" @click="practice(item)">针对练习 <ArrowRight :size="14" /></button><button class="button button-outline button-small" :disabled="!item.knowledgePoint" @click="graph(item)">查看图谱 <Network :size="14" /></button></div></article></section>
    <FeedbackState v-else empty empty-title="当前暂无学习建议" empty-text="可以先完成课程练习，再刷新查看服务端推荐。" empty-action-label="开始练习" @action="router.push('/practice')" />
    <div class="recommend-formula"><strong>推荐依据</strong><span>掌握度 · 错题 · 练习记录 · 先修关系</span><i></i><span>完成练习后可刷新推荐</span></div>
  </div>
</template>

<style scoped>
.rec-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.inline-notice { flex-wrap: wrap; }
@media (max-width: 720px) {
  .recommendation-row { grid-template-columns: 24px minmax(0, 1fr); }
  .rec-icon { display: none; }
  .rec-actions { grid-column: 2; }
}
</style>
