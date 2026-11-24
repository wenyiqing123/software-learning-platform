<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDownToLine, CircleHelp, FlaskConical, Play, Plus, Timer, X } from '@lucide/vue'
import { apiError, learningApi, type ExperimentAssignment, type FixedExperimentCase, type FixedExperimentSuite } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import { experimentCsv, experimentJson, type ExperimentExportRun } from '../domain/experimentExport'
import ExperimentResultGrid from '../components/ExperimentResultGrid.vue'

const props = withDefaults(defineProps<{ embedded?: boolean; studentTaskMode?: boolean }>(), {
  embedded: false,
  studentTaskMode: false
})

const route = useRoute()
const router = useRouter()

const isStudentMode = computed(() => Boolean(props.studentTaskMode || route.path === '/experiment-run'))
const assignedTask = ref<ExperimentAssignment | null>(null)

interface Answer {
  mode: string
  answer: string
  latencyMs: number
  error?: string
  prompt?: string
  triples?: Array<{ subject: string; predicate: string; object: string; score?: number }>
  provenance?: Record<string, unknown>
  grounding?: { strategyVersion: string; status: string; candidates: unknown[]; selectedIds: string[]; selectedEvidence: unknown[]; selectionMs: number; answerMs: number; modelCallCount: number }
  experimentType?: string
  modelVariant?: string
  resultKey?: string
}

interface Scores {
  rougeL?: number
  bleu4?: number
  bleuOrder?: number
  entityRecall?: number
}

interface Run {
  id: string
  question: string
  createdAt: string
  results: Answer[]
  ratings: Record<string, number>
  evaluation?: { source?: string; experimentType?: string; metricsByMode?: Record<string, Scores>; note?: string }
}

const question = ref('数据流图中，加工的命名应采用什么形式？')
const referenceAnswer = ref('加工命名的语法结构必须采用动词+宾语的动宾短语形式，如校验权限、生成报表')
const expectedEntityInputs = ref(['动宾短语', '', ''])
const experimentType = ref<'METHODS' | 'QWEN_MATRIX'>('METHODS')
const rankedPromptVersion = 'ranked-grounded-v3'
const includeAblations = ref(false)
const taskId = computed(() => {
  const q = route.query.taskId
  if (typeof q === 'string' && q.trim()) return Number(q) || undefined
  if (typeof q === 'number') return q
  return Number(new URLSearchParams(window.location.search).get('taskId')) || undefined
})
const assignmentId = computed(() => {
  const q = route.query.assignmentId
  if (typeof q === 'string' && q.trim()) return Number(q) || undefined
  if (typeof q === 'number') return q
  return Number(new URLSearchParams(window.location.search).get('assignmentId')) || undefined
})
const running = ref(false), error = ref(''), latest = ref<Run | null>(null), history = ref<Run[]>([])
const executionPhase = ref<'idle' | 'running' | 'complete'>('idle')
const executionStartedAt = ref<number | null>(null)
const executionElapsed = ref(0)
let executionTimer: ReturnType<typeof setInterval> | null = null

interface BatchRunItem {
  caseId: string
  question: string
  sourceSection: string
  knowledgePoint: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  error?: string
  runId?: string
}

const fixedSuite = ref<FixedExperimentSuite | null>(null)
const fixedSuiteLoading = ref(false)
const fixedSuiteError = ref('')
const selectedCaseIds = ref<string[]>([])

const batchRunning = ref(false)
const batchCanceled = ref(false)
const batchItems = ref<BatchRunItem[]>([])
const batchStartTime = ref<number | null>(null)
const batchElapsedSeconds = ref(0)
let batchTimer: ReturnType<typeof setInterval> | null = null
const currentBatchIndex = ref<number>(-1)

const batchSuccessCount = computed(() => batchItems.value.filter(i => i.status === 'SUCCESS').length)
const batchFailedCount = computed(() => batchItems.value.filter(i => i.status === 'FAILED').length)

// Tab 分段支持：run（实验与结果）、summary（个人统计）、history（实验历史）
type ExperimentTab = 'run' | 'summary' | 'history'
const activeTab = ref<ExperimentTab>(
  typeof route?.query?.tab === 'string' && ['run', 'summary', 'history'].includes(route.query.tab)
    ? (route.query.tab as ExperimentTab)
    : 'run'
)

watch(() => route?.query?.tab, (newTab) => {
  if (typeof newTab === 'string' && ['run', 'summary', 'history'].includes(newTab)) {
    activeTab.value = newTab as ExperimentTab
  }
})

function setTab(tab: ExperimentTab) {
  activeTab.value = tab
  if (router && route) {
    void router.replace({ query: { ...route.query, tab } })
  }
}

