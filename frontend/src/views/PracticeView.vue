<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, CircleHelp, Flag, Lightbulb, ListChecks, Sparkles } from '@lucide/vue'
import { learningApi, type QuizDiagnosis } from '../api/client'
import { useLearningMetrics } from '../composables/useLearningMetrics'
import SectionHeading from '../components/SectionHeading.vue'
import LearningContextActions from '../components/LearningContextActions.vue'
import PracticeQuestionCard from '../components/PracticeQuestionCard.vue'
import {
  buildNavigatorSlots,
  computeReviewMatrix,
  computeSessionSummary,
  type PracticeQuestionOutcome,
  type PracticeReviewFilter,
} from '../domain/practiceSession'

interface QuizOption { key: string; text: string }
interface QuizQuestion { id: number; stem: string; type: 'SINGLE' | 'MULTIPLE' | 'TRUE_FALSE' | 'JUDGE'; options: QuizOption[]; knowledgePoint?: string }
interface SubmitResult { questionId: number; correct: boolean; correctAnswer: string[]; explanation?: string }
interface WrongItem { id: number; questionId: number; status?: string }
class QuestionLoadError extends Error {}
const route = useRoute()
const router = useRouter()
const { overview } = useLearningMetrics()
const questions = ref<QuizQuestion[]>([])
const index = ref(0)
const selected = ref<string[]>([])
const result = ref<SubmitResult | null>(null)
const loadingQuestions = ref(true)
const submitting = ref(false)
const serviceError = ref('')
const submitError = ref('')
const wrongItemId = ref<number | undefined>()
const submissions = ref<boolean[]>([])
const diagnoses = ref<QuizDiagnosis[]>([])
const diagnosisLoading = ref(false)
const diagnosisError = ref('')
const draftsById = ref<Record<number, string[]>>({})
const outcomesById = ref<Record<number, PracticeQuestionOutcome>>({})
const flaggedIds = ref<number[]>([])
const diagnosesById = ref<Record<number, QuizDiagnosis[]>>({})
const reviewFilter = ref<PracticeReviewFilter>('all')
let loadId = 0
const question = computed(() => questions.value[index.value])
const options = computed(() => question.value?.options || [])
const progress = computed(() => questions.value.length ? ((index.value + 1) / questions.value.length) * 100 : 0)
const isMultiple = computed(() => question.value?.type === 'MULTIPLE')
const isTrueFalse = computed(() => question.value?.type === 'TRUE_FALSE' || question.value?.type === 'JUDGE')
const questionType = computed(() => ({ SINGLE: '单选题', MULTIPLE: '多选题', TRUE_FALSE: '判断题', JUDGE: '判断题' }[question.value?.type || 'SINGLE']))
const correctAnswers = computed(() => result.value?.correctAnswer || [])
const sessionAccuracy = computed(() => submissions.value.length ? Math.round(submissions.value.filter(Boolean).length / submissions.value.length * 100) + '%' : '—')
const targeted = computed(() => route.query.questionId !== undefined || route.query.wrongItemId !== undefined)
const batchIds = computed(() => parseQuestionIds(route.query.questionIds))
const isBatch = computed(() => batchIds.value.length > 0)
const batchCompleted = ref(false)
const batchOutcomes = ref<Array<{ questionId: number; stem: string; knowledgePoint?: string; correct: boolean }>>([])
const navigatorSlots = computed(() =>
  buildNavigatorSlots(questions.value, {
    currentIndex: index.value,
    draftsById: draftsById.value,
    outcomesById: outcomesById.value,
    flaggedIds: flaggedIds.value,
  }),
)
const sessionSummary = computed(() =>
  computeSessionSummary(questions.value, outcomesById.value, flaggedIds.value),
)
const reviewEntries = computed(() =>
  computeReviewMatrix(questions.value, outcomesById.value, flaggedIds.value, reviewFilter.value),
)
const isCurrentFlagged = computed(() =>
  question.value ? flaggedIds.value.includes(question.value.id) : false,
)
const batchCorrectCount = computed(() => sessionSummary.value.correctCount)
const batchAccuracy = computed(() => sessionSummary.value.accuracyLabel)
const missedPoints = computed(() => sessionSummary.value.missedPoints)

