<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, BookOpenCheck, Check, CircleAlert, Clock3, RotateCcw, Search, Sparkles } from '@lucide/vue'
import { learningApi } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import LearningContextActions from '../components/LearningContextActions.vue'

interface WrongQuestion { id: number; questionId: number; question: string; knowledgePoint?: string; wrongCount: number; status?: string }
const router = useRouter()
const rows = ref<WrongQuestion[]>([])
const loading = ref(true)
const loaded = ref(false)
const loadError = ref('')
const actionError = ref('')
const search = ref('')
const busyId = ref<number | null>(null)
const retryId = ref<number | null>(null)
let requestId = 0
let active = true
const pending = computed(() => rows.value.filter(item => item.status !== 'MASTERED'))
const items = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return pending.value.filter(item => (item.question || '').toLocaleLowerCase().includes(query) || (item.knowledgePoint || '').toLocaleLowerCase().includes(query))
})
async function load() {
  const request = ++requestId
  loading.value = true
  loadError.value = ''
  try {
    const result: WrongQuestion[] = await learningApi.wrongBook()
    if (request !== requestId || !active) return
    if (!Array.isArray(result)) throw new Error('Invalid wrong-book response')
    rows.value = result
    loaded.value = true
  } catch {
    if (request !== requestId || !active) return
    rows.value = []
    loaded.value = false
    loadError.value = '错题服务暂不可用，无法确认当前待复习数量，请检查连接后重试。'
  } finally {
    if (request === requestId && active) loading.value = false
  }
}
function review(item?: WrongQuestion) {
  if (!item || !Number.isSafeInteger(item.questionId) || item.questionId <= 0) {
    actionError.value = '原题暂不可用，请刷新错题列表或联系课程管理员。'
    return
  }
  void router.push({ path: '/practice', query: { questionId: String(item.questionId), wrongItemId: String(item.id) } })
}
async function markMastered(id: number) {
  if (busyId.value !== null) return
  busyId.value = id
  actionError.value = ''
  retryId.value = null
  try {
    await learningApi.markWrongMastered(id)
    if (active) rows.value = rows.value.filter(item => item.id !== id)
    window.dispatchEvent(new Event('learning:changed'))
  } catch {
    if (active) {
      actionError.value = '暂时无法更新错题状态，请检查服务连接。题目仍保留在待复习列表中。'
      retryId.value = id
    }
  } finally {
    if (active) busyId.value = null
  }
}
onMounted(() => { void load() })
onBeforeUnmount(() => { active = false; requestId++ })
</script>

<template>
  <div class="page-stack">
    <SectionHeading eyebrow="复习巩固" title="错题复习" description="按知识点整理薄弱环节，及时回顾并记录掌握情况。"><button class="button button-dark" :disabled="loading || !loaded || !pending.length" @click="review(pending[0])"><RotateCcw :size="16" />开始复习</button></SectionHeading>
    <div v-if="loadError" class="inline-notice" role="alert"><CircleAlert :size="16" /><span>{{ loadError }}</span><button class="text-button" :disabled="loading" @click="load">重试加载</button></div>
    <div v-if="actionError" class="inline-notice" role="alert"><CircleAlert :size="16" /><span>{{ actionError }}</span><button v-if="retryId !== null" class="text-button" :disabled="busyId !== null" @click="markMastered(retryId)">重试更新</button></div>
    <section class="wrong-summary"><div><p class="eyebrow">待处理错题</p><strong>{{ loaded ? pending.length : '—' }}</strong><span>{{ loaded ? '道题目待复习（不受搜索筛选影响）' : loading ? '正在读取个人错题记录' : '数量暂不可用' }}</span></div><div class="wrong-summary-note"><span class="summary-icon"><Sparkles :size="17" /></span><span>重新作答将记录复习结果；也可以在确认理解后标记为已掌握。</span></div></section>
    <section class="content-section wrong-list-section"><div class="section-title-row"><div><p class="eyebrow">逐题巩固</p><h2>待复习题目</h2></div><label class="search-field"><Search :size="16" /><input v-model="search" placeholder="搜索题目或知识点" /></label></div>
      <div v-if="loading" class="empty-state"><span class="spinner"></span><strong>正在加载错题</strong></div>
      <div v-else-if="loadError" class="empty-state"><CircleAlert :size="24" /><strong>暂时无法读取错题</strong><span>连接恢复后再查看真实待复习记录。</span><button class="text-button" @click="load">重试加载 <RotateCcw :size="14" /></button></div>
      <div v-else-if="items.length" class="wrong-list"><article v-for="item in items" :key="item.id" class="wrong-row"><span class="wrong-mark"><CircleAlert :size="18" /></span><div class="wrong-content"><strong>{{ item.question }}</strong><div><span v-if="item.knowledgePoint" class="topic-chip">{{ item.knowledgePoint }}</span><span class="wrong-meta"><RotateCcw :size="13" />答错 {{ item.wrongCount }} 次</span></div><LearningContextActions compact :context="{ entity: item.knowledgePoint }" /></div><button class="button button-outline button-small" :disabled="busyId !== null" @click="review(item)">重新练习 <ArrowRight :size="14" /></button><button class="icon-button mastered-button" :disabled="busyId !== null" :title="busyId === item.id ? '正在保存' : '标记为已掌握'" @click="markMastered(item.id)"><Check :size="17" /></button></article></div>
      <div v-else class="empty-state"><span class="empty-check"><BookOpenCheck :size="24" /></span><strong>{{ search ? '没有匹配的错题' : '当前没有待复习错题' }}</strong><span>{{ search ? '调整搜索词查看其他待复习题目。' : '完成课程练习后，答错的题目会自动整理到这里。' }}</span><button class="text-button" @click="router.push('/practice')">去做练习 <ArrowRight :size="14" /></button></div>
    </section>
    <div class="review-tip"><Clock3 :size="16" /><span>建议隔一段时间重新作答，结合解析检查自己是否真正理解。</span></div>
  </div>
</template>

<style scoped>
.inline-notice { flex-wrap: wrap; }
</style>