function selectRun(run: Run) {
  latest.value = run
  setTab('run')
}

const expectedConditions = computed(() => experimentType.value === 'QWEN_MATRIX' ? 6 : 3 + (includeAblations.value ? 4 : 0))
const executionStatusText = computed(() => executionPhase.value === 'running' ? '服务端正在并行执行并统一校验各条件' : executionPhase.value === 'complete' ? `已返回 ${latest.value?.results.length || 0} 个实际条件` : '等待运行')
const modeNames: Record<string, string> = {
  ABLATION_SORTED_SCORED: '消融基线：排序且评分',
  ABLATION_WITHOUT_SCORE: '消融：不评分',
  ABLATION_WITHOUT_SORT: '消融：不排序',
  ABLATION_WITHOUT_SORT_AND_SCORE: '消融：不排序且不评分',
  DIRECT: '大模型直答',
  TRIPLES: '三元组增强',
  RANKED: '排序评分 + 原始提示词',
  QWEN_BASE_DIRECT: 'Qwen 基座 · DIRECT',
  QWEN_BASE_TRIPLES: 'Qwen 基座 · TRIPLES',
  QWEN_BASE_RANKED: 'Qwen 基座 · RANKED',
  QWEN_LORA_DIRECT: 'Qwen LoRA · DIRECT',
  QWEN_LORA_TRIPLES: 'Qwen LoRA · TRIPLES',
  QWEN_LORA_RANKED: 'Qwen LoRA · RANKED',
  NO_SORT: '消融：不排序',
  NO_SCORE: '消融：不评分',
  NO_SORT_NO_SCORE: '消融：不排序且不评分',
  WITHOUT_SORT: '消融：不排序',
  WITHOUT_SCORE: '消融：不评分',
  WITHOUT_SORT_AND_SCORE: '消融：不排序且不评分'
}

const summary = computed(() => {
  const groups = new Map<string, { key: string; label: string; count: number; failed: number; times: number[]; ratings: number[] }>()
  for (const run of history.value.filter(r => r.evaluation?.source === 'SERVER_AI_SERVICE')) {
    for (const result of run.results) {
      if (result.mode === 'RANKED' && result.provenance?.systemPromptVersion !== rankedPromptVersion) continue
      const key = `${run.evaluation?.experimentType || 'METHODS'}:${result.resultKey || result.mode}`
      const entry = groups.get(key) || { key, label: modeNames[result.resultKey || result.mode] || result.resultKey || result.mode, count: 0, failed: 0, times: [], ratings: [] }
      entry.count++
      if (result.error) entry.failed++
      else entry.times.push(result.latencyMs)
      if (run.ratings[result.resultKey || result.mode]) entry.ratings.push(run.ratings[result.resultKey || result.mode]!)
      groups.set(key, entry)
    }
  }
  return [...groups.values()].map(e => {
    const times = e.times.sort((a, b) => a - b)
    return {
      ...e,
      mean: times.length ? Math.round(times.reduce((a, b) => a + b, 0) / times.length) : null,
      p95: times.length ? times[Math.ceil(times.length * 0.95) - 1] : null,
      csat: e.ratings.length ? (e.ratings.reduce((a, b) => a + b, 0) / e.ratings.length).toFixed(2) : '未评分'
    }
  })
})

onMounted(async () => {
  if (isStudentMode.value) {
    try {
      const assignments = await learningApi.experimentAssignments()
      const matched = assignments.find(a =>
        (taskId.value != null && a.taskId === taskId.value) ||
        (assignmentId.value != null && a.id === assignmentId.value)
      )
      if (!matched) {
        error.value = '未找到指派给您的有效实验任务，请确认任务是否开放或联系教师'
      } else {
        assignedTask.value = matched
        question.value = matched.question || matched.title || ''
      }
    } catch (e) {
      error.value = apiError(e, '获取实验任务指派失败')
    }
  } else {
    try {
      history.value = await learningApi.listExperiments()
    } catch (e) {
      error.value = apiError(e)
    }
    void loadFixedSuite()
  }
})

onBeforeUnmount(() => {
  if (executionTimer) clearInterval(executionTimer)
  if (batchTimer) clearInterval(batchTimer)
})

async function loadFixedSuite() {
  if (isStudentMode.value) return
  fixedSuiteLoading.value = true
  fixedSuiteError.value = ''
  try {
    const suite = await learningApi.getFixedExperimentSuite()
    fixedSuite.value = suite
    selectedCaseIds.value = suite.cases.map(c => c.caseId)
  } catch (e) {
    fixedSuiteError.value = apiError(e, '加载固定实验案例目录失败')
  } finally {
    fixedSuiteLoading.value = false
  }
}

