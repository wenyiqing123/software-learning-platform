<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, BookOpen, CheckCircle2, CircleAlert, Eye, GraduationCap, Layers, Network, RotateCw } from '@lucide/vue'
import { api, apiError, learningApi } from '../api/client'
import type { FlashcardItem } from '../api/flashcards'
import { getSpacedRatingPreviews } from '../domain/graphAlgorithms'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
import KbdBadge from '../components/KbdBadge.vue'

interface Course { id: number; name: string; term: string }

const route = useRoute()
const router = useRouter()

const courses = ref<Course[]>([])
const courseId = ref<number | undefined>(Number(route.query.courseId) || undefined)
const dueOnly = ref(false)
const cards = ref<FlashcardItem[]>([])
const currentIndex = ref(0)
const flipped = ref(false)
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const feedbackNotice = ref('')
const sessionReviewedCount = ref(0)

const ratingPreviews = getSpacedRatingPreviews()

const dueCount = computed(() => cards.value.filter((c) => c.due).length)
const currentCard = computed(() => cards.value[currentIndex.value] ?? null)

async function loadCourses() {
  try {
    courses.value = (await api.get<Course[]>('/courses')).data
  } catch {
    courses.value = []
  }
}

async function loadCards(keepIndex = false) {
  loading.value = true
  error.value = ''
  try {
    const list = await learningApi.flashcards({
      ...(courseId.value ? { courseId: courseId.value } : {}),
      dueOnly: dueOnly.value,
    })
    cards.value = Array.isArray(list) ? list : []
    if (!keepIndex || currentIndex.value >= cards.value.length) {
      currentIndex.value = 0
    }
    flipped.value = false
  } catch (cause) {
    error.value = apiError(cause, '主动回忆闪卡暂时无法加载，请稍后重试。')
    cards.value = []
  } finally {
    loading.value = false
  }
}

function toggleFlip() {
  if (!currentCard.value || submitting.value) return
  flipped.value = !flipped.value
}

function goPrev() {
  if (!cards.value.length) return
  currentIndex.value = (currentIndex.value - 1 + cards.value.length) % cards.value.length
  flipped.value = false
}

function goNext() {
  if (!cards.value.length) return
  currentIndex.value = (currentIndex.value + 1) % cards.value.length
  flipped.value = false
}

