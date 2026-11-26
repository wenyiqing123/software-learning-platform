<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChevronDown,
  CircleAlert,
  CircleCheck,
  Database,
  GraduationCap,
  Network,
  RotateCcw,
  SendHorizontal,
  Sparkles,
  Trash2,
} from '@lucide/vue'
import { apiError, learningApi, type FeynmanEvaluateResponse, type KnowledgePoint } from '../api/client'
import MarkdownContent from './MarkdownContent.vue'

const props = withDefaults(defineProps<{
  initialKnowledgePoint?: string
  courseId?: number
}>(), {
  initialKnowledgePoint: '',
  courseId: undefined,
})

const emit = defineEmits<{
  'open-graph': [entity?: string]
}>()

const router = useRouter()

const FEYNMAN_DRAFT_KEY = 'slp:feynman-draft'
interface FeynmanDraftCache {
  topic: string
  explanation: string
}
interface FeynmanRoundSnapshot {
  round: number
  topic: string
  coveredCount: number
  missingCount: number
  misconceptionCount: number
  time: string
}

function readDraftCache(): FeynmanDraftCache | null {
  try {
    const raw = window.sessionStorage?.getItem(FEYNMAN_DRAFT_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<FeynmanDraftCache>
    if (typeof parsed.topic !== 'string' || typeof parsed.explanation !== 'string') return null
    return { topic: parsed.topic, explanation: parsed.explanation }
  } catch {
    return null
  }
}

const savedDraft = readDraftCache()
const initialTopicTrimmed = props.initialKnowledgePoint.trim()
const knowledgePoint = ref(initialTopicTrimmed || savedDraft?.topic || '')
const studentExplanation = ref(
  savedDraft && (!initialTopicTrimmed || savedDraft.topic.trim() === initialTopicTrimmed)
    ? savedDraft.explanation
    : '',
)
const isComposing = ref(false)
const points = ref<KnowledgePoint[]>([])
const pointsLoading = ref(false)

const loading = ref(false)
const error = ref('')
const result = ref<FeynmanEvaluateResponse | null>(null)
const rounds = ref<FeynmanRoundSnapshot[]>([])
const explanationRef = ref<HTMLTextAreaElement | null>(null)

watch([knowledgePoint, studentExplanation], ([nextTopic, nextExplanation]) => {
  try {
    if (!nextTopic.trim() && !nextExplanation.trim()) {
      window.sessionStorage?.removeItem(FEYNMAN_DRAFT_KEY)
      return
    }
    window.sessionStorage?.setItem(
      FEYNMAN_DRAFT_KEY,
      JSON.stringify({ topic: nextTopic, explanation: nextExplanation }),
    )
  } catch {
    // 忽略隐私模式或配额导致的存储异常
  }
})

watch(
  () => props.initialKnowledgePoint,
  (next) => {
    const trimmed = next.trim()
    if (trimmed && !knowledgePoint.value.trim()) {
      knowledgePoint.value = trimmed
    }
  },
)

const trimmedTopic = computed(() => knowledgePoint.value.trim())
const trimmedExplanation = computed(() => studentExplanation.value.trim())
const trimmedLength = computed(() => trimmedExplanation.value.length)
const canSubmit = computed(
  () =>
    !loading.value
    && trimmedTopic.value.length > 0
    && trimmedTopic.value.length <= 100
    && trimmedLength.value >= 10
    && studentExplanation.value.length <= 2000,
)

const topicOptions = computed(() => points.value.slice(0, 8))

async function loadCoursePoints() {
  pointsLoading.value = true
  try {
    const list = await learningApi.knowledgePoints(props.courseId)
    points.value = Array.isArray(list) ? list : []
  } catch {
    points.value = []
  } finally {
    pointsLoading.value = false
  }
}

onMounted(() => {
  void loadCoursePoints()
})

watch(() => props.courseId, () => {
  void loadCoursePoints()
})

function selectQuickTopic(point: KnowledgePoint) {
  knowledgePoint.value = (point.graphEntity || point.name || '').trim()
}

function clearForm() {
  studentExplanation.value = ''
  error.value = ''
  result.value = null
  try {
    window.sessionStorage?.removeItem(FEYNMAN_DRAFT_KEY)
  } catch {
    // 忽略存储清理异常
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey) && !isComposing.value) {
    event.preventDefault()
    void submit()
  }
}

