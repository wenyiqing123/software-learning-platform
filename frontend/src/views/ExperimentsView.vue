<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDownToLine, CircleHelp, FlaskConical, Play, Plus, Timer, X } from '@lucide/vue'
import { apiError, learningApi } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import { experimentCsv, experimentJson, type ExperimentExportRun } from '../domain/experimentExport'
import ExperimentResultGrid from '../components/ExperimentResultGrid.vue'

defineProps<{ embedded?: boolean }>()

const route = useRoute()
const router = useRouter()

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
const taskId = computed(() => Number(new URLSearchParams(window.location.search).get('taskId')) || undefined)
const running = ref(false), error = ref(''), latest = ref<Run | null>(null), history = ref<Run[]>([])
const executionPhase = ref<'idle' | 'running' | 'complete'>('idle')
const executionStartedAt = ref<number | null>(null)
const executionElapsed = ref(0)
let executionTimer: ReturnType<typeof setInterval> | null = null

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
  try {
    history.value = await learningApi.listExperiments()
  } catch (e) {
    error.value = apiError(e)
  }
})

onBeforeUnmount(() => {
  if (executionTimer) clearInterval(executionTimer)
})

function addExpectedEntity() {
  if (expectedEntityInputs.value.length < 40) expectedEntityInputs.value.push('')
}

function removeExpectedEntity(index: number) {
  if (expectedEntityInputs.value.length > 3) expectedEntityInputs.value.splice(index, 1)
}

async function runComparison() {
  if (!question.value.trim() || running.value) return
  const expectedEntities = expectedEntityInputs.value.map(value => value.trim()).filter(Boolean)
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
    const saved = await learningApi.runExperiment({
      question: question.value.trim(),
      referenceAnswer: referenceAnswer.value.trim(),
      expectedEntities,
      experimentType: experimentType.value,
      includeAblations: experimentType.value === 'METHODS' && includeAblations.value,
      taskId: taskId.value
    }) as Run
    latest.value = saved
    history.value = [saved, ...history.value].slice(0, 100)
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
  if (!latest.value || !score) return
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
    download(JSON.stringify(experimentJson(history.value as unknown as ExperimentExportRun[]), null, 2), 'software-engineering-experiments.json', 'application/json')
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
</script>

<template>
  <div class="page-stack experiment-page">
    <SectionHeading :embedded="embedded" title="问答实验" description="固定问题与实验条件，对照问答策略或 Qwen 基座与 LoRA。">
      <div class="experiment-export-actions">
        <button class="button button-outline" :disabled="!history.length" @click="exportResults"><ArrowDownToLine :size="16" />导出 JSON</button>
        <button class="button button-outline" :disabled="!history.length" @click="exportCsv"><ArrowDownToLine :size="16" />导出 CSV</button>
      </div>
    </SectionHeading>

    <!-- Query/Tab 分段栏：实验输入与结果、个人统计、实验历史 -->
    <div role="tablist" class="experiment-tabs-nav" aria-label="实验功能分段">
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
    <div v-show="activeTab === 'run'" role="tabpanel" aria-labelledby="tab-btn-run" class="tab-panel-container">
      <details class="experiment-methods">
        <summary>实验条件说明</summary>
        <dl>
          <div><dt>大模型直答</dt><dd>直接提问，不访问图谱。</dd></div>
          <div><dt>三元组 + 大模型</dt><dd>使用共享证据，不向提示词注入分数。</dd></div>
          <div><dt>排序评分 + 原始提示词</dt><dd>先从排序评分证据中选择依据，再生成回答。</dd></div>
        </dl>
      </details>
      <section class="content-section experiment-run-form" aria-label="配置对照实验">
        <label for="experiment-type">实验方案</label>
        <select id="experiment-type" v-model="experimentType">
          <option value="METHODS">方法对照：固定默认模型</option>
          <option value="QWEN_MATRIX">模型对照：Qwen 基座 × LoRA × 三种方法</option>
        </select>
        <p v-if="experimentType === 'QWEN_MATRIX'" class="matrix-notice">此方案只调用本地 Qwen 基座和 LoRA，不调用 DeepSeek/M3E；六种组合共用一次图谱检索，逐项运行。训练标签尚未经教师审校，结果仅作探索性比较。</p>
        <div class="experiment-input-pair">
          <label for="experiment-question">统一测试问题<textarea id="experiment-question" v-model="question" maxlength="2000" rows="3"></textarea></label>
          <label for="reference-answer">参考答案（可选）<textarea id="reference-answer" v-model="referenceAnswer" maxlength="4000" rows="3" placeholder="由教师或教材确定；留空时不生成文本相似度指标"></textarea></label>
        </div>
        <fieldset class="expected-entities">
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
          <label v-if="experimentType === 'METHODS'" class="ablation-control"><input v-model="includeAblations" type="checkbox" />附加消融实验</label>
          <span v-else>串行运行 6 个条件；RANKED 每个模型均执行证据筛选和答案校验</span>
          <button class="button button-dark" :disabled="running || !question.trim()" @click="runComparison"><Play :size="15" />{{ running ? '矩阵实验运行中…' : '开始对比实验' }}</button>
        </div>
        <small v-if="experimentType === 'METHODS'">各模式使用相同模型与生成参数；排序评分组包含证据选择和生成两次调用，总耗时与 token 按实际记录。失败结果不参与评分，参考答案与期望实体仅用于生成后的评价。</small>
      </section>
      <section class="experiment-execution-status" aria-live="polite">
        <div>
          <p class="eyebrow">批量执行状态</p>
          <strong>{{ executionStatusText }}</strong>
        </div>
        <div class="execution-status-meta">
          <span>预期条件 {{ expectedConditions }}</span>
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
        <ExperimentResultGrid :results="latest.results" :ratings="latest.ratings" :metrics="latest.evaluation?.metricsByMode" :mode-names="modeNames" :score="score" @rate="rate" />
        <small class="local-history-note">{{ latest.evaluation?.note || '早期导入记录，未生成可复核的实验指标。自动指标与人工评分分别呈现，不等同于事实正确性。' }}</small>
      </section>

      <!-- 历史记录快速选择区（移出首屏主阅读区，置于底部） -->
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

    <!-- 选项卡 2：个人实验统计 -->
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

    <!-- 选项卡 3：实验历史 -->
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

@media (max-width: 700px) {
  .experiment-methods dl,
  .experiment-input-pair,
  .expected-entity-list {
    grid-template-columns: 1fr;
  }
  .experiment-run-form {
    padding: 16px;
  }
}
</style>
