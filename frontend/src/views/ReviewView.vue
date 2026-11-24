<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, BookOpen, Check, CircleAlert, CircleCheck, CircleX, Clock3, Pause, RotateCcw } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import { learningApi, type SpacedReviewCard, type SpacedReviewResult } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import LearningContextActions from '../components/LearningContextActions.vue'

const router = useRouter()
const route = useRoute()
const dueCards = ref<SpacedReviewCard[]>([])
const pausedCards = ref<SpacedReviewCard[]>([])
const showingPaused = ref(false)
const index = ref(0)
const selected = ref<string[]>([])
const rating = ref<number | null>(null)
const result = ref<SpacedReviewResult | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const cards = computed(() => showingPaused.value ? pausedCards.value : dueCards.value)
const current = computed(() => cards.value[index.value])
const answers = computed(() => result.value?.correctAnswer ?? [])

function clearAnswer() {
  selected.value = []
  rating.value = null
  result.value = null
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const targetId = typeof route.query.wrongQuestionId === 'string' ? Number(route.query.wrongQuestionId) : undefined
    const [due, paused] = await Promise.all([learningApi.spacedReviewsDue(targetId), learningApi.spacedReviewsPaused()])
    dueCards.value = due
    pausedCards.value = paused
    showingPaused.value = false
    index.value = 0
    clearAnswer()
  } catch {
    error.value = '暂时无法读取复习题，请检查服务连接后重试。'
  } finally {
    loading.value = false
  }
}

function showPaused() { showingPaused.value=true; index.value=0; clearAnswer() }
function showDue() { showingPaused.value=false; index.value=0; clearAnswer() }

function toggle(key: string) {
  if (!current.value || result.value || showingPaused.value) return
  selected.value = current.value.questionType === 'MULTIPLE'
    ? selected.value.includes(key) ? selected.value.filter(value => value !== key) : [...selected.value, key]
    : [key]
}

async function submit() {
  if (!current.value || !selected.value.length || rating.value === null || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    result.value = await learningApi.submitSpacedReview(current.value.wrongQuestionId, {
      answer: [...selected.value], recallRating: rating.value, requestKey: crypto.randomUUID(),
    })
    window.dispatchEvent(new Event('learning:changed'))
  } catch (cause) {
    const response = cause as { response?: { data?: { message?: string; detail?: string } } }
    error.value = response.response?.data?.message || response.response?.data?.detail || '提交失败，复习任务未更新。请重试。'
  } finally {
    submitting.value = false
  }
}

async function setState(state: 'ACTIVE' | 'PAUSED' | 'MASTERED') {
  if (!current.value || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    await learningApi.setSpacedReviewState(current.value.wrongQuestionId, state)
    await load()
    window.dispatchEvent(new Event('learning:changed'))
  } catch {
    error.value = '复习状态更新失败，请检查服务连接后重试。'
  } finally {
    submitting.value = false
  }
}