const currentTopicRounds = computed(() => {
  const key = result.value?.knowledgePoint || trimmedTopic.value
  return rounds.value.filter(item => item.topic === key)
})

const roundDelta = computed(() => {
  const list = currentTopicRounds.value
  if (list.length < 2) return null
  const prev = list[list.length - 2]
  const latest = list[list.length - 1]
  return {
    prevRound: prev.round,
    latestRound: latest.round,
    coveredDiff: latest.coveredCount - prev.coveredCount,
    missingDiff: latest.missingCount - prev.missingCount,
  }
})

async function submit() {
  if (!canSubmit.value) return
  loading.value = true
  error.value = ''
  try {
    const response = await learningApi.evaluateFeynmanExplanation({
      knowledgePoint: trimmedTopic.value,
      studentExplanation: trimmedExplanation.value,
      ...(props.courseId ? { courseId: props.courseId } : {}),
    })
    result.value = response
    const topicKey = response.knowledgePoint || trimmedTopic.value
    const sameTopicCount = rounds.value.filter(item => item.topic === topicKey).length
    rounds.value.push({
      round: sameTopicCount + 1,
      topic: topicKey,
      coveredCount: response.coveredPoints.length,
      missingCount: response.missingTriples.length,
      misconceptionCount: response.misconceptions.length,
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    })
  } catch (cause) {
    error.value = apiError(cause, '费曼图谱自检服务或知识检索暂不可用，已保留你的讲解草稿，请稍后重试。')
  } finally {
    loading.value = false
  }
}

async function applyFollowUpRevision() {
  if (!result.value?.followUpQuestion) return
  const cue = `补充回应追问（${result.value.followUpQuestion}）：`
  if (!studentExplanation.value.includes(cue)) {
    studentExplanation.value = studentExplanation.value.trimEnd()
      ? `${studentExplanation.value.trimEnd()}\n\n${cue}`
      : cue
  }
  await nextTick()
  explanationRef.value?.focus()
}

function jumpToGraph(entity: string) {
  emit('open-graph', entity)
  if (!entity) return
  void router.push({
    path: '/knowledge',
    query: {
      entity,
      ...(props.courseId ? { courseId: String(props.courseId) } : {}),
    },
  })
}
</script>