async function submitRecall(rating: 0 | 1 | 2 | 3) {
  const card = currentCard.value
  if (!card || submitting.value) return
  submitting.value = true
  error.value = ''
  feedbackNotice.value = ''
  try {
    const updated = await learningApi.reviewFlashcard(card.knowledgePointId, rating)
    sessionReviewedCount.value += 1
    const preview = ratingPreviews.find((p) => p.rating === rating)
    feedbackNotice.value = `已记录「${card.name}」回忆评价，安排于${preview?.correctText || '稍后'}再次复习`

    if (dueOnly.value && !updated.due) {
      cards.value = cards.value.filter((item) => item.knowledgePointId !== card.knowledgePointId)
      if (currentIndex.value >= cards.value.length) {
        currentIndex.value = 0
      }
      flipped.value = false
    } else {
      cards.value = cards.value.map((item) =>
        item.knowledgePointId === updated.knowledgePointId ? updated : item,
      )
      goNext()
    }
  } catch (cause) {
    error.value = apiError(cause, '保存闪卡复习结果失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

function onCourseFilterChange() {
  feedbackNotice.value = ''
  void loadCards(false)
}

function setDueOnlyMode(nextDueOnly: boolean) {
  if (dueOnly.value === nextDueOnly) return
  dueOnly.value = nextDueOnly
  feedbackNotice.value = ''
  void loadCards(false)
}

function openReading(card: FlashcardItem) {
  void router.push({
    path: '/courses',
    query: {
      courseId: String(card.courseId),
      knowledgePointId: String(card.knowledgePointId),
      ...(card.sectionId ? { sectionId: String(card.sectionId) } : {}),
    },
  })
}

function openGraph(card: FlashcardItem) {
  void router.push({
    path: '/knowledge',
    query: { entity: card.graphEntity || card.name, courseId: String(card.courseId) },
  })
}

function openFeynman(card: FlashcardItem) {
  void router.push({
    path: '/tutor',
    query: {
      activity: 'feynman',
      courseId: String(card.courseId),
      knowledgePointId: String(card.knowledgePointId),
      entity: card.graphEntity || card.name,
    },
  })
}

function onFlashcardKeydown(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null
  if (event.altKey || event.ctrlKey || event.metaKey || target?.closest('input, textarea, select, [contenteditable="true"]')) {
    return
  }
  if (!currentCard.value || loading.value) return
  if (event.code === 'Space') {
    event.preventDefault()
    toggleFlip()
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    goPrev()
    return
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    goNext()
    return
  }
  if (flipped.value && !submitting.value && ['1', '2', '3', '4'].includes(event.key)) {
    event.preventDefault()
    void submitRecall((Number(event.key) - 1) as 0 | 1 | 2 | 3)
  }
}

watch(
  () => route.query.courseId,
  (raw) => {
    const next = Number(raw) || undefined
    if (next !== courseId.value) {
      courseId.value = next
      void loadCards(false)
    }
  },
)

onMounted(async () => {
  window.addEventListener('keydown', onFlashcardKeydown)
  await loadCourses()
  await loadCards(false)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onFlashcardKeydown)
})
</script>

<template>
  <div class="page-stack flashcards-page">
    <SectionHeading
      eyebrow="主动回忆 · 检索巩固"
      title="概念闪卡"
      description="先在脑中尝试复述知识点核心定义与边界，再翻面核对小节学习目标并按回忆难易安排下一次间隔。"
    >
      <div class="flashcard-filters">
        <label class="flashcard-course-select">
          <span>课程</span>
          <select v-model.number="courseId" aria-label="闪卡筛选课程" @change="onCourseFilterChange">
            <option :value="undefined">全部课程</option>
            <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }} · {{ c.term }}</option>
          </select>
        </label>
        <div class="flashcard-mode-tabs" role="tablist" aria-label="闪卡范围切换">
          <button
            type="button"
            role="tab"
            class="flashcard-mode-tab"
            :class="{ active: !dueOnly }"
            :aria-selected="!dueOnly"
            data-testid="flashcards-tab-all"
            @click="setDueOnlyMode(false)"
          >
            全部卡片
          </button>
          <button
            type="button"
            role="tab"
            class="flashcard-mode-tab"
            :class="{ active: dueOnly }"
            :aria-selected="dueOnly"
            data-testid="flashcards-tab-due"
            @click="setDueOnlyMode(true)"
          >
            仅到期回忆
          </button>
        </div>
      </div>
    </SectionHeading>

    <section class="flashcard-metrics" aria-label="闪卡队列统计">
      <div class="flashcard-stat">
        <span>当前队列</span>
        <strong>{{ cards.length }} 张</strong>
      </div>
      <div class="flashcard-stat">
        <span>待到期回忆</span>
        <strong>{{ dueCount }} 张</strong>
      </div>
      <div class="flashcard-stat">
        <span>本轮已自评</span>
        <strong>{{ sessionReviewedCount }} 张</strong>
      </div>
      <span class="flashcard-shortcut-hint">
        <span>快捷键：</span>
        <KbdBadge :keys="['Space']" /><span>翻面</span>
        <KbdBadge :keys="['←', '→']" separator="/" /><span>切卡</span>
        <KbdBadge :keys="['1', '4']" separator="~" /><span>评分</span>
      </span>
    </section>

    <div v-if="error" class="inline-notice error-notice" role="alert">
      <CircleAlert :size="16" /><span>{{ error }}</span>
      <button class="text-button" @click="loadCards(true)">重试</button>
    </div>
    <div v-if="feedbackNotice" class="inline-notice success" role="status" data-testid="flashcards-notice">
      <CheckCircle2 :size="16" /><span>{{ feedbackNotice }}</span>
    </div>

    <FeedbackState
      v-if="loading"
      :loading="true"
      skeleton="flashcard"
      loading-title="正在整理知识点闪卡"
      loading-text="正在读取课程概念定义、学习目标与个人回忆记录…"
    />

    <FeedbackState
      v-else-if="!currentCard"
      :empty="true"
      :empty-title="dueOnly ? '太棒了！当前没有待回忆的概念闪卡' : '当前课程暂未配置启用知识点'"
      :empty-text="dueOnly ? '你可以切换为“全部卡片”主动复习，或前往每日计划与练习。' : '选择其他课程后再试。'"
      :empty-action-label="dueOnly ? '查看全部卡片' : ''"
      @action="setDueOnlyMode(false)"
    />

    <section v-else class="flashcard-stage-wrap" aria-label="闪卡交互练习区">
      <div class="flashcard-stage-top">
        <span>第 {{ currentIndex + 1 }} / {{ cards.length }} 张 · {{ currentCard.courseName }}</span>
        <span class="flashcard-due-badge" :class="{ 'is-due': currentCard.due }">
          {{ currentCard.due ? '待回忆' : '未到期' }} · 已练 {{ currentCard.reviewCount }} 次
        </span>
      </div>

      <div
        class="flashcard-card"
        :class="{ 'is-flipped': flipped }"
        role="button"
        tabindex="0"
        :aria-label="flipped ? '闪卡背面：点击切回正面' : '闪卡正面：点击翻面查看解析'"
        data-testid="flashcard-surface"
        @click="toggleFlip"
        @keydown.enter.prevent="toggleFlip"
      >
        <div v-if="!flipped" class="flashcard-face flashcard-front" data-testid="flashcard-front">
          <div class="flashcard-face-eyebrow">
            <Layers :size="15" />
            <span>{{ currentCard.chapterName || currentCard.courseName }}</span>
          </div>
          <h2 class="flashcard-concept">{{ currentCard.name }}</h2>
          <p class="flashcard-entity-pill">图谱实体：{{ currentCard.graphEntity || currentCard.name }}</p>
          <p class="flashcard-prompt-copy">请尝试不看提示，口头复述该概念的核心定义、工程用途及常见联系…</p>
          <span class="flashcard-flip-cta"><Eye :size="15" />点击卡片或按空格键查看参考要点</span>
        </div>

        <div v-else class="flashcard-face flashcard-back" data-testid="flashcard-back" @click.stop>
          <div class="flashcard-back-head">
            <div>
              <small>{{ currentCard.sectionTitle || currentCard.chapterName || currentCard.courseName }}</small>
              <h2>{{ currentCard.name }}</h2>
            </div>
            <button type="button" class="button button-outline button-small" @click="toggleFlip">
              <RotateCw :size="14" />翻回正面
            </button>
          </div>

          <div class="flashcard-summary-box">
            <strong>核心要点</strong>
            <p>{{ currentCard.summary || '可结合对应课程小节与知识图谱回顾详细解释。' }}</p>
          </div>

          <div v-if="currentCard.objectives.length" class="flashcard-objectives">
            <strong>关联学习目标</strong>
            <ul>
              <li v-for="(obj, oIdx) in currentCard.objectives" :key="oIdx">{{ obj }}</li>
            </ul>
          </div>

          <div class="flashcard-links">
            <button type="button" class="text-button" @click="openReading(currentCard)"><BookOpen :size="14" />课程阅读</button>
            <button type="button" class="text-button" @click="openGraph(currentCard)"><Network :size="14" />知识图谱</button>
            <button type="button" class="text-button" @click="openFeynman(currentCard)"><GraduationCap :size="14" />费曼试讲</button>
          </div>
        </div>
      </div>

      <div class="flashcard-controls">
        <button type="button" class="button button-outline" :disabled="cards.length <= 1" data-testid="flashcard-prev" @click="goPrev">
          <ArrowLeft :size="15" />上一张
        </button>

        <div v-if="!flipped" class="flashcard-reveal-wrap">
          <button type="button" class="button button-dark" data-testid="flashcard-flip-btn" @click="toggleFlip">
            <Eye :size="15" />显示答案并自评
          </button>
        </div>
        <div v-else class="flashcard-rating-grid" role="group" aria-label="主动回忆自评反馈">
          <button
            v-for="item in ratingPreviews"
            :key="item.rating"
            type="button"
            class="flashcard-rate-btn"
            :class="`rate-${item.rating}`"
            :disabled="submitting"
            :data-testid="`flashcard-rate-${item.rating}`"
            @click="submitRecall(item.rating)"
          >
            <strong>{{ item.rating + 1 }}. {{ item.label }}</strong>
            <small>{{ item.correctText }}</small>
          </button>
        </div>

        <button type="button" class="button button-outline" :disabled="cards.length <= 1" data-testid="flashcard-next" @click="goNext">
          下一张<ArrowRight :size="15" />
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.flashcards-page { min-width: 0; }
.flashcard-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.flashcard-course-select { display: inline-flex; align-items: center; gap: 8px; color: var(--text-muted); font-size: 13px; }
.flashcard-course-select select { min-height: var(--control-height-small); }
.flashcard-mode-tabs { display: inline-flex; gap: 4px; padding: 3px; background: var(--surface); border: 1px solid var(--border-default); border-radius: var(--radius-sm); }
.flashcard-mode-tab { min-height: 28px; padding: 4px 12px; border: 0; border-radius: var(--radius-xs); background: transparent; color: var(--text-muted); font-size: 12.5px; font-weight: 600; cursor: pointer; }
.flashcard-mode-tab.active { color: var(--accent-strong); background: var(--accent-soft); }