function isCaseSelected(caseId: string) {
  return selectedCaseIds.value.includes(caseId)
}

function toggleCaseSelection(caseId: string) {
  if (batchRunning.value) return
  const idx = selectedCaseIds.value.indexOf(caseId)
  if (idx >= 0) {
    selectedCaseIds.value.splice(idx, 1)
  } else {
    selectedCaseIds.value.push(caseId)
  }
}

function selectAllCases() {
  if (batchRunning.value || !fixedSuite.value) return
  selectedCaseIds.value = fixedSuite.value.cases.map(c => c.caseId)
}

function deselectAllCases() {
  if (batchRunning.value) return
  selectedCaseIds.value = []
}

function fillSingleRun(item: FixedExperimentCase) {
  question.value = item.question
  referenceAnswer.value = ''
  expectedEntityInputs.value = ['', '', '']
  setTab('run')
}

async function runBatchItems(itemsToRun: BatchRunItem[]) {
  batchRunning.value = true
  batchCanceled.value = false
  batchStartTime.value = Date.now()
  batchElapsedSeconds.value = 0
  if (batchTimer) clearInterval(batchTimer)
  batchTimer = setInterval(() => {
    if (batchStartTime.value) {
      batchElapsedSeconds.value = Math.floor((Date.now() - batchStartTime.value) / 1000)
    }
  }, 1000)

  try {
    for (let i = 0; i < itemsToRun.length; i++) {
      if (batchCanceled.value) break
      const item = itemsToRun[i]
      if (item.status === 'SUCCESS') continue

      currentBatchIndex.value = batchItems.value.findIndex(b => b.caseId === item.caseId)
      item.status = 'RUNNING'
      item.error = undefined

      try {
        const saved = await learningApi.runExperiment({
          question: item.question,
          experimentType: experimentType.value,
          includeAblations: experimentType.value === 'METHODS' && includeAblations.value,
          referenceAnswer: '',
          expectedEntities: []
        }) as Run
        item.status = 'SUCCESS'
        item.runId = saved.id
        latest.value = saved
        history.value = [saved, ...history.value.filter(h => h.id !== saved.id)].slice(0, 100)
      } catch (err) {
        item.status = 'FAILED'
        item.error = apiError(err, '运行异常')
      }
    }
  } finally {
    batchRunning.value = false
    currentBatchIndex.value = -1
    if (batchTimer) {
      clearInterval(batchTimer)
      batchTimer = null
    }
  }
}

function startBatchRun() {
  if (batchRunning.value || !fixedSuite.value) return
  const selectedCases = fixedSuite.value.cases.filter(c => selectedCaseIds.value.includes(c.caseId))
  if (!selectedCases.length) return

  batchItems.value = selectedCases.map(c => ({
    caseId: c.caseId,
    question: c.question,
    sourceSection: c.sourceSection,
    knowledgePoint: c.knowledgePoint,
    status: 'PENDING'
  }))
  void runBatchItems(batchItems.value)
}

function cancelBatchRun() {
  batchCanceled.value = true
}

function retryFailedBatch() {
  if (batchRunning.value) return
  const failed = batchItems.value.filter(b => b.status === 'FAILED')
  for (const item of failed) {
    item.status = 'PENDING'
    item.error = undefined
  }
  void runBatchItems(failed)
}

function retrySingleBatchItem(item: BatchRunItem) {
  if (batchRunning.value) return
  item.status = 'PENDING'
  item.error = undefined
  void runBatchItems([item])
}

function addExpectedEntity() {
  if (expectedEntityInputs.value.length < 40) expectedEntityInputs.value.push('')
}

function removeExpectedEntity(index: number) {
  if (expectedEntityInputs.value.length > 3) expectedEntityInputs.value.splice(index, 1)
}

async function runComparison() {
  if (running.value) return
  if (!isStudentMode.value && !question.value.trim()) return
  const currentTaskId = taskId.value || assignedTask.value?.taskId
  if (isStudentMode.value && !currentTaskId) {
    error.value = '缺少实验任务参数'
    return
  }

  running.value = true
  error.value = ''
  executionPhase.value = 'running'
  executionStartedAt.value = Date.now()
  executionElapsed.value = 0
  if (executionTimer) clearInterval(executionTimer)
  executionTimer = setInterval(() => {
    if (executionStartedAt.value) executionElapsed.value = Math.floor((Date.now() - executionStartedAt.value) / 1000)
  }, 1000)
  try {
    let saved: Run
    if (isStudentMode.value) {
      saved = await learningApi.runExperiment({
        taskId: currentTaskId
      }) as Run
    } else {
      const expectedEntities = expectedEntityInputs.value.map(value => value.trim()).filter(Boolean)
      saved = await learningApi.runExperiment({
        question: question.value.trim(),
        referenceAnswer: referenceAnswer.value.trim(),
        expectedEntities,
        experimentType: experimentType.value,
        includeAblations: experimentType.value === 'METHODS' && includeAblations.value,
        taskId: taskId.value
      }) as Run
      history.value = [saved, ...history.value].slice(0, 100)
    }
    latest.value = saved
    setTab('run')
  } catch (e) {
    error.value = apiError(e, '实验未完成，请检查 AI 服务和模型配置')
    executionPhase.value = 'idle'
  } finally {
    running.value = false
    if (executionTimer) {
      clearInterval(executionTimer)
      executionTimer = null
    }
    if (latest.value) executionPhase.value = 'complete'
  }
}

