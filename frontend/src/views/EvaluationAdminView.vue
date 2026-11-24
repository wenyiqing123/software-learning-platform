<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Download, RefreshCw } from '@lucide/vue'
import { apiError, learningApi, type EvaluationSummary } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'

defineProps<{ embedded?: boolean }>()

const rows = ref<EvaluationSummary[]>([])
const condition = ref('')
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await learningApi.evaluationSummary(condition.value || undefined)
  } catch (cause) {
    error.value = apiError(cause, '评估汇总暂时无法读取')
  } finally {
    loading.value = false
  }
}

async function download() {
  try {
    const blob = await learningApi.exportEvaluationSummary()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'learning-evaluation-anonymized.csv'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (cause) {
    error.value = apiError(cause, '去标识化导出失败')
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page-stack evaluation-admin-page">
    <SectionHeading :embedded="embedded" title="去标识化学习评估汇总" description="按条件展示描述性统计；导出仅包含匿名编号及评估记录。">
      <div class="row-actions">
        <button class="button button-outline" :disabled="loading" @click="load"><RefreshCw :size="15" />刷新</button>
        <button class="button button-dark" :disabled="loading" @click="download"><Download :size="15" />导出匿名 CSV</button>
      </div>
    </SectionHeading>
    <div v-if="error" class="inline-notice error-notice" role="alert">{{ error }}</div>
    <section class="workspace-toolbar evaluation-admin-filter" aria-label="评估筛选">
      <label>条件筛选
        <select v-model="condition" @change="load">
          <option value="">全部条件</option>
          <option value="METHODS">问答方法对照</option>
          <option value="QWEN_MATRIX">Qwen 基座与 LoRA 对照</option>
          <option value="LEARNING_TASK">课程学习任务</option>
        </select>
      </label>
      <span>成绩、心理努力与用时分开呈现。</span>
    </section>
    <FeedbackState v-if="loading" loading loading-title="正在读取汇总" />
    <div v-else-if="rows.length" class="evaluation-table" role="region" aria-label="按条件统计" tabindex="0">
      <table>
        <caption>去标识化学习评估分组描述性统计（均值/中位数，SD 标准差，IQR 四分位距）</caption>
        <thead>
          <tr>
            <th scope="col">实验条件</th>
            <th scope="col">样本量</th>
            <th scope="col">前测 (0–100)</th>
            <th scope="col">后测 (0–100)</th>
            <th scope="col">延后测验 (0–100)</th>
            <th scope="col">Paas 心理努力 (1–9)</th>
            <th scope="col">用时 (秒)</th>
            <th scope="col">反馈数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.conditionKey">
            <th scope="row">{{ row.conditionKey }}</th>
            <td>{{ row.sampleCount }}</td>
            <td>{{ row.meanPreScore ?? '—' }} / {{ row.medianPreScore ?? '—' }}<small>SD {{ row.stddevPreScore ?? '—' }} · IQR {{ row.iqrPreScore ?? '—' }}</small></td>
            <td>{{ row.meanPostScore ?? '—' }} / {{ row.medianPostScore ?? '—' }}<small>SD {{ row.stddevPostScore ?? '—' }} · IQR {{ row.iqrPostScore ?? '—' }}</small></td>
            <td>{{ row.meanDelayedScore ?? '—' }} / {{ row.medianDelayedScore ?? '—' }}<small>IQR {{ row.iqrDelayedScore ?? '—' }}</small></td>
            <td>{{ row.meanEffort ?? '—' }} / {{ row.medianEffort ?? '—' }}<small>SD {{ row.stddevEffort ?? '—' }} · IQR {{ row.iqrEffort ?? '—' }}</small></td>
            <td>{{ row.meanElapsedSeconds ?? '—' }} / {{ row.medianElapsedSeconds ?? '—' }} 秒<small>SD {{ row.stddevElapsedSeconds ?? '—' }} · IQR {{ row.iqrElapsedSeconds ?? '—' }}</small></td>
            <td>{{ row.feedbackCount }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <FeedbackState v-else-if="!error" empty empty-title="暂无已完成评估" empty-text="完成评估会进入匿名聚合汇总。" />
    <p class="evaluation-admin-note">单元格依次列出均值与中位数；SD 为标准差，IQR 为四分位距。统计为描述性结果，不作因果结论。</p>
  </div>
</template>

<style scoped>
.evaluation-admin-filter > span,
.evaluation-admin-note {
  color: var(--text-muted);
  font-size: 13px;
}

.evaluation-admin-note {
  margin: 0;
}

.evaluation-table {
  min-width: 0;
  overflow-x: auto;
  border-radius: var(--radius-md);
  font-variant-numeric: tabular-nums;
  background: var(--surface);
  border: 1px solid var(--border-default);
}

.evaluation-table table {
  width: 100%;
  min-width: 680px;
  border-collapse: collapse;
  text-align: right;
}

.evaluation-table caption {
  text-align: left;
  padding: 10px 14px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 500;
  border-bottom: 1px solid var(--border-soft);
  background: var(--surface-soft);
}

.evaluation-table :is(th, td) {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-soft);
}

.evaluation-table thead th {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  background: var(--surface);
  white-space: nowrap;
}

.evaluation-table thead th:first-child,
.evaluation-table tbody th {
  text-align: left;
}

.evaluation-table thead th:not(:first-child) {
  text-align: right;
}

.evaluation-table tbody th {
  font-weight: 600;
  color: var(--text);
  background: transparent;
  overflow-wrap: anywhere;
}

.evaluation-table tbody tr:last-child :is(th, td) {
  border-bottom: 0;
}

.evaluation-table small {
  display: block;
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.45;
}
</style>
