<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowRight, Check, CircleHelp, CircleX, Lightbulb, CircleCheck } from '@lucide/vue'
import { learningApi, type QuizDiagnosis } from '../api/client'
import { useLearningMetrics } from '../composables/useLearningMetrics'
import SectionHeading from '../components/SectionHeading.vue'
import LearningContextActions from '../components/LearningContextActions.vue'
import PracticeQuestionCard from '../components/PracticeQuestionCard.vue'

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
    const filtered = targetId === undefined ? available : available.filter(item => item.id === targetId)
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
function toggleOption(key: string) {
  if (submitting.value || result.value) return
  selected.value = isMultiple.value ? (selected.value.includes(key) ? selected.value.filter(item => item !== key) : [...selected.value, key]) : [key]
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
    if (request === loadId) diagnoses.value = Array.isArray(matches) ? matches : []
  } catch {
    if (request === loadId) diagnosisError.value = '暂时无法读取错因关联，请稍后重试。'
  } finally {
    if (request === loadId) diagnosisLoading.value = false
  }
}
function nextQuestion() {
  if (targeted.value) { void router.push(wrongItemId.value !== undefined ? '/wrong-book' : '/recommendations'); return }
  if (index.value + 1 >= questions.value.length) { void router.push('/recommendations'); return }
  index.value++
  selected.value = []
  result.value = null
  submitError.value = ''
}
function retryQuestion() { selected.value = []; result.value = null; diagnoses.value = []; diagnosisError.value = ''; submitError.value = '' }
function openKnowledge() {
  void router.push({ path: '/knowledge', query: question.value?.knowledgePoint ? { entity: question.value.knowledgePoint } : undefined })
}
watch(() => [route.query.knowledgePointId, route.query.questionId, route.query.wrongItemId], () => { void loadQuestions() }, { immediate: true })
onMounted(() => window.addEventListener('keydown', onAnswerKey))
onBeforeUnmount(() => { loadId++; window.removeEventListener('keydown', onAnswerKey) })
</script>

<template>
  <div class="page-stack">
    <SectionHeading :eyebrow="wrongItemId !== undefined ? '错题定向复习' : '课程练习'" :title="wrongItemId !== undefined ? '重新练习这道错题' : '课程练习'" description="提交后由服务端判分，答题记录和错题状态以保存结果为准。" />
    <div v-if="serviceError" class="inline-notice" role="alert"><CircleHelp :size="16" />{{ serviceError }}<button class="text-button" @click="loadQuestions">重试</button><button v-if="targeted" class="text-button" @click="router.push('/wrong-book')">返回错题列表</button></div>
    <div v-if="loadingQuestions" class="loading-row"><span class="spinner"></span>正在加载练习题目</div>
    <section v-else-if="question" class="quiz-layout">
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
        @toggle="toggleOption"
        @submit="submit"
        @retry-submit="submit"
        @retry-question="retryQuestion"
        @next="nextQuestion"
      />
       <aside class="quiz-side"><div class="quiz-side-mark"><Lightbulb :size="19" /></div><p class="eyebrow">学习提示</p><h3>{{ question.knowledgePoint || '先理解，再作答' }}</h3><p>先独立作答，再查看服务端返回的正确答案与解析。需要补充概念时，可以打开关联知识图谱。</p><button class="text-button" @click="openKnowledge">查看相关知识 <ArrowRight :size="14" /></button><LearningContextActions compact :context="{ knowledgePointId: Number(route.query.knowledgePointId) || undefined, entity: question.knowledgePoint }" /><div class="side-separator"></div><div class="side-stat"><span>本次正确率</span><strong>{{ sessionAccuracy }}</strong></div><div class="side-stat"><span>本次已保存</span><strong>{{ submissions.length }} 次</strong></div><div class="side-stat"><span>待复习错题</span><strong>{{ overview?.wrongCount ?? '—' }}</strong></div></aside>
    </section>
    <div v-else-if="!serviceError" class="empty-state"><CircleHelp :size="24" /><strong>当前范围暂无可用题目</strong><span>可返回课程列表选择其他知识点。</span><button class="text-button" @click="router.push('/courses')">查看课程 <ArrowRight :size="14" /></button></div>
  </div>
</template>