function next() {
  if (index.value + 1 < cards.value.length) {
    index.value++
    clearAnswer()
  } else {
    void load()
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page-stack">
    <SectionHeading eyebrow="记忆巩固" title="到期复习" description="结合客观答题结果与主观回忆评分安排下一次复习。">
      <button v-if="pausedCards.length" class="button button-outline" :disabled="loading" @click="showPaused"><Pause :size="15" />暂停题目 {{ pausedCards.length }}</button>
      <button class="button button-outline" :disabled="loading" @click="load"><RotateCcw :size="15" />刷新队列</button>
    </SectionHeading>
    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16" /><span>{{ error }}</span><button class="text-button" @click="load">重试</button></div>
    <div v-if="loading" class="empty-state"><span class="spinner"></span><strong>正在读取复习题</strong></div>
    <section v-else-if="current" class="review-workspace">
      <div class="review-progress"><span><Clock3 :size="15" />{{ showingPaused ? '已暂停' : '到期' }} {{ cards.length }} 题</span><span>{{ index + 1 }} / {{ cards.length }}</span></div>
      <div class="review-context"><span class="eyebrow">{{ current.knowledgePoint || '课程知识点' }}</span><span>此前答错 {{ current.wrongCount }} 次</span><span>上次间隔 {{ current.previousIntervalMinutes || 0 }} 分钟</span></div>
      <h2 class="review-question">{{ current.question }}</h2>
      <LearningContextActions compact :context="{ entity: current.knowledgePoint }" />
      <div class="review-options">
        <button v-for="option in current.options" :key="option.key" class="answer-option" :class="{ selected: selected.includes(option.key), correct: result && answers.includes(option.key), incorrect: result && selected.includes(option.key) && !answers.includes(option.key) }" :disabled="!!result || submitting || showingPaused" :aria-pressed="selected.includes(option.key)" @click="toggle(option.key)">
          <span class="option-marker">{{ option.key }}</span><span>{{ option.text }}</span><Check v-if="result && answers.includes(option.key)" :size="17" /><CircleX v-else-if="result && selected.includes(option.key) && !answers.includes(option.key)" :size="17" />
        </button>
      </div>
      <div v-if="showingPaused" class="review-actions"><button class="button button-outline" @click="showDue">返回到期队列</button><button class="button button-dark" :disabled="submitting" @click="setState('ACTIVE')">恢复并立即安排<RotateCcw :size="15" /></button></div>
      <template v-else-if="!result">
        <fieldset class="recall-rating">
          <legend>不看提示，刚才回忆答案的难度</legend>
          <label v-for="item in [{ value: 0, label: '忘记了' }, { value: 1, label: '困难' }, { value: 2, label: '一般' }, { value: 3, label: '轻松' }]" :key="item.value" class="rating-choice">
            <input v-model="rating" type="radio" name="recall-rating" :value="item.value" /><span>{{ item.label }}</span>
          </label>
          <small>答题错误时，下次间隔会缩短到 10 分钟；评分仍用于记录你的主观回忆。</small>
        </fieldset>
        <div class="review-actions"><button class="button button-outline" @click="setState('PAUSED')"><Pause :size="15" />暂停此题</button><button class="button button-dark" :disabled="!selected.length || rating === null || submitting" @click="submit">{{ submitting ? '提交中…' : '提交复习' }}<ArrowRight :size="15" /></button></div>
      </template>
      <div v-else class="review-result" :class="result.correct ? 'result-correct' : 'result-wrong'">
        <strong>{{ result.correct ? '回答正确' : '本次回答有误' }}</strong><p>{{ result.explanation || '服务端暂未提供解析。' }}</p>
        <p>下一次复习：{{ result.intervalMinutes }} 分钟后 · {{ new Date(result.nextDueAt).toLocaleString('zh-CN') }}</p>
        <p>连续达到“正确且一般/轻松”：{{ result.consecutiveSuccessfulReviews }} / 3{{ result.mastered ? '，已达到复习掌握条件' : '' }}</p>
        <button class="button button-dark" @click="next">{{ index + 1 < cards.length ? '下一道题' : '完成' }}<ArrowRight :size="15" /></button>
      </div>
      <div v-if="!showingPaused" class="review-footer"><button class="text-button" @click="router.push('/wrong-book')"><BookOpen :size="14" />错题整理</button><button class="text-button" @click="setState('MASTERED')">手动标记已掌握</button></div>
    </section>
    <section v-else class="empty-state"><CircleCheck :size="25" /><strong>{{ showingPaused ? '没有暂停的错题' : dueCards.length ? '本次到期复习已完成' : '目前没有到期复习' }}</strong><span>{{ dueCards.length ? '稍后刷新查看新到期任务。' : '错题答错后会进入复习队列。' }}</span><button v-if="showingPaused" class="button button-outline" @click="showDue">返回到期队列</button><button v-else class="button button-outline" @click="router.push('/study-plan')">查看每日计划<ArrowRight :size="15" /></button></section>
  </div>
</template>