function parseQuestionIds(value: unknown): number[] {
  if (typeof value !== 'string' || !value.trim()) return []
  const ids = value.split(',').map(part => part.trim()).filter(part => /^[1-9]\d*$/.test(part)).map(Number)
  return [...new Set(ids)].filter(id => Number.isSafeInteger(id) && id > 0)
}

function queryId(value: unknown): number | undefined {
  if (value === undefined) return undefined
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value))) throw new QuestionLoadError('练习链接参数无效，请从课程或错题列表重新进入。')
  return Number(value)
}

async function loadQuestions() {
  const request = ++loadId
  questions.value = []
  index.value = 0
  selected.value = []
  result.value = null
  wrongItemId.value = undefined
  submissions.value = []
  batchCompleted.value = false
  batchOutcomes.value = []
  draftsById.value = {}
  outcomesById.value = {}
  flaggedIds.value = []
  diagnosesById.value = {}
  reviewFilter.value = 'all'
  diagnoses.value = []
  diagnosisError.value = ''
  serviceError.value = ''
  submitError.value = ''
  loadingQuestions.value = true
  submitting.value = false
  try {
    const pointId = queryId(route.query.knowledgePointId)
    let targetId = queryId(route.query.questionId)
    const reviewId = queryId(route.query.wrongItemId)
    if (reviewId !== undefined) {
      const wrongItems: WrongItem[] = await learningApi.wrongBook()
      if (request !== loadId) return
      const item = wrongItems.find(item => item.id === reviewId && item.status !== 'MASTERED')
      if (!item || (targetId !== undefined && item.questionId !== targetId)) throw new QuestionLoadError('该复习链接不可用，错题可能已掌握，请从错题列表重新进入。')
      targetId = item.questionId
    }
    const available: QuizQuestion[] = await learningApi.questions(pointId)
    if (request !== loadId) return
    if (!Array.isArray(available)) throw new QuestionLoadError('题库返回的数据不可用，请稍后重试。')
    let filtered = targetId === undefined ? available : available.filter(item => item.id === targetId)
    if (targetId === undefined && batchIds.value.length) {
      const order = new Map(batchIds.value.map((id, orderIndex) => [id, orderIndex]))
      filtered = available.filter(item => order.has(item.id)).sort((a, b) => (order.get(a.id) ?? 0) - (order.get(b.id) ?? 0))
      if (!filtered.length) throw new QuestionLoadError('未找到该题单中的有效题目，请重新从专项练习或错题列表进入。')
    }
    if (targetId !== undefined && !filtered.length) throw new QuestionLoadError('未找到目标题目，题目可能已停用或不属于所选知识点。')
    questions.value = filtered
    wrongItemId.value = reviewId
  } catch (cause) {
    if (request === loadId) serviceError.value = cause instanceof QuestionLoadError
      ? cause.message : '题库服务暂不可用，请检查网络后重试。'
  } finally {
    if (request === loadId) loadingQuestions.value = false
  }
}

function onAnswerKey(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null
  if (event.altKey || event.ctrlKey || event.metaKey || target?.closest('input, textarea, select, button, a, [contenteditable="true"]') || !/^[1-9]$/.test(event.key)) return
  const option = options.value[Number(event.key) - 1]
  if (option && !submitting.value && !result.value) { event.preventDefault(); toggleOption(option.key) }
}
function persistDraftForCurrent() {
  if (!question.value || result.value) return
  if (selected.value.length) {
    draftsById.value = { ...draftsById.value, [question.value.id]: [...selected.value] }
  } else {
    const next = { ...draftsById.value }
    delete next[question.value.id]
    draftsById.value = next
  }
}

