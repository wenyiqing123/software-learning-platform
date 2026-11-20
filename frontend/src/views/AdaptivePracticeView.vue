<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, CircleAlert, Clock3, RotateCcw, Target, TriangleAlert } from '@lucide/vue'
import { useRouter } from 'vue-router'
import { api, apiError, learningApi, type AdaptivePracticeItem } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'

interface CourseOption { id: number; name: string }

const router = useRouter()
const items = ref<AdaptivePracticeItem[]>([])
const courses = ref<CourseOption[]>([])
const limit = ref(10)
const courseId = ref<number | undefined>()
const loading = ref(true)
const courseLoading = ref(true)
const error = ref('')
const courseError = ref('')
const requestVersion = ref(0)

const hasItems = computed(() => items.value.length > 0)
const selectedCourseName = computed(() => courses.value.find(course => course.id === courseId.value)?.name)

async function loadCourses() {
  courseLoading.value = true
  courseError.value = ''
  try {
    courses.value = (await api.get<CourseOption[]>('/courses')).data
  } catch (cause) {
    courseError.value = apiError(cause, '课程列表暂时无法加载，仍可查看全部专项练习。')
  } finally {
    courseLoading.value = false
  }
}

async function loadCandidates() {
  const version = ++requestVersion.value
  loading.value = true
  error.value = ''
  const params: { limit: number; courseId?: number } = { limit: limit.value }
  if (courseId.value !== undefined) params.courseId = courseId.value
  try {
    const result = await learningApi.adaptivePractice(params)
    if (version === requestVersion.value) items.value = Array.isArray(result) ? result : []
  } catch (cause) {
    if (version === requestVersion.value) error.value = apiError(cause, '专项练习暂时无法生成，请稍后重试。')
  } finally {
    if (version === requestVersion.value) loading.value = false
  }
}

function start(item: AdaptivePracticeItem) {
  void router.push({ path: '/practice', query: { questionId: String(item.questionId) } })
}

function mastery(item: AdaptivePracticeItem) {
  return Math.max(0, Math.min(100, Math.round(item.mastery ?? 0)))
}

function difficulty(value?: number) {
  return value === undefined ? '未标注' : `难度 ${value}/5`
}

function reasonLabel(item: AdaptivePracticeItem) {
  if (item.due) return '到期复习'
  if (item.wrongCount > 0) return '错题巩固'
  if (mastery(item) < 60) return '薄弱知识点'
  return '先修关系补强'
}

onMounted(() => {
  void Promise.all([loadCourses(), loadCandidates()])
})
</script>

<template>
  <div class="page-stack">
    <SectionHeading eyebrow="规则式选题" title="自适应专项练习" description="从真实题库中优先安排到期错题、薄弱知识点和先修关系相关题目。">
      <button class="button button-outline" aria-label="刷新专项练习" :disabled="loading" @click="loadCandidates"><RotateCcw :size="15" />刷新</button>
    </SectionHeading>

    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16" /><span>{{ error }}</span><button class="text-button" @click="loadCandidates">重试</button></div>

    <section class="adaptive-controls" aria-label="专项练习筛选">
      <label><Target :size="15" />练习数量<select v-model.number="limit" data-testid="adaptive-limit"><option :value="5">5 题</option><option :value="10">10 题</option><option :value="15">15 题</option><option :value="20">20 题</option><option :value="30">30 题</option></select></label>
      <label><TriangleAlert :size="15" />课程<select v-model.number="courseId" data-testid="adaptive-course" :disabled="courseLoading"><option :value="undefined">全部课程</option><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }}</option></select></label>
      <button class="button button-dark" :disabled="loading" @click="loadCandidates"><Target :size="15" />生成专项题单</button>
      <span class="adaptive-filter-note">{{ selectedCourseName || '全部课程' }} · 最多 {{ limit }} 题</span>
    </section>
    <p v-if="courseError" class="field-note">{{ courseError }}</p>

    <div v-if="loading" class="empty-state"><span class="spinner"></span><strong>正在分析学习记录</strong><span>只读取当前账户可见的题库、错题和掌握度。</span></div>
    <section v-else-if="hasItems" class="adaptive-list">
      <article v-for="(item, index) in items" :key="item.questionId" class="adaptive-item">
        <div class="adaptive-index">{{ String(index + 1).padStart(2, '0') }}</div>
        <div class="adaptive-copy">
          <div class="adaptive-top"><span class="eyebrow">{{ item.knowledgePointName || '课程知识点' }}</span><span class="adaptive-score">排序分 {{ item.score }}</span></div>
          <h2>{{ item.stem }}</h2>
          <div class="adaptive-meta"><span>{{ reasonLabel(item) }}</span><span>掌握度 {{ mastery(item) }}%</span><span>答错 {{ item.wrongCount }} 次</span><span>{{ difficulty(item.difficulty) }}</span><span v-if="item.due" class="due-label"><Clock3 :size="12" />已到期</span></div>
          <p>{{ item.reason }}</p>
        </div>
        <button class="button button-dark button-small" :data-testid="'adaptive-start-' + item.questionId" @click="start(item)">开始练习<ArrowRight :size="14" /></button>
      </article>
    </section>
    <section v-else class="empty-state"><Target :size="24" /><strong>当前没有可安排的专项题目</strong><span>先完成课程练习，或调整课程和题目数量后重试。</span><button class="button button-outline" @click="router.push('/practice')">进入课程练习<ArrowRight :size="15" /></button></section>
  </div>
</template>
