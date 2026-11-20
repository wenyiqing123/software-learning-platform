<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, BrainCircuit, CircleAlert, Network, RotateCcw, Sparkles, Target } from '@lucide/vue'
import { learningApi } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'

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
    <SectionHeading eyebrow="基于你的学习记录" title="为你推荐" description="查看服务端返回的学习建议，按知识点打开练习或图谱。"><button class="button button-outline" :disabled="loading" @click="reload"><RotateCcw :size="15" />{{ loading ? '正在读取' : '刷新推荐' }}</button></SectionHeading>
    <section class="recommend-overview"><div><span class="recommend-big-icon"><Sparkles :size="20" /></span><p class="eyebrow">课程学习建议</p><h2>{{ loading ? '正在读取推荐' : error ? '推荐暂不可用' : hasCards ? '从一个知识点开始巩固' : '当前暂无推荐记录' }}</h2><p>推荐原因与排序分由服务端提供；尚无答题记录的知识点也可能作为入门建议。</p></div><div class="recommend-signal"><span><Target :size="16" />按知识点练习</span><span><Network :size="16" />探索关联概念</span></div></section>
    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16" />{{ error }}<button class="text-button" @click="reload">重试</button></div>
    <div v-if="loading" class="empty-state"><span class="spinner"></span><strong>正在读取学习建议</strong></div>
    <div v-else-if="error" class="empty-state"><CircleAlert :size="24" /><strong>暂时无法确认推荐内容</strong><span>可稍后重试，或直接进入课程练习。</span><button class="text-button" @click="router.push('/practice')">查看练习 <ArrowRight :size="14" /></button></div>
    <section v-else-if="hasCards" class="recommendation-list"><article v-for="(item, index) in cards" :key="item.id" class="recommendation-row"><span class="rec-number">{{ String(index + 1).padStart(2, '0') }}</span><span class="rec-icon"><component :is="icons[index % icons.length]" :size="19" /></span><div class="rec-copy"><div><span class="recommend-kind">{{ item.kind || '学习建议' }}</span><span v-if="Number.isFinite(item.score)" class="rec-score" title="仅用于推荐排序，不代表掌握度">排序分 {{ item.score }}</span></div><h3>{{ item.title }}</h3><p :title="item.reason">{{ item.reason }}</p></div><div class="rec-actions"><button class="button button-dark button-small" :disabled="pointId(item) === undefined" @click="practice(item)">针对练习 <ArrowRight :size="14" /></button><button class="button button-outline button-small" :disabled="!item.knowledgePoint" @click="graph(item)">查看图谱 <Network :size="14" /></button></div></article></section>
    <div v-else class="empty-state"><span class="empty-check"><Sparkles :size="24" /></span><strong>当前暂无学习建议</strong><span>可以先完成课程练习，再刷新查看服务端推荐。</span><button class="text-button" @click="router.push('/practice')">开始练习 <ArrowRight :size="14" /></button></div>
    <div class="recommend-formula"><strong>使用说明</strong><span>排序分不代表学习完成度</span><i></i><span>提交练习后可刷新推荐</span></div>
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