.flashcard-metrics { display: flex; flex-wrap: wrap; align-items: center; gap: 12px 24px; padding: 12px 16px; background: var(--surface-tint); border-radius: var(--radius-md); }
.flashcard-stat { display: flex; align-items: baseline; gap: 8px; font-size: 13px; color: var(--text-muted); }
.flashcard-stat strong { font-size: 16px; color: var(--text); font-variant-numeric: tabular-nums; }
.flashcard-shortcut-hint { margin-left: auto; display: inline-flex; flex-wrap: wrap; align-items: center; gap: 6px; font-size: 12px; color: var(--text-subtle); }

.flashcard-stage-wrap { display: grid; gap: 14px; max-width: 820px; width: 100%; margin: 0 auto; }
.flashcard-stage-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--text-muted); font-size: 13px; font-variant-numeric: tabular-nums; }
.flashcard-due-badge { padding: 2px 10px; border-radius: var(--radius-pill); background: var(--surface-tint); color: var(--text-muted); font-size: 12px; font-weight: 600; }
.flashcard-due-badge.is-due { background: var(--warning-soft); color: var(--warning-strong); }

.flashcard-card { min-height: 300px; padding: 28px 28px; background: var(--surface); border: 1px solid var(--border-default); border-radius: var(--radius-md); box-shadow: var(--shadow-sm); display: flex; flex-direction: column; justify-content: center; cursor: pointer; transition: border-color var(--motion), box-shadow var(--motion); }
.flashcard-card:hover { border-color: var(--border-focus); }
.flashcard-card.is-flipped { cursor: default; justify-content: flex-start; }