async function rate(mode: string, score: number) {
  if (isStudentMode.value || !latest.value || !score) return
  const key = latest.value.results.find(result => result.resultKey === mode || result.mode === mode)?.resultKey || mode
  try {
    latest.value = await learningApi.rateExperiment(latest.value.id, { [key]: score })
    history.value = history.value.map(r => r.id === latest.value?.id ? latest.value! : r)
  } catch (e) {
    error.value = apiError(e)
  }
}

function download(content: string, filename: string, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

async function exportResults() {
  try {
    download(JSON.stringify(await learningApi.exportExperimentsJson(), null, 2), 'software-engineering-experiments.json', 'application/json')
  } catch {
    const batchMeta = batchItems.value.length ? {
      suiteVersion: fixedSuite.value?.suiteVersion || '2026.1',
      total: batchItems.value.length,
      successCount: batchSuccessCount.value,
      failedCount: batchFailedCount.value
    } : undefined
    download(JSON.stringify(experimentJson(history.value as unknown as ExperimentExportRun[], batchMeta), null, 2), 'software-engineering-experiments.json', 'application/json')
  }
}

async function exportCsv() {
  try {
    const blob = await learningApi.exportExperimentsCsv()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'software-engineering-experiments.csv'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch {
    download(experimentCsv(history.value as unknown as ExperimentExportRun[]), 'software-engineering-experiments.csv', 'text/csv;charset=utf-8')
  }
}

function score(value?: number) {
  return value == null ? '未测评' : (value * 100).toFixed(1) + '%'
}

defineExpose({
  cancelBatchRun
})
</script>

<template>
  <div class="page-stack experiment-page">
    <SectionHeading
      :embedded="embedded"
      :title="isStudentMode ? '任务实验运行' : '问答实验'"
      :description="isStudentMode ? '当前为受限实验任务运行环境，服务端将自动加载指派题目与评测环境。' : '固定问题与实验条件，对照问答策略或 Qwen 基座与 LoRA。'"
    >
      <div v-if="!isStudentMode" class="experiment-export-actions">
        <button class="button button-outline" :disabled="!history.length" @click="exportResults"><ArrowDownToLine :size="16" />导出 JSON</button>
        <button class="button button-outline" :disabled="!history.length" @click="exportCsv"><ArrowDownToLine :size="16" />导出 CSV</button>
      </div>
    </SectionHeading>

    <!-- Query/Tab 分段栏：仅在管理端完整模式下展示 -->
    <div v-if="!isStudentMode" role="tablist" class="experiment-tabs-nav" aria-label="实验功能分段">
      <button
        role="tab"
        id="tab-btn-run"
        :aria-selected="activeTab === 'run'"
        :class="['exp-tab-button', { active: activeTab === 'run' }]"
        @click="setTab('run')"
      >
        对比实验与本次结果
      </button>
      <button
        role="tab"
        id="tab-btn-summary"
        :aria-selected="activeTab === 'summary'"
        :class="['exp-tab-button', { active: activeTab === 'summary' }]"
        @click="setTab('summary')"
      >
        个人实验统计 ({{ summary.length }})
      </button>
      <button
        role="tab"
        id="tab-btn-history"
        :aria-selected="activeTab === 'history'"
        :class="['exp-tab-button', { active: activeTab === 'history' }]"
        @click="setTab('history')"
      >
        实验历史 ({{ history.length }})
      </button>
    </div>

    <!-- 选项卡 1：对比实验输入与本次结果 -->
    <div v-show="isStudentMode || activeTab === 'run'" role="tabpanel" :aria-labelledby="isStudentMode ? undefined : 'tab-btn-run'" class="tab-panel-container">
      <details v-if="!isStudentMode" class="experiment-methods">
        <summary>实验条件说明</summary>
        <dl>
          <div><dt>大模型直答</dt><dd>直接提问，不访问图谱。</dd></div>
          <div><dt>三元组 + 大模型</dt><dd>使用共享证据，不向提示词注入分数。</dd></div>
          <div><dt>排序评分 + 原始提示词</dt><dd>先从排序评分证据中选择依据，再生成回答。</dd></div>
        </dl>
      </details>
      <!-- 固定实验案例目录与串行评测面板（仅管理端全功能模式渲染，学生模式严格隐藏） -->
      <section v-if="!isStudentMode" class="content-section fixed-suite-section" aria-label="固定实验案例目录">
        <div class="section-title-row">
          <div>
            <p class="eyebrow">基准数据集 · 8 个案例</p>
            <h2>固定实验案例目录 <span v-if="fixedSuite" class="suite-version-tag">版本 {{ fixedSuite.suiteVersion }}</span></h2>
          </div>
          <div class="suite-header-actions">
            <button type="button" class="text-button" :disabled="batchRunning" @click="selectAllCases">全选</button>
            <button type="button" class="text-button" :disabled="batchRunning" @click="deselectAllCases">取消选择</button>
          </div>
        </div>

        <div v-if="fixedSuiteLoading" class="suite-loading">正在加载固定实验案例目录…</div>
        <div v-else-if="fixedSuiteError" class="inline-notice" role="alert">
          <CircleHelp :size="16" />
          <span>{{ fixedSuiteError }}</span>
          <button type="button" class="text-button retry-btn" @click="loadFixedSuite">重试加载</button>
        </div>
        <div v-else-if="fixedSuite && fixedSuite.cases.length" class="fixed-cases-grid">
          <div
            v-for="item in fixedSuite.cases"
            :key="item.caseId"
            class="fixed-case-card"
            :class="{ selected: isCaseSelected(item.caseId) }"
          >
            <div class="case-card-header">
              <label class="case-select-label">
                <input
                  type="checkbox"
                  v-model="selectedCaseIds"
                  :value="item.caseId"
                  :disabled="batchRunning"
                />
                <span class="case-id">{{ item.caseId }}</span>
              </label>
              <span class="case-meta-tag">{{ item.sourceSection }} · {{ item.knowledgePoint }}</span>
            </div>
            <p class="case-question">{{ item.question }}</p>
            <div class="case-card-actions">
              <button
                type="button"
                class="text-button fill-btn"
                title="将该题目填入单题实验输入框"
                @click="fillSingleRun(item)"
              >
                填入单题输入
              </button>
            </div>
          </div>
        </div>

        <!-- 批量运行控制条与结果进度 -->
        <div class="batch-runner-toolbar">
          <div class="batch-runner-info">
            <span>已选中 <strong>{{ selectedCaseIds.length }}</strong> / {{ fixedSuite?.cases.length || 0 }} 个基准案例</span>
            <span v-if="batchRunning">正在串行评测第 {{ currentBatchIndex + 1 }} 项，已运行 {{ batchElapsedSeconds }}s</span>
            <span v-else-if="batchItems.length">批次运行完成：成功 {{ batchSuccessCount }} / 失败 {{ batchFailedCount }}</span>
          </div>
          <div class="batch-runner-buttons">
            <button
              v-if="batchRunning"
              type="button"
              class="button button-outline"
              @click="cancelBatchRun"
            >
              停止批量运行
            </button>
            <button
              v-else
              type="button"
              class="button button-outline batch-run-btn"
              :disabled="!selectedCaseIds.length"
              @click="startBatchRun"
            >
              <Play :size="15" /> 串行批量运行 ({{ selectedCaseIds.length }})
            </button>
            <button
              v-if="!batchRunning && batchFailedCount > 0"
              type="button"
              class="button button-outline retry-failed-btn"
              @click="retryFailedBatch"
            >
              重试失败项 ({{ batchFailedCount }})
            </button>
          </div>
        </div>

        <!-- 批次运行列表状态（如有运行记录） -->
        <div v-if="batchItems.length" class="batch-items-list">
          <div
            v-for="b in batchItems"
            :key="b.caseId"
            class="batch-item-row"
            :class="b.status.toLowerCase()"
          >
            <span class="batch-status-badge">{{ b.status }}</span>
            <span class="batch-case-id">{{ b.caseId }}</span>
            <span class="batch-item-question">{{ b.question }}</span>
            <span v-if="b.error" class="batch-item-error">{{ b.error }}</span>
            <button
              v-if="!batchRunning && b.status === 'FAILED'"
              type="button"
              class="text-button"
              @click="retrySingleBatchItem(b)"
            >
              重试
            </button>
          </div>
        </div>
      </section>
      <section class="content-section experiment-run-form" aria-label="配置对照实验">
        <template v-if="!isStudentMode">
          <label for="experiment-type">实验方案</label>
          <select id="experiment-type" v-model="experimentType">
            <option value="METHODS">方法对照：固定默认模型</option>
            <option value="QWEN_MATRIX">模型对照：Qwen 基座 × LoRA × 三种方法</option>
          </select>
          <p v-if="experimentType === 'QWEN_MATRIX'" class="matrix-notice">此方案只调用本地 Qwen 基座和 LoRA，不调用 DeepSeek/M3E；六种组合共用一次图谱检索，逐项运行。训练标签尚未经教师审校，结果仅作探索性比较。</p>
        </template>
        <div class="experiment-input-pair" :class="{ 'student-mode-input': isStudentMode }">
          <label v-if="!isStudentMode" for="experiment-question">
            统一测试问题
            <textarea id="experiment-question" v-model="question" maxlength="2000" rows="3"></textarea>
          </label>
          <div v-else class="readonly-task-stem">
            <span class="eyebrow">指派实验题干（只读）</span>
            <p class="task-question-display">{{ question || '正在加载指派题目…' }}</p>
          </div>
          <label v-if="!isStudentMode" for="reference-answer">
            参考答案（可选）
            <textarea id="reference-answer" v-model="referenceAnswer" maxlength="4000" rows="3" placeholder="由教师或教材确定；留空时不生成文本相似度指标"></textarea>
          </label>
        </div>
        <fieldset v-if="!isStudentMode" class="expected-entities">
          <legend>期望答案实体（可选）</legend>
          <div class="expected-entity-list">
            <div v-for="(entity, index) in expectedEntityInputs" :key="index" class="expected-entity-row">
              <input v-model="expectedEntityInputs[index]" data-testid="expected-entity-input" :aria-label="`期望实体 ${index + 1}`" maxlength="100" placeholder="一个实体或答案短语，可含标点" />
              <button type="button" class="icon-button" :aria-label="`移除期望实体 ${index + 1}`" title="移除期望实体" :disabled="expectedEntityInputs.length <= 3" @click="removeExpectedEntity(index)"><X :size="15" /></button>
            </div>
          </div>
          <button type="button" class="text-button" aria-label="添加期望实体" :disabled="expectedEntityInputs.length >= 40" @click="addExpectedEntity"><Plus :size="15" />添加实体</button>
          <p>每格内容整体作为一个答案实体或短语，可包含逗号、顿号等标点。本题可将完整目标句填写在一个框中；多个目标请分别填写或添加输入框。答案实体不是三元组的主语、关系、宾语；最多 40 项，空白不参与评分。</p>
        </fieldset>
        <div class="experiment-actions">
          <label v-if="!isStudentMode && experimentType === 'METHODS'" class="ablation-control"><input v-model="includeAblations" type="checkbox" />附加消融实验</label>
          <span v-else-if="!isStudentMode">串行运行 6 个条件；RANKED 每个模型均执行证据筛选和答案校验</span>
          <span v-else>指派任务受限模式：执行预设实验条件，自动汇总对比</span>
          <button
            class="button button-dark"
            :disabled="running || (isStudentMode ? (!taskId && !assignedTask?.taskId) : !question.trim())"
            @click="runComparison"
          >
            <Play :size="15" />{{ running ? '实验运行中…' : (isStudentMode ? '运行问答对比' : '开始对比实验') }}
          </button>
        </div>
        <small v-if="!isStudentMode && experimentType === 'METHODS'">各模式使用相同模型与生成参数；排序评分组包含证据选择和生成两次调用，总耗时与 token 按实际记录。失败结果不参与评分，参考答案与期望实体仅用于生成后的评价。</small>
      </section>
      <section class="experiment-execution-status" aria-live="polite">
        <div>
          <p class="eyebrow">批量执行状态</p>
          <strong>{{ executionStatusText }}</strong>
        </div>
        <div class="execution-status-meta">
          <span v-if="!isStudentMode">预期条件 {{ expectedConditions }}</span>
          <span v-if="executionPhase === 'running'">已运行 {{ executionElapsed }} 秒</span>
          <span v-if="latest">实际返回 {{ latest.results.length }} 条</span>
        </div>
        <small v-if="executionPhase === 'running'">失败条件会保留为失败状态，不会生成或参与答案评分。</small>
      </section>
      <div v-if="error" class="inline-notice" role="alert"><CircleHelp :size="16" />{{ error }}</div>
      <section v-if="latest" class="content-section experiment-history">
        <div class="section-title-row">
          <div>
            <p class="eyebrow">{{ latest.evaluation?.experimentType === 'QWEN_MATRIX' ? 'Qwen 基座 × LoRA 实验矩阵' : '本次结果' }}</p>
            <h2>{{ latest.question }}</h2>
          </div>
          <span class="history-count"><Timer :size="13" />{{ new Date(latest.createdAt).toLocaleString('zh-CN') }}</span>
        </div>
        <ExperimentResultGrid
          :results="latest.results"
          :ratings="latest.ratings"
          :metrics="latest.evaluation?.metricsByMode"
          :mode-names="modeNames"
          :score="score"
          :hide-rating="isStudentMode"
          @rate="rate"
        />
        <small class="local-history-note">{{ latest.evaluation?.note || '早期导入记录，未生成可复核的实验指标。自动指标与人工评分分别呈现，不等同于事实正确性。' }}</small>
      </section>

      <!-- 历史记录快速选择区（仅管理端全功能模式展示） -->
      <section v-if="!isStudentMode" class="content-section experiment-history">
        <div class="section-title-row">
          <h2>实验历史</h2>
          <span class="history-count">{{ history.length }} 条</span>
        </div>
        <div v-if="history.length" class="saved-experiment-list">
          <button v-for="run in history" :key="run.id" @click="selectRun(run)">
            <span>{{ run.question }}</span>
            <small>{{ new Date(run.createdAt).toLocaleString('zh-CN') }}</small>
          </button>
        </div>
        <div v-else class="empty-state compact-empty">
          <FlaskConical :size="24" />
          <strong>还没有实验记录</strong>
          <span>运行一次对比，记录会保存到当前账户。</span>
        </div>
      </section>
    </div>

    <!-- 选项卡 2：个人实验统计（仅管理端全功能模式渲染） -->
    <template v-if="!isStudentMode">
      <div v-show="activeTab === 'summary'" role="tabpanel" aria-labelledby="tab-btn-summary" class="tab-panel-container">
        <section class="content-section">
          <div class="section-title-row">
            <h2>个人实验统计</h2>
            <small>方法实验与 Qwen 矩阵分组汇总；不同实验类型不会混算</small>
          </div>
          <div v-if="summary.length" class="table-scroll">
            <table class="experiment-table">
              <thead>
                <tr>
                  <th scope="col">实验条件</th>
                  <th scope="col">运行数</th>
                  <th scope="col">错误率</th>
                  <th scope="col">成功平均耗时</th>
                  <th scope="col">成功 P95</th>
                  <th scope="col">人工均分</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in summary" :key="row.key">
                  <td>{{ row.label }}</td>
                  <td>{{ row.count }}</td>
                  <td>{{ (row.failed * 100 / row.count).toFixed(1) }}%</td>
                  <td>{{ row.mean == null ? '未测得' : row.mean + ' ms' }}</td>
                  <td>{{ row.p95 == null ? '未测得' : row.p95 + ' ms' }}</td>
                  <td>{{ row.csat }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state compact-empty">
            <FlaskConical :size="24" />
            <strong>暂无统计数据</strong>
            <span>运行对比实验后将自动生成统计指标。</span>
          </div>
        </section>
      </div>

      <!-- 选项卡 3：实验历史（仅管理端全功能模式渲染） -->
      <div v-show="activeTab === 'history'" role="tabpanel" aria-labelledby="tab-btn-history" class="tab-panel-container">
        <section class="content-section experiment-history">
          <div class="section-title-row">
            <h2>实验历史</h2>
            <span class="history-count">{{ history.length }} 条</span>
          </div>
          <div v-if="history.length" class="saved-experiment-list">
            <button v-for="run in history" :key="run.id" @click="selectRun(run)">
              <span>{{ run.question }}</span>
              <small>{{ new Date(run.createdAt).toLocaleString('zh-CN') }}</small>
            </button>
          </div>
          <div v-else class="empty-state compact-empty">
            <FlaskConical :size="24" />
            <strong>还没有实验记录</strong>
            <span>运行一次对比，记录会保存到当前账户。</span>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.experiment-page {
  font-variant-numeric: tabular-nums;
}

.experiment-tabs-nav {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border-soft);
  padding-bottom: 8px;
  margin-bottom: 16px;
}

.exp-tab-button {
  padding: 8px 16px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all var(--motion);
}

.exp-tab-button:hover {
  color: var(--text);
  background: var(--surface-soft);
}

.exp-tab-button.active {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--accent);
  font-weight: 600;
}

.tab-panel-container {
  display: grid;
  gap: 16px;
}

.experiment-export-actions,
.experiment-actions,
.experiment-execution-status,
.execution-status-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.experiment-methods,
.experiment-execution-status,
.matrix-notice {
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--surface-tint);
}