<template>
  <section class="feynman-scroll" aria-label="费曼以教促学自检区" data-testid="feynman-panel">
    <div class="feynman-shell">
      <header class="feynman-hero">
        <div class="feynman-hero-badge">
          <GraduationCap :size="15" aria-hidden="true" />
          <span>费曼学习法 · 图谱对照自检</span>
        </div>
        <h1 class="feynman-title">费曼“以教促学”自检</h1>
        <p class="section-description">
          请尝试用自己的话向初学者解释该概念的核心定义、作用与关联关系；系统将对照课程知识图谱三元组反馈已覆盖要点与遗漏关联。
        </p>
      </header>

      <form class="feynman-form-surface" @submit.prevent="submit">
        <div class="feynman-field-row">
          <label class="feynman-field-label" for="feynman-topic-input">目标知识点</label>
          <input
            id="feynman-topic-input"
            v-model="knowledgePoint"
            type="text"
            class="feynman-topic-input"
            maxlength="100"
            list="feynman-topic-suggestions"
            placeholder="输入或点击选择知识点，如：需求分析、数据流图DFD"
            data-testid="feynman-topic-input"
          />
          <datalist id="feynman-topic-suggestions">
            <option v-for="point in points" :key="point.id" :value="point.graphEntity || point.name">
              {{ point.name }}
            </option>
          </datalist>
          <div v-if="topicOptions.length" class="feynman-topic-chips" aria-label="课程知识点快选">
            <button
              v-for="point in topicOptions"
              :key="point.id"
              type="button"
              class="feynman-chip-btn"
              :class="{ active: trimmedTopic === (point.graphEntity || point.name) }"
              :disabled="loading"
              @click="selectQuickTopic(point)"
            >
              {{ point.name }}
            </button>
          </div>
          <span v-else-if="pointsLoading" class="feynman-subhint">正在加载课程知识点列表…</span>
        </div>

        <div class="feynman-field-row">
          <label class="feynman-field-label" for="feynman-explanation-input">
            用自己的话向初学者解释（Ctrl/Cmd + Enter 提交）
          </label>
          <textarea
            id="feynman-explanation-input"
            ref="explanationRef"
            v-model="studentExplanation"
            class="feynman-textarea"
            rows="5"
            maxlength="2000"
            placeholder="例如：需求分析的核心是弄清楚用户想让系统完成哪些业务目标，并区分系统边界与外部参与者，最终把约定好的功能与约束写进软件需求规格说明书..."
            data-testid="feynman-explanation-input"
            @compositionstart="isComposing = true"
            @compositionend="isComposing = false"
            @keydown="onKeydown"
          ></textarea>
        </div>

        <div class="feynman-form-footer">
          <span class="feynman-char-count" :class="{ 'is-ready': trimmedLength >= 10 }">
            {{ trimmedLength }} / 2000 字（{{ trimmedLength >= 10 ? '已达自检字数要求' : '至少需输入 10 个有效字符' }}）
          </span>
          <div class="feynman-form-actions">
            <button
              type="button"
              class="button button-outline button-small"
              data-testid="feynman-clear-btn"
              :disabled="loading || (!studentExplanation && !result)"
              @click="clearForm"
            >
              <Trash2 :size="14" aria-hidden="true" />
              <span>清空</span>
            </button>
            <button
              type="submit"
              class="button button-dark button-small"
              data-testid="feynman-submit-btn"
              :disabled="!canSubmit"
            >
              <SendHorizontal :size="14" aria-hidden="true" />
              <span>{{ loading ? '正在对照图谱评估…' : '开始图谱自检' }}</span>
            </button>
          </div>
        </div>
      </form>

      <div v-if="error" class="inline-notice" role="alert" data-testid="feynman-error">
        <CircleAlert :size="16" aria-hidden="true" />
        <span>{{ error }}</span>
        <button type="button" class="text-button" :disabled="!canSubmit" @click="submit">重试</button>
      </div>

      <div v-if="loading" class="feynman-loading-bar" role="status" data-testid="feynman-loading">
        <span class="spinner"></span>
        <span>正在只读检索「{{ trimmedTopic }}」课程知识图谱并生成诊断…</span>
      </div>

      <section v-if="result" class="feynman-report" aria-label="费曼自检诊断结果" data-testid="feynman-result">
        <div class="feynman-report-head">
          <h2>图谱对照自检反馈 · {{ result.knowledgePoint }}</h2>
          <button
            type="button"
            class="text-button"
            @click="jumpToGraph(result.knowledgePoint)"
          >
            <Network :size="14" aria-hidden="true" />
            <span>在图谱中查看</span>
          </button>
        </div>

        <div
          v-if="currentTopicRounds.length"
          class="feynman-rounds-bar"
          data-testid="feynman-rounds-history"
        >
          <span class="feynman-rounds-label">自检迭代记录（{{ currentTopicRounds.length }} 轮）</span>
          <div class="feynman-rounds-chips">
            <span
              v-for="item in currentTopicRounds"
              :key="item.round"
              class="feynman-round-chip"
            >
              第 {{ item.round }} 轮（{{ item.time }}）：覆盖 {{ item.coveredCount }} · 遗漏 {{ item.missingCount }}
            </span>
          </div>
          <span v-if="roundDelta" class="feynman-round-delta">
            较第 {{ roundDelta.prevRound }} 轮：已覆盖 {{ roundDelta.coveredDiff >= 0 ? `+${roundDelta.coveredDiff}` : roundDelta.coveredDiff }} · 遗漏 {{ roundDelta.missingDiff >= 0 ? `+${roundDelta.missingDiff}` : roundDelta.missingDiff }}
          </span>
        </div>

        <div class="feynman-grid">
          <article class="feynman-section-card" data-testid="feynman-covered">
            <h3>
              <CircleCheck :size="15" aria-hidden="true" />
              <span>已覆盖要点</span>
            </h3>
            <ul v-if="result.coveredPoints.length" class="feynman-bullet-list">
              <li v-for="(point, idx) in result.coveredPoints" :key="idx">{{ point }}</li>
            </ul>
            <p v-else class="feynman-empty-note">暂无明确匹配到图谱三元组的要点，建议结合核心定义再梳理一遍。</p>
          </article>

          <article class="feynman-section-card" data-testid="feynman-missing">
            <h3>
              <CircleAlert :size="15" aria-hidden="true" />
              <span>遗漏的图谱关联</span>
            </h3>
            <div v-if="result.missingTriples.length" class="feynman-missing-list">
              <button
                v-for="(triple, idx) in result.missingTriples"
                :key="`${idx}-${triple.subject}-${triple.predicate}-${triple.object}`"
                type="button"
                class="feynman-triple-jump"
                @click="jumpToGraph(triple.subject)"
              >
                <span class="feynman-triple-text">
                  <strong>{{ triple.subject }}</strong> — {{ triple.predicate }} — {{ triple.object }}
                </span>
                <ArrowRight :size="14" aria-hidden="true" />
              </button>
            </div>
            <p v-else class="feynman-empty-note">本次未发现明显遗漏的检索图谱关联。</p>
          </article>
        </div>

        <article class="feynman-section-card" data-testid="feynman-misconceptions">
          <h3>
            <Sparkles :size="15" aria-hidden="true" />
            <span>易混纠偏与补充提示</span>
          </h3>
          <div v-if="result.misconceptions.length" class="feynman-markdown-list">
            <MarkdownContent
              v-for="(item, idx) in result.misconceptions"
              :key="idx"
              :content="item"
            />
          </div>
          <p v-else class="feynman-empty-note">暂未发现明显的术语混淆或概念偏差。</p>
        </article>

        <article class="feynman-followup-card" data-testid="feynman-followup">
          <div class="feynman-followup-copy">
            <span class="eyebrow">启发式追问</span>
            <p>{{ result.followUpQuestion }}</p>
          </div>
          <button
            type="button"
            class="button button-outline button-small"
            data-testid="feynman-refine-btn"
            @click="applyFollowUpRevision"
          >
            <RotateCcw :size="14" aria-hidden="true" />
            <span>基于追问继续完善讲解</span>
          </button>
        </article>

        <details
          v-if="result.evidenceTriples.length"
          class="evidence-block"
          data-testid="feynman-evidence-details"
        >
          <summary class="evidence-capsule-summary">
            <Database :size="14" class="evidence-capsule-icon" />
            <span class="evidence-capsule-label">本次对照的 {{ result.evidenceTriples.length }} 条真实图谱证据</span>
            <ChevronDown :size="14" class="evidence-chevron" />
          </summary>
          <div class="evidence-panel">
            <div class="evidence-triple-list">
              <div
                v-for="(triple, idx) in result.evidenceTriples"
                :key="`${idx}-${triple.subject}-${triple.object}`"
                class="evidence-triple"
              >
                <span class="evidence-index-badge">[{{ idx + 1 }}]</span>
                <button
                  type="button"
                  class="triple-subject-link"
                  @click="jumpToGraph(triple.subject)"
                >
                  {{ triple.subject }}
                </button>
                <i class="triple-predicate">— {{ triple.predicate }} —</i>
                <span class="triple-object">{{ triple.object }}</span>
              </div>
            </div>
          </div>
        </details>

        <p class="feynman-disclaimer" data-testid="feynman-disclaimer">
          基于当前课程知识图谱三元组对照生成的辅助自检建议，仅供学习参考，不作为正式成绩评定。
        </p>
      </section>
    </div>
  </section>