function goToQuestion(targetIndex: number) {
  if (targetIndex < 0 || targetIndex >= questions.value.length) return
  persistDraftForCurrent()
  batchCompleted.value = false
  index.value = targetIndex
  submitError.value = ''
  diagnosisError.value = ''
  const targetQuestion = questions.value[targetIndex]
  const outcome = outcomesById.value[targetQuestion.id]
  if (outcome) {
    selected.value = [...outcome.selected]
    result.value = {
      questionId: outcome.questionId,
      correct: outcome.correct,
      correctAnswer: outcome.correctAnswer,
      explanation: outcome.explanation,
    }
    diagnoses.value = diagnosesById.value[targetQuestion.id] ?? []
  } else {
    selected.value = [...(draftsById.value[targetQuestion.id] ?? [])]
    result.value = null
    diagnoses.value = []
  }
}

function toggleFlagCurrent() {
  const q = question.value
  if (!q) return
  flaggedIds.value = flaggedIds.value.includes(q.id)
    ? flaggedIds.value.filter(id => id !== q.id)
    : [...flaggedIds.value, q.id]
}

function toggleOption(key: string) {
  if (submitting.value || result.value) return
  selected.value = isMultiple.value ? (selected.value.includes(key) ? selected.value.filter(item => item !== key) : [...selected.value, key]) : [key]
  persistDraftForCurrent()
  submitError.value = ''
}
async function submit() {
  if (!question.value || !selected.value.length || submitting.value || result.value) return
  const request = loadId
  const questionId = question.value.id
  const answer = [...selected.value]
  submitting.value = true
  submitError.value = ''
  try {
    const response: SubmitResult = wrongItemId.value !== undefined
      ? await learningApi.reviewWrong(wrongItemId.value, answer)
      : await learningApi.submitAnswer(questionId, answer)
    window.dispatchEvent(new Event('learning:changed'))
    if (request !== loadId) return
    if (!response || response.questionId !== questionId || typeof response.correct !== 'boolean' || !Array.isArray(response.correctAnswer)) throw new Error('Invalid submission response')
    result.value = response
    submissions.value.push(response.correct)
    const currentQ = question.value
    const savedOutcome: PracticeQuestionOutcome = {
      questionId,
      stem: currentQ?.stem || '',
      knowledgePoint: currentQ?.knowledgePoint,
      selected: answer,
      correct: response.correct,
      correctAnswer: response.correctAnswer,
      explanation: response.explanation,
    }
    outcomesById.value = { ...outcomesById.value, [questionId]: savedOutcome }
    const nextDrafts = { ...draftsById.value }
    delete nextDrafts[questionId]
    draftsById.value = nextDrafts
    const nextOutcome = { questionId, stem: currentQ?.stem || '', knowledgePoint: currentQ?.knowledgePoint, correct: response.correct }
    const existingIdx = batchOutcomes.value.findIndex(item => item.questionId === questionId)
    if (existingIdx >= 0) batchOutcomes.value.splice(existingIdx, 1, nextOutcome)
    else batchOutcomes.value.push(nextOutcome)
    if (!response.correct) await loadDiagnosis(questionId, answer, request)
  } catch {
    if (request === loadId) submitError.value = '暂时无法确认提交结果，当前未显示判分。请检查连接后重试，或返回概览查看最新答题记录。'
  } finally {
    if (request === loadId) submitting.value = false
  }
}
async function loadDiagnosis(questionId: number, answer: string[], request: number) {
  diagnosisLoading.value = true
  diagnosisError.value = ''
  diagnoses.value = []
  try {
    const matches = await learningApi.diagnoseAnswer(questionId, answer)
    if (request === loadId) {
      const safeMatches = Array.isArray(matches) ? matches : []
      diagnoses.value = safeMatches
      diagnosesById.value = { ...diagnosesById.value, [questionId]: safeMatches }
    }
  } catch {
    if (request === loadId) diagnosisError.value = '暂时无法读取错因关联，请稍后重试。'
  } finally {
    if (request === loadId) diagnosisLoading.value = false
  }
}
function openReviewSummary() {
  persistDraftForCurrent()
  reviewFilter.value = 'all'
  batchCompleted.value = true
}
function nextQuestion() {
  if (targeted.value) { void router.push(wrongItemId.value !== undefined ? '/wrong-book' : '/recommendations'); return }
  if (index.value + 1 >= questions.value.length) {
    if (isBatch.value || questions.value.length > 1) {
      openReviewSummary()
      return
    }
    void router.push('/recommendations')
    return
  }
  goToQuestion(index.value + 1)
}
function retryQuestion() {
  const q = question.value
  if (q) {
    const nextOutcomes = { ...outcomesById.value }
    delete nextOutcomes[q.id]
    outcomesById.value = nextOutcomes
    const nextDiag = { ...diagnosesById.value }
    delete nextDiag[q.id]
    diagnosesById.value = nextDiag
  }
  selected.value = []
  result.value = null
  diagnoses.value = []
  diagnosisError.value = ''
  submitError.value = ''
}
function restartBatch() { void loadQuestions() }
function askTutorForQuestion(entry: { stem: string; knowledgePoint?: string; outcome?: PracticeQuestionOutcome }) {
  const chosen = entry.outcome?.selected?.join('、') || '未选'
  const expected = entry.outcome?.correctAnswer?.join('、') || ''
  void router.push({
    path: '/tutor',
    query: {
      ...(entry.knowledgePoint ? { entity: entry.knowledgePoint } : {}),
      ...(route.query.knowledgePointId ? { knowledgePointId: String(route.query.knowledgePointId) } : {}),
      prompt: `我在练习时遇到以下题目想进一步弄清：\n题干：${entry.stem}\n我的作答：${chosen}${expected ? `\n参考答案：${expected}` : ''}\n请帮我结合知识图谱梳理关键辨析点。`,
    },
  })
}
function openKnowledge() {
  void router.push({ path: '/knowledge', query: question.value?.knowledgePoint ? { entity: question.value.knowledgePoint } : undefined })
}
watch(() => [route.query.knowledgePointId, route.query.questionId, route.query.wrongItemId, route.query.questionIds], () => { void loadQuestions() }, { immediate: true })
onMounted(() => window.addEventListener('keydown', onAnswerKey))
onBeforeUnmount(() => { loadId++; window.removeEventListener('keydown', onAnswerKey) })
</script>

