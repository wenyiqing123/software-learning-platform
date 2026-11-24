<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Check, CircleAlert, Clock3, FileCheck2, ShieldCheck } from '@lucide/vue'
import { RouterLink, useRoute } from 'vue-router'
import { learningApi, type EvaluationSession } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'

const route = useRoute()
const consentAccepted = ref(false)
const conditionKey = ref(typeof route.query.conditionKey === 'string' ? route.query.conditionKey : 'METHODS')
const taskKey = ref(typeof route.query.taskKey === 'string' ? route.query.taskKey : 'AI_QA_TASK_1')
const taskOrder = ref(typeof route.query.taskOrder === 'string' ? route.query.taskOrder : 'A→B')
const preScore = ref<number | null>(null)
const postScore = ref<number | null>(null)
const delayedScore = ref<number | null>(null)
const effort = ref<number | null>(null)
const feedback = ref('')
const session = ref<EvaluationSession | null>(null)
const history = ref<EvaluationSession[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const elapsed = ref(0)

const assignmentId = computed(() => Number(route.query.assignmentId) || undefined)
let timer: ReturnType<typeof setInterval> | undefined

const active = computed(() => session.value && !session.value.completedAt)
const elapsedText = computed(() => Math.floor(elapsed.value / 60) + '分 ' + String(elapsed.value % 60).padStart(2, '0') + '秒')

function getEffortAnchor(val: number | null): string {
  switch (val) {
    case 1: return '非常低'
    case 2: return '很低'
    case 3: return '较低'
    case 4: return '偏低'
    case 5: return '中等'
    case 6: return '偏高'
    case 7: return '较高'
    case 8: return '很高'
    case 9: return '非常高'
    default: return ''
  }
}

const wizardSteps = computed(() => [
  {
    id: 1,
    title: '知情说明',
    status: !session.value ? 'current' : 'done',
    statusText: !session.value ? '进行中' : '已确认'
  },
  {
    id: 2,
    title: '前测',
    status: !session.value ? 'pending' : preScore.value !== null ? 'done' : 'current',
    statusText: !session.value ? '待开始评估' : preScore.value !== null ? '已完成' : '待填写'
  },
  {
    id: 3,
    title: '任务与计时',
    status: !session.value ? 'pending' : session.value.completedAt ? 'done' : 'current',
    statusText: !session.value ? '待解锁' : session.value.completedAt ? '已完成' : '计时中'
  },
  {
    id: 4,
    title: '后测',
    status: !session.value ? 'pending' : postScore.value !== null ? 'done' : 'current',
    statusText: !session.value ? '待解锁' : postScore.value !== null ? '已完成' : '待填写'
  },
  {
    id: 5,
    title: 'Paas 心理努力',
    status: !session.value ? 'pending' : effort.value !== null ? 'done' : 'current',
    statusText: !session.value ? '待解锁' : effort.value !== null ? `${effort.value} 级已选` : '待评分'
  },
  {
    id: 6,
    title: '延后测验',
    status: !session.value ? 'pending' : delayedScore.value !== null ? 'done' : 'current',
    statusText: !session.value ? '待解锁' : delayedScore.value !== null ? '已补录' : '约定时间补录'
  }
])

function updateTimer() {
  if (!session.value?.startedAt || session.value.completedAt) return
  elapsed.value = Math.max(0, Math.floor((Date.now() - new Date(session.value.startedAt).getTime()) / 1000))
}

async function load() {
  loading.value = true
  try {
    history.value = await learningApi.evaluationSessions()
  } catch {
    error.value = '评估记录暂时无法读取。'
  } finally {
    loading.value = false
  }
}

async function start() {
  if (!consentAccepted.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    session.value = await learningApi.startEvaluation({
      conditionKey: conditionKey.value,
      taskKey: taskKey.value,
      taskOrder: taskOrder.value,
      assignmentId: assignmentId.value,
      consentVersion: 'evaluation-v2',
      consentAccepted: true
    })
    timer = setInterval(updateTimer, 1000)
    notice.value = '评估会话已开始，请按当前顺序完成前测、学习任务、后测和主观反馈。'
  } catch {
    error.value = '无法开始评估，请检查输入和服务连接。'
  } finally {
    saving.value = false
  }
}

async function measure(phase: 'PRE' | 'POST' | 'DELAYED') {
  if (!session.value || saving.value) return
  const score = phase === 'PRE' ? preScore.value : phase === 'POST' ? postScore.value : delayedScore.value
  if (score === null || score < 0 || score > 100) {
    error.value = '请输入0到100之间的测验得分。'
    return
  }
  saving.value = true
  try {
    await learningApi.recordEvaluationMeasurement(session.value.id, { phase, score })
    notice.value = (phase === 'PRE' ? '前测' : phase === 'POST' ? '后测' : '延后测验') + '已保存。'
  } catch {
    error.value = '测验结果保存失败，请重试。'
  } finally {
    saving.value = false
  }
}

async function complete() {
  if (!session.value || saving.value || effort.value === null || postScore.value === null) {
    error.value = '请先填写后测和心理努力评分。'
    return
  }
  saving.value = true
  updateTimer()
  try {
    const finished = await learningApi.completeEvaluation(session.value.id, {
      elapsedSeconds: Math.max(1, elapsed.value),
      psychologicalEffort: effort.value,
      feedback: feedback.value
    })
    session.value = finished
    history.value = [finished, ...history.value.filter(item => item.id !== finished.id)]
    notice.value = '评估已完成，数据仅用于匿名聚合分析。'
    if (timer) clearInterval(timer)
  } catch {
    error.value = '评估完成信息保存失败，请重试。'
  } finally {
    saving.value = false
  }
}

function reset() {
  session.value = null
  preScore.value = null
  postScore.value = null
  delayedScore.value = null
  effort.value = null
  feedback.value = ''
  notice.value = ''
  error.value = ''
  consentAccepted.value = false
  if (timer) clearInterval(timer)
}

onMounted(() => {
  void load()
  timer = setInterval(updateTimer, 1000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="page-stack evaluation-page">
    <SectionHeading eyebrow="研究评估" title="学习投入与效果记录" description="分别记录测验成绩、任务用时与心理努力自评，不等同于完整认知负荷测量。">
      <span class="evaluation-status"><ShieldCheck :size="15" />数据按账号隔离</span>
    </SectionHeading>

    <!-- 评估流程 stepper (6 步固定视觉槽位) -->
    <nav class="evaluation-stepper" aria-label="评估流程进度">
      <div
        v-for="s in wizardSteps"
        :key="s.id"
        class="step-item"
        :class="['status-' + s.status]"
      >
        <span class="step-badge">{{ s.id }}</span>
        <div class="step-body">
          <span class="step-title">{{ s.title }}</span>
          <span class="step-desc">{{ s.statusText }}</span>
        </div>
      </div>
    </nav>

    <div v-if="error" class="inline-notice error-notice" role="alert"><CircleAlert :size="16" />{{ error }}</div>
    <div v-if="notice" class="inline-notice success" role="status"><Check :size="16" />{{ notice }}</div>

    <section v-if="!session" class="content-section evaluation-consent">
      <div class="evaluation-consent-copy">
        <p class="eyebrow">开始前说明</p>
        <h2>自愿参与，数据用于去标识化统计</h2>
        <p>本流程记录实验条件、任务顺序、前测/后测/延后测验、任务用时、Paas 九点心理努力评分和可选反馈。系统不采集任何生理信号，不把单次评分直接解释为认知负荷，也不预设学习效果结论。</p>
        <ol class="evaluation-protocol">
          <li>阅读知情说明并确认任务条件和顺序。</li>
          <li>完成前测后执行指定课程/问答任务，计时从开始到完成自动记录。</li>
          <li>完成后测和 1–9 级心理努力评分；延后测验可在约定时间补录。</li>
        </ol>
      </div>
      <div class="evaluation-form">
        <label>实验条件
          <select v-model="conditionKey">
            <option value="METHODS">问答方法对照</option>
            <option value="QWEN_MATRIX">Qwen 基座与 LoRA 对照</option>
            <option value="LEARNING_TASK">课程学习任务</option>
          </select>
        </label>
        <label>任务编号<input v-model="taskKey" maxlength="120" /></label>
        <label>任务顺序<input v-model="taskOrder" maxlength="120" placeholder="例如 A→B 或 先图谱后直答" /></label>
        <label class="consent-check"><input v-model="consentAccepted" type="checkbox" />我已阅读说明并同意记录本次评估数据</label>
        <button class="button button-dark" :disabled="!consentAccepted || saving" @click="start"><FileCheck2 :size="16" />开始评估</button>
      </div>
    </section>

    <section v-else class="evaluation-workspace">
      <div class="evaluation-topline">
        <div><p class="eyebrow">{{ session.conditionKey }} · {{ session.taskOrder || taskOrder }}</p><h2>{{ session.taskKey }}</h2></div>
        <span class="evaluation-timer"><Clock3 :size="15" />{{ elapsedText }}</span>
      </div>
      <div class="evaluation-grid">
        <article class="evaluation-card">
          <p class="eyebrow">1 · 前测</p><h3>开始学习前完成</h3>
          <label>得分（0–100）<input v-model.number="preScore" type="number" min="0" max="100" /></label>
          <button class="button button-outline button-small" :disabled="saving || preScore === null" @click="measure('PRE')">保存前测</button>
        </article>
        <article class="evaluation-card">
          <p class="eyebrow">2 · 当前任务</p><h3>完成课程学习或问答任务</h3>
          <p>可返回课程、知识图谱、练习或 AI 助教完成任务；用时由系统计时。</p>
          <RouterLink v-if="route.query.taskId" class="button button-outline button-small" :to="{ path: '/experiments', query: { taskId: String(route.query.taskId), assignmentId: assignmentId ? String(assignmentId) : undefined } }">进入问答任务</RouterLink>
        </article>
        <article class="evaluation-card">
          <p class="eyebrow">3 · 后测</p><h3>完成任务后填写</h3>
          <label>得分（0–100）<input v-model.number="postScore" type="number" min="0" max="100" /></label>
          <button class="button button-outline button-small" :disabled="saving || postScore === null" @click="measure('POST')">保存后测</button>
        </article>
      </div>
      <section class="content-section evaluation-complete">
        <div>
          <p class="eyebrow">4 · Paas 心理努力评分</p>
          <h3 id="effort-title">请评价完成本任务时的心理努力</h3>
          <p id="effort-description">1 = 非常低，3 = 较低，5 = 中等，7 = 较高，9 = 非常高。</p>
        </div>
        <fieldset class="effort-scale" aria-labelledby="effort-title" aria-describedby="effort-description">
          <label v-for="value in 9" :key="value" :class="{ selected: effort === value }">
            <span class="effort-num">{{ value }}</span>
            <input v-model="effort" type="radio" name="effort" :value="value" :aria-label="value + ' 级 · ' + getEffortAnchor(value)" />
          </label>
        </fieldset>
        <div v-if="effort !== null" class="effort-selection-guide">
          已选择：<strong>{{ effort }} 级</strong>（{{ getEffortAnchor(effort) }}）
        </div>
        <label>可选反馈<textarea v-model="feedback" maxlength="2000" rows="2" placeholder="可以描述任务难度、证据是否有帮助或页面体验。"></textarea></label>
        <div class="evaluation-actions">
          <button class="button button-outline" @click="reset">放弃本次</button>
          <button class="button button-dark" :disabled="saving || effort === null || postScore === null" @click="complete"><Check :size="16" />完成并保存</button>
        </div>
      </section>
      <section class="content-section delayed-card">
        <div><p class="eyebrow">5 · 短时延后测验</p><h3>在约定时间补录理解成绩</h3><p>延后测验独立保存，不会改写任务用时或 Paas 评分。</p></div>
        <label>延后测验得分（0–100）<input v-model.number="delayedScore" type="number" min="0" max="100" /></label>
        <button class="button button-outline button-small" :disabled="saving || delayedScore === null" @click="measure('DELAYED')">保存延后测验</button>
      </section>
    </section>

    <section v-if="!loading && history.length" class="content-section evaluation-history">
      <div class="section-title-row"><h2>最近评估会话</h2><span>{{ history.length }} 条</span></div>
      <div class="evaluation-history-row" v-for="item in history" :key="item.id"><strong>{{ item.conditionKey }}</strong><span>{{ item.taskKey }}</span><span>{{ item.completedAt ? '已完成' : '进行中' }}</span><span v-if="item.psychologicalEffort">心理努力 {{ item.psychologicalEffort }}/9</span></div>
    </section>
  </div>
</template>

<style scoped>
.evaluation-page {
  font-variant-numeric: tabular-nums;
}

.evaluation-status,
.evaluation-timer {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--accent-strong);
  font-size: 12px;
}

/* Stepper 6 slots */
.evaluation-stepper {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 20px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: 12px 14px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  min-width: 0;
  transition: all var(--motion);
}

.step-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  background: var(--surface-soft);
  color: var(--text-muted);
  border: 1px solid var(--border-soft);
}

.step-body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.step-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.step-desc {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Stepper states */
.step-item.status-done .step-badge {
  background: var(--accent-soft);
  color: var(--accent-strong);
  border-color: var(--accent);
}

.step-item.status-done .step-title {
  color: var(--accent-strong);
}

.step-item.status-current {
  background: var(--surface-soft);
  border: 1px solid var(--accent-soft);
}

.step-item.status-current .step-badge {
  background: var(--accent);
  color: var(--surface);
  border-color: var(--accent);
}

.step-item.status-current .step-title {
  color: var(--text);
  font-weight: 700;
}

.step-item.status-pending {
  opacity: 0.55;
}

.step-item.status-pending .step-title,
.step-item.status-pending .step-desc {
  color: var(--text-muted);
}

.evaluation-consent {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  gap: 24px;
}

.evaluation-consent h2,
.evaluation-topline h2 {
  margin: 0;
  color: var(--text);
  font-size: 18px;
  line-height: 1.4;
}

.evaluation-consent-copy p:not(.eyebrow),
.evaluation-protocol,
.evaluation-workspace p:not(.eyebrow) {
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.65;
}

.evaluation-protocol {
  margin: 12px 0 0;
  padding-left: 20px;
}

.evaluation-protocol li + li {
  margin-top: 6px;
}

.evaluation-form,
.evaluation-workspace,
.evaluation-complete,
.evaluation-card {
  display: grid;
  align-content: start;
  gap: 12px;
  min-width: 0;
}

.evaluation-form {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-content: start;
  padding-inline-start: 20px;
  border-inline-start: 1px solid var(--border-soft);
}

.evaluation-form label,
.evaluation-workspace label:not(.effort-scale label) {
  display: grid;
  gap: 5px;
  min-width: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.evaluation-form > label:first-child,
.evaluation-form > .consent-check {
  grid-column: 1 / -1;
}

.evaluation-form > .consent-check {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.evaluation-form :is(input:not([type=checkbox]), select),
.evaluation-card input,
.evaluation-complete textarea,
.delayed-card input {
  width: 100%;
}

.evaluation-form > .button,
.evaluation-card > .button {
  justify-self: start;
}

.evaluation-topline,
.evaluation-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
}

.evaluation-actions {
  justify-content: flex-end;
}

.evaluation-timer {
  padding: 6px 10px;
  background: var(--accent-soft);
  border-radius: var(--radius-pill);
}

.evaluation-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

.evaluation-card {
  gap: 8px;
  padding: 14px 16px;
}

.evaluation-card + .evaluation-card {
  border-inline-start: 1px solid var(--border-soft);
}

.evaluation-card :is(p, h3),
.evaluation-complete :is(p, h3),
.delayed-card :is(p, h3) {
  margin: 0;
}

.evaluation-card .eyebrow,
.evaluation-complete .eyebrow,
.delayed-card .eyebrow {
  margin-bottom: 3px;
}

.evaluation-workspace h3 {
  color: var(--text);
  font-size: 16px;
  line-height: 1.45;
}

.effort-scale {
  display: grid;
  grid-template-columns: repeat(9, minmax(0, 1fr));
  gap: 8px;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.effort-scale label {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 3px;
  min-height: 48px;
  min-width: 44px;
  padding: 4px;
  color: var(--text-muted);
  background: var(--surface-soft);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  font-size: 18px;
  font-weight: 650;
  cursor: pointer;
  transition: color var(--motion), background-color var(--motion), border-color var(--motion);
}

.effort-scale label:hover,
.effort-scale label.selected {
  color: var(--accent-strong);
  background: var(--accent-soft);
  border-color: var(--border-focus);
}

.effort-scale label:has(:focus-visible) {
  box-shadow: var(--focus-ring);
}

.effort-scale input {
  width: 14px;
  height: 14px;
  margin: 0;
}

.effort-num {
  line-height: 1;
}

.effort-selection-guide {
  font-size: 13px;
  color: var(--accent-strong);
  padding: 6px 10px;
  background: var(--accent-soft);
  border-radius: var(--radius-sm);
  display: inline-block;
}

.delayed-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(140px, 210px) auto;
  align-items: end;
  gap: 12px;
}

.evaluation-history {
  display: grid;
  gap: 8px;
}

.evaluation-history-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr) minmax(0, 0.65fr) minmax(0, 1fr);
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-soft);
  color: var(--text-muted);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.evaluation-history-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

@media (max-width: 900px) {
  .evaluation-stepper {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .evaluation-consent,
  .delayed-card {
    grid-template-columns: 1fr;
  }
  .evaluation-form {
    border-inline-start: 0;
    border-top: 1px solid var(--border-soft);
    padding: 14px 0 0;
  }
}

@media (max-width: 600px) {
  .evaluation-stepper {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .evaluation-grid,
  .evaluation-form {
    grid-template-columns: 1fr;
  }
  .evaluation-card + .evaluation-card {
    border-inline-start: 0;
    border-top: 1px solid var(--border-soft);
  }
  .effort-scale {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .evaluation-history-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