</template>

<style scoped>
.feynman-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 24px 36px;
}

.feynman-shell {
  width: 100%;
  max-width: 780px;
  margin: 0 auto;
  display: grid;
  gap: 16px;
}

.feynman-hero {
  display: grid;
  gap: 6px;
}

.feynman-hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: max-content;
  padding: 3px 10px;
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
}

.feynman-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.3;
  color: var(--text);
}

.feynman-form-surface {
  display: grid;
  gap: 14px;
  padding: 18px 20px;
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.feynman-field-row {
  display: grid;
  gap: 8px;
}

.feynman-field-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.feynman-topic-input,
.feynman-textarea {
  width: 100%;
  padding: 10px 12px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 14px;
  line-height: 1.6;
}

.feynman-topic-input {
  min-height: 40px;
}

.feynman-textarea {
  resize: vertical;
  min-height: 120px;
}

.feynman-topic-input:focus,
.feynman-textarea:focus {
  outline: none;
  border-color: var(--border-focus);
  box-shadow: var(--focus-ring);
}

.feynman-topic-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.feynman-chip-btn {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 2px 10px;
  color: var(--text-muted);
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-pill);
  font-size: 12px;
  cursor: pointer;
}

.feynman-chip-btn:hover:not(:disabled) {
  color: var(--accent-strong);
  border-color: var(--border-focus);
}