<template>
  <div class="page-stack">
    <SectionHeading :eyebrow="wrongItemId !== undefined ? '错题定向复习' : '课程练习'" :title="wrongItemId !== undefined ? '重新练习这道错题' : '课程练习'" description="提交后由服务端判分，答题记录和错题状态以保存结果为准。" />
    <div v-if="serviceError" class="inline-notice" role="alert"><CircleHelp :size="16" />{{ serviceError }}<button class="text-button" @click="loadQuestions">重试</button><button v-if="targeted" class="text-button" @click="router.push('/wrong-book')">返回错题列表</button></div>
    <div v-if="loadingQuestions" class="loading-row"><span class="spinner"></span>正在加载练习题目</div>
    <section v-else-if="batchCompleted" class="content-section practice-batch-summary" data-testid="practice-batch-summary">
      <div class="batch-summary-header">
        <div>
          <p class="eyebrow">连续练习总结</p>
          <h2>本轮题单复盘与知识点巩固</h2>
        </div>
        <button type="button" class="text-button" data-testid="practice-back-to-quiz" @click="goToQuestion(index)">
          返回做题视图
        </button>
      </div>
      <div class="batch-summary-stats">
        <div><span>作答题数</span><strong>{{ sessionSummary.answeredCount }} / {{ questions.length }} 题</strong></div>
        <div><span>答对题数</span><strong>{{ batchCorrectCount }} 题</strong></div>
        <div><span>本轮正确率</span><strong>{{ batchAccuracy }}</strong></div>
        <div><span>标记存疑</span><strong>{{ sessionSummary.flaggedCount }} 题</strong></div>
      </div>
      <div v-if="missedPoints.length" class="batch-missed-points">
        <span>建议巩固知识点：</span>
        <button
          v-for="point in missedPoints"
          :key="point"
          type="button"
          class="notes-tag-pill-btn"
          @click="router.push({ path: '/knowledge', query: { entity: point } })"
        >
          {{ point }}
        </button>
      </div>

      <section class="practice-review-panel" data-testid="practice-review-panel" aria-label="逐题复盘清单">
        <div class="practice-review-head">
          <div class="practice-review-title">
            <ListChecks :size="16" aria-hidden="true" />
            <strong>逐题复盘清单</strong>
          </div>
          <div class="practice-review-filters" role="group" aria-label="复盘题目筛选">
            <button type="button" class="review-filter-pill" :class="{ active: reviewFilter === 'all' }" data-testid="review-filter-all" @click="reviewFilter = 'all'">
              全部 ({{ questions.length }})
            </button>
            <button type="button" class="review-filter-pill" :class="{ active: reviewFilter === 'wrong' }" data-testid="review-filter-wrong" @click="reviewFilter = 'wrong'">
              仅看错题 ({{ sessionSummary.wrongCount }})
            </button>
            <button type="button" class="review-filter-pill" :class="{ active: reviewFilter === 'flagged' }" data-testid="review-filter-flagged" @click="reviewFilter = 'flagged'">
              仅看存疑 ({{ sessionSummary.flaggedCount }})
            </button>
          </div>
        </div>

        <div v-if="!reviewEntries.length" class="practice-review-empty">当前筛选条件下暂无题目。</div>
        <div v-else class="practice-review-list">
          <article
            v-for="entry in reviewEntries"
            :key="entry.questionId"
            class="practice-review-item"
            :data-testid="`review-entry-${entry.questionId}`"
          >
            <div class="review-item-meta">
              <span class="review-index-tag">Q{{ entry.numberLabel }}</span>
              <span v-if="entry.knowledgePoint" class="review-kp-tag">{{ entry.knowledgePoint }}</span>
              <span v-if="entry.flagged" class="review-flag-tag"><Flag :size="11" aria-hidden="true" /> 存疑</span>
              <span
                class="review-status-badge"
                :class="!entry.outcome ? 'is-pending' : entry.outcome.correct ? 'is-correct' : 'is-wrong'"
              >
                {{ !entry.outcome ? '未提交' : entry.outcome.correct ? '回答正确' : '回答有误' }}
              </span>
            </div>
            <p class="review-item-stem">{{ entry.stem }}</p>
            <div v-if="entry.outcome" class="review-item-detail">
              <span>我的选择：<strong>{{ entry.outcome.selected.join('、') }}</strong></span>
              <span>正确答案：<strong>{{ entry.outcome.correctAnswer.join('、') }}</strong></span>
              <p v-if="entry.outcome.explanation" class="review-item-explanation">{{ entry.outcome.explanation }}</p>
            </div>
            <p v-else class="review-item-pending-note">本题尚未提交判分，提交后才会展示参考答案与解析。</p>
            <div class="review-item-actions">
              <button type="button" class="text-button" @click="goToQuestion(entry.index)">
                {{ entry.outcome ? '回看本题' : '去作答此题' }}
              </button>
              <button v-if="entry.outcome" type="button" class="text-button" @click="askTutorForQuestion(entry)">
                <Sparkles :size="13" aria-hidden="true" />带题干问助教
              </button>
            </div>
          </article>
        </div>
      </section>

      <div class="batch-summary-actions">
        <button class="button button-dark" @click="restartBatch">重刷本组题目</button>
        <button class="button button-outline" @click="router.push('/wrong-book')">查看错题本</button>
        <button class="button button-outline" @click="router.push('/review')">打开到期复习</button>
        <button class="text-button" @click="router.push('/recommendations')">查看学习建议 <ArrowRight :size="14" /></button>
      </div>
    </section>
    <template v-else-if="question">
      <section v-if="questions.length > 1" class="practice-navigator" data-testid="practice-navigator" aria-label="答题卡题号导航">
        <div class="navigator-head">
          <div class="navigator-legend">
            <strong>答题卡</strong>
            <span>已答 {{ sessionSummary.answeredCount }}/{{ sessionSummary.total }}</span>
            <span v-if="sessionSummary.flaggedCount">存疑 {{ sessionSummary.flaggedCount }}</span>
            <i class="legend-chip is-draft">待提交</i>
            <i class="legend-chip is-correct">对</i>
            <i class="legend-chip is-wrong">错</i>
            <i class="legend-chip is-flagged">存疑</i>
          </div>
          <button
            v-if="sessionSummary.answeredCount > 0"
            type="button"
            class="button button-outline button-small"
            data-testid="practice-open-review-btn"
            @click="openReviewSummary"
          >
            <ListChecks :size="14" aria-hidden="true" />
            <span>查看本轮复盘</span>
          </button>
        </div>
        <div class="navigator-slots">
          <button
            v-for="slot in navigatorSlots"
            :key="slot.questionId"
            type="button"
            class="navigator-slot-btn"
            :class="[`status-${slot.status}`, { current: slot.isCurrent, flagged: slot.flagged }]"
            :aria-label="`第 ${slot.index + 1} 题${slot.flagged ? '（已标记存疑）' : ''}`"
            :aria-current="slot.isCurrent ? 'step' : undefined"
            :data-testid="`practice-nav-slot-${slot.index + 1}`"
            @click="goToQuestion(slot.index)"
          >
            <span>{{ slot.numberLabel }}</span>
            <i v-if="slot.flagged" class="slot-flag-dot" aria-hidden="true"></i>
          </button>
        </div>
      </section>
      <section class="quiz-layout">
        <PracticeQuestionCard
          :question="question"
          :index="index"
          :total="questions.length"
          :question-type="questionType"
          :progress="progress"
          :is-multiple="isMultiple"
          :is-true-false="isTrueFalse"
          :selected="selected"
          :correct-answers="correctAnswers"
          :result="result"
          :submitting="submitting"
          :submit-error="submitError"
          :wrong-item-id="wrongItemId"
          :diagnoses="diagnoses"
          :diagnosis-loading="diagnosisLoading"
          :diagnosis-error="diagnosisError"
          :is-batch="isBatch"
          :flagged="isCurrentFlagged"
          :show-flag-button="questions.length > 1"
          @toggle="toggleOption"
          @submit="submit"
          @retry-submit="submit"
          @retry-question="retryQuestion"
          @next="nextQuestion"
          @toggle-flag="toggleFlagCurrent"
        />
        <aside class="quiz-side"><div class="quiz-side-mark"><Lightbulb :size="19" /></div><p class="eyebrow">学习提示</p><h3>{{ question.knowledgePoint || '先理解，再作答' }}</h3><p>先独立作答，再查看服务端返回的正确答案与解析。需要补充概念时，可以打开关联知识图谱。</p><button class="text-button" @click="openKnowledge">查看相关知识 <ArrowRight :size="14" /></button><LearningContextActions compact :context="{ knowledgePointId: Number(route.query.knowledgePointId) || undefined, entity: question.knowledgePoint }" /><div class="side-separator"></div><div class="side-stat"><span>本次正确率</span><strong>{{ sessionAccuracy }}</strong></div><div class="side-stat"><span>本次已保存</span><strong>{{ submissions.length }} 次</strong></div><div class="side-stat"><span>待复习错题</span><strong>{{ overview?.wrongCount ?? '—' }}</strong></div></aside>
      </section>
    </template>
    <div v-else-if="!serviceError" class="empty-state"><CircleHelp :size="24" /><strong>当前范围暂无可用题目</strong><span>可返回课程列表选择其他知识点。</span><button class="text-button" @click="router.push('/courses')">查看课程 <ArrowRight :size="14" /></button></div>
  </div>