.experiment-methods summary {
  width: fit-content;
  color: var(--accent-strong);
  font-weight: 600;
  cursor: pointer;
}

.experiment-methods dl,
.experiment-input-pair,
.expected-entity-list {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.student-mode-input {
  grid-template-columns: 1fr;
}

.readonly-task-stem {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  background: var(--surface-base);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
}

.readonly-task-stem .eyebrow {
  margin: 0;
}

.task-question-display {
  margin: 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.5;
}

.experiment-methods dt {
  font-weight: 650;
}

.experiment-methods dd {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.experiment-run-form {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.experiment-input-pair {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.experiment-run-form label,
.expected-entities legend {
  display: grid;
  gap: 5px;
  color: var(--text);
  font-weight: 600;
  font-size: 13px;
}

.experiment-run-form > select {
  justify-self: start;
}

.experiment-run-form textarea,
.expected-entity-row input {
  width: 100%;
  font-size: 14px;
  font-weight: 400;
}

.expected-entities {
  min-width: 0;
  margin: 4px 0 0;
  padding: 0;
  border: 0;
}

.expected-entities legend {
  margin-bottom: 8px;
  padding: 0;
}

.expected-entity-row {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.expected-entity-row .icon-button {
  flex: none;
}

.expected-entities .text-button {
  margin-top: 6px;
}

.expected-entities p,
.experiment-run-form > small,
.matrix-notice {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.65;
}

.matrix-notice {
  color: var(--warning);
  background: var(--warning-soft);
}

.experiment-actions {
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--border-soft);
}

.experiment-actions .ablation-control {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.experiment-actions > span,
.execution-status-meta,
.experiment-execution-status small {
  color: var(--text-muted);
  font-size: 12px;
}

.experiment-execution-status {
  justify-content: space-between;
}

.experiment-execution-status .eyebrow {
  margin: 0;
}

.experiment-execution-status small {
  flex-basis: 100%;
}

.experiment-history .section-title-row {
  flex-wrap: wrap;
}

.table-scroll {
  margin-top: 12px;
  overflow: auto;
}

.compact-empty {
  padding: 18px;
}

.experiment-table {
  min-width: 520px;
  overflow: hidden;
}

.experiment-table :is(th, td):not(:first-child) {
  text-align: right;
}

.fixed-suite-section {
  display: grid;
  gap: 12px;
}

.suite-version-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  color: var(--accent-strong);
  vertical-align: middle;
  margin-left: 8px;
}

.suite-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.suite-loading {
  color: var(--text-muted);
  font-size: 13px;
  padding: 12px 0;
}

.retry-btn {
  margin-left: 12px;
  color: var(--accent-strong);
  text-decoration: underline;
  cursor: pointer;
}

.fixed-cases-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.fixed-case-card {
  padding: 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-soft);
  background: var(--surface);
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: border-color var(--motion);
}

.fixed-case-card.selected {
  border-color: var(--accent);
  background: var(--surface-tint);
}

.case-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.case-select-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-weight: 600;
  font-size: 13px;
}

.case-id {
  font-family: var(--font-mono);
  color: var(--text);
}

.case-meta-tag {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--surface-soft);
  padding: 2px 6px;
  border-radius: 4px;
}

.case-question {
  margin: 0;
  font-size: 13px;
  color: var(--text);
  line-height: 1.5;
  flex: 1;
}

.case-card-actions {
  display: flex;
  justify-content: flex-end;
}

.fill-btn {
  font-size: 12px;
  color: var(--accent-strong);
  cursor: pointer;
}

.batch-runner-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  margin-top: 4px;
}

.batch-runner-info {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--text);
}

.batch-runner-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.batch-run-btn {
  border-color: var(--accent);
  color: var(--accent-strong);
}

.retry-failed-btn {
  border-color: var(--danger);
  color: var(--danger);
}

.batch-items-list {
  display: grid;
  gap: 6px;
  margin-top: 8px;
}

.batch-item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-soft);
  font-size: 13px;
  background: var(--surface);
}

.batch-item-row.success {
  border-color: var(--success);
  background: var(--success-soft);
}

.batch-item-row.failed {
  border-color: var(--danger);
  background: var(--danger-soft);
}

.batch-item-row.running {
  border-color: var(--accent);
  background: var(--surface-tint);
}

.batch-status-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--surface-soft);
}

.batch-case-id {
  font-family: var(--font-mono);
  font-weight: 600;
  min-width: 120px;
}

.batch-item-question {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-item-error {
  color: var(--danger);
  font-size: 12px;
}

@media (max-width: 700px) {
  .experiment-methods dl,
  .experiment-input-pair,
  .expected-entity-list,
  .fixed-cases-grid {
    grid-template-columns: 1fr;
  }
  .experiment-run-form {
    padding: 16px;
  }
}
</style>