.flashcard-face { width: 100%; min-width: 0; }
.flashcard-front { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 14px; }
.flashcard-face-eyebrow { display: inline-flex; align-items: center; gap: 6px; color: var(--text-subtle); font-size: 12.5px; font-weight: 600; }
.flashcard-concept { margin: 0; font-size: 26px; color: var(--text); line-height: 1.3; }
.flashcard-entity-pill { margin: 0; padding: 4px 12px; border-radius: var(--radius-pill); background: var(--accent-soft); color: var(--accent-strong); font-size: 12.5px; font-weight: 600; }
.flashcard-prompt-copy { margin: 4px 0 0; max-width: 52ch; color: var(--text-muted); font-size: 14px; line-height: 1.65; }
.flashcard-flip-cta { margin-top: 8px; display: inline-flex; align-items: center; gap: 6px; color: var(--accent-strong); font-size: 13px; font-weight: 600; }

.flashcard-back { display: grid; gap: 16px; }
.flashcard-back-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--border-soft); }
.flashcard-back-head small { color: var(--text-subtle); font-size: 12px; }
.flashcard-back-head h2 { margin: 4px 0 0; font-size: 20px; color: var(--text); }
.flashcard-summary-box,
.flashcard-objectives { display: grid; gap: 6px; }
.flashcard-summary-box strong,
.flashcard-objectives strong { font-size: 13px; color: var(--accent-strong); }
.flashcard-summary-box p { margin: 0; color: var(--text); font-size: 14.5px; line-height: 1.7; }
.flashcard-objectives ul { margin: 0; padding-left: 18px; color: var(--text-muted); font-size: 13.5px; line-height: 1.65; }
.flashcard-links { display: flex; flex-wrap: wrap; gap: 14px; padding-top: 10px; border-top: 1px solid var(--border-soft); }

.flashcard-controls { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }
.flashcard-reveal-wrap { flex: 1; display: flex; justify-content: center; }
.flashcard-rating-grid { flex: 1; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.flashcard-rate-btn { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 8px 6px; border: 1px solid var(--border-default); border-radius: var(--radius-sm); background: var(--surface); color: var(--text); cursor: pointer; transition: border-color var(--motion), background var(--motion); }
.flashcard-rate-btn strong { font-size: 13px; }
.flashcard-rate-btn small { font-size: 11px; color: var(--text-muted); }
.flashcard-rate-btn.rate-0:hover { border-color: var(--danger); background: var(--danger-soft); color: var(--danger); }
.flashcard-rate-btn.rate-1:hover { border-color: var(--warning); background: var(--warning-soft); color: var(--warning-strong); }
.flashcard-rate-btn.rate-2:hover,
.flashcard-rate-btn.rate-3:hover { border-color: var(--border-focus); background: var(--accent-soft); color: var(--accent-strong); }

@media (max-width: 720px) {
  .flashcard-shortcut-hint { width: 100%; margin-left: 0; }
  .flashcard-rating-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); width: 100%; order: -1; }
}
</style>