</template>

<style scoped>
.practice-batch-summary {
  display: grid;
  gap: 16px;
}
.practice-batch-summary h2 {
  margin: 0;
  font-size: 20px;
}
.batch-summary-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px 12px;
}
.batch-summary-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.practice-navigator {
  display: grid;
  gap: 10px;
  padding: 12px 16px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-md);
}
.navigator-head,
.navigator-legend,
.practice-review-head,
.practice-review-title,
.practice-review-filters,
.review-item-meta,
.review-item-detail,
.review-item-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
}
.navigator-head,
.practice-review-head {
  justify-content: space-between;
}
.navigator-legend {
  font-size: 12px;
  color: var(--text-muted);
}
.navigator-legend strong {
  color: var(--text);
  font-size: 13px;
}
.legend-chip {
  font-style: normal;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  border: 1px solid var(--border-default);
  background: var(--surface);
}
.legend-chip.is-draft {
  background: var(--info-soft);
  color: var(--info);
}
.legend-chip.is-correct {
  background: var(--accent-soft);
  color: var(--accent-strong);
}
.legend-chip.is-wrong {
  background: var(--danger-soft);
  color: var(--danger);
}
.legend-chip.is-flagged {
  background: var(--warning-soft);
  color: var(--warning);
}
.navigator-slots {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.navigator-slot-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}
.navigator-slot-btn.status-draft {
  background: var(--info-soft);
  color: var(--info);
  border-color: var(--info);
}
.navigator-slot-btn.status-correct {
  background: var(--accent-soft);
  color: var(--accent-strong);
  border-color: var(--border-focus);
}
.navigator-slot-btn.status-wrong {
  background: var(--danger-soft);
  color: var(--danger);
  border-color: var(--danger-border);
}
.navigator-slot-btn.flagged {
  border-color: var(--warning);
}
.navigator-slot-btn.current {
  box-shadow: var(--focus-ring);
  color: var(--text);
  border-color: var(--accent);
}
.slot-flag-dot {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--warning);
}
.practice-review-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-md);
}
.practice-review-title {
  color: var(--text);
  font-size: 14px;
}
.review-filter-pill {
  min-height: 28px;
  padding: 3px 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
}
.review-filter-pill.active {
  background: var(--accent-soft);
  color: var(--accent-strong);
  border-color: var(--border-focus);
  font-weight: 600;
}
.practice-review-empty {
  font-size: 13px;
  color: var(--text-muted);
  padding: 8px 0;
}
.practice-review-list {
  display: grid;
  gap: 10px;
}
.practice-review-item {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
}
.review-index-tag {
  font-weight: 700;
  font-size: 12px;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.review-kp-tag,
.review-flag-tag,
.review-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-size: 11.5px;
  font-weight: 600;
}
.review-kp-tag {
  background: var(--surface-tint);
  color: var(--text-muted);
}
.review-flag-tag {
  background: var(--warning-soft);
  color: var(--warning);
}
.review-status-badge.is-correct {
  background: var(--accent-soft);
  color: var(--accent-strong);
}
.review-status-badge.is-wrong {
  background: var(--danger-soft);
  color: var(--danger);
}
.review-status-badge.is-pending {
  background: var(--surface-tint);
  color: var(--text-muted);
}
.review-item-stem {
  margin: 0;
  font-size: 14px;
  color: var(--text);
  line-height: 1.55;
}
.review-item-detail,
.review-item-pending-note {
  margin: 0;
  font-size: 12.5px;
  color: var(--text-muted);
}
.review-item-explanation {
  flex-basis: 100%;
  margin: 2px 0 0;
  line-height: 1.55;
}
.batch-summary-stats > div {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  background: var(--surface-soft);
  border-radius: var(--radius-sm);
}
.batch-summary-stats span {
  color: var(--text-muted);
  font-size: 12px;
}
.batch-summary-stats strong {
  color: var(--text);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}
.batch-missed-points {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-muted);
}
.notes-tag-pill-btn {
  min-height: 28px;
  padding: 3px 10px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border: 1px solid var(--border-focus);
  border-radius: var(--radius-pill);
  font-size: 12px;
  cursor: pointer;
}
.batch-summary-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
@media (max-width: 620px) {
  .batch-summary-stats {
    grid-template-columns: 1fr;
  }
}
</style>