.feynman-chip-btn.active {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--border-focus);
  font-weight: 600;
}

.feynman-subhint {
  color: var(--text-muted);
  font-size: 12px;
}

.feynman-form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 16px;
}

.feynman-char-count {
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.feynman-char-count.is-ready {
  color: var(--accent-strong);
}

.feynman-form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.feynman-loading-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  color: var(--text-muted);
  background: var(--surface-tint);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.feynman-report {
  display: grid;
  gap: 12px;
}

.feynman-report-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.feynman-report-head h2 {
  margin: 0;
  font-size: 16px;
  color: var(--text);
}

.feynman-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.feynman-section-card {
  display: grid;
  gap: 8px;
  align-content: start;
  padding: 14px 16px;
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
}

.feynman-section-card h3 {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--text);
}

.feynman-section-card h3 svg {
  color: var(--accent-strong);
}

.feynman-bullet-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
  font-size: 13.5px;
  color: var(--text);
  line-height: 1.55;
}

.feynman-missing-list {
  display: grid;
  gap: 8px;
}

.feynman-triple-jump {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  color: var(--text);
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-xs);
  text-align: left;
  font-size: 13px;
  cursor: pointer;
}

.feynman-triple-jump:hover {
  border-color: var(--border-focus);
  background: var(--accent-soft);
}

.feynman-triple-text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.feynman-empty-note {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.feynman-markdown-list {
  display: grid;
  gap: 8px;
}

.feynman-followup-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 16px;
  padding: 14px 16px;
  background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 22%, transparent);
  border-radius: var(--radius-sm);
}

.feynman-followup-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
  flex: 1 1 280px;
}

.feynman-followup-copy .eyebrow {
  margin: 0;
  color: var(--accent-strong);
}

.feynman-followup-copy p {
  margin: 0;
  color: var(--text);
  font-size: 14px;
  font-weight: 550;
  line-height: 1.55;
}

.feynman-disclaimer {
  margin: 2px 0 0;
  color: var(--text-subtle);
  font-size: 12px;
  line-height: 1.5;
}

.evidence-block {
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
}

.evidence-capsule-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  cursor: pointer;
  list-style: none;
  color: var(--text-muted);
  font-size: 12.5px;
}

.evidence-capsule-summary::-webkit-details-marker {
  display: none;
}

.evidence-capsule-icon {
  color: var(--accent-strong);
  flex-shrink: 0;
}

.evidence-capsule-label {
  font-weight: 600;
  color: var(--text);
}

.evidence-chevron {
  margin-left: auto;
  transition: transform var(--motion);
}

.evidence-block[open] .evidence-chevron {
  transform: rotate(180deg);
}

.evidence-panel {
  padding: 0 12px 12px;
}

.evidence-triple-list {
  display: grid;
  gap: 6px;
}

.evidence-triple {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  padding: 6px 8px;
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-xs);
  font-size: 12.5px;
}

.evidence-index-badge {
  color: var(--accent-strong);
  font-weight: 600;
  font-size: 11.5px;
}

.triple-subject-link {
  padding: 0;
  color: var(--accent-strong);
  background: transparent;
  border: 0;
  font-weight: 600;
  cursor: pointer;
}

.triple-subject-link:hover {
  text-decoration: underline;
}

.triple-predicate {
  color: var(--text-muted);
  font-style: normal;
}

.triple-object {
  color: var(--text);
}

.feynman-rounds-bar {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
}

.feynman-rounds-label {
  color: var(--text);
  font-size: 12px;
  font-weight: 600;
}

.feynman-rounds-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.feynman-round-chip {
  padding: 2px 8px;
  color: var(--text-muted);
  background: var(--surface);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.feynman-round-delta {
  color: var(--accent-strong);
  font-size: 12px;
  font-weight: 600;
}

@media (max-width: 780px) {
  .feynman-scroll {
    padding: 12px 14px 28px;
  }

  .feynman-grid {
    grid-template-columns: 1fr;
  }
}
</style>
