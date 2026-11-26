<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { TrendingUp } from '@lucide/vue'
import { useTheme } from '../composables/useTheme'
import {
  buildExperimentBarOption,
  computeBaselineDeltas,
  type ExperimentConditionResult,
  type ExperimentMetricScores,
} from '../domain/experimentComparisonCharts'

const props = withDefaults(
  defineProps<{
    results: ExperimentConditionResult[]
    metrics?: Record<string, ExperimentMetricScores>
    modeNames: Record<string, string>
  }>(),
  {
    metrics: () => ({}),
  }
)

const { isDark } = useTheme()
const chartHost = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const deltaSummary = computed(() => computeBaselineDeltas(props.results, props.metrics, props.modeNames))
const hasMetricValues = computed(() =>
  deltaSummary.value.rows.some(
    (r) => r.rougeL !== undefined || r.bleu4 !== undefined || r.entityRecall !== undefined,
  ),
)

function canRenderCanvas(): boolean {
  if (typeof document === 'undefined') return false
  try {
    const probe = document.createElement('canvas')
    return Boolean(typeof probe.getContext === 'function' && probe.getContext('2d'))
  } catch {
    return false
  }
}

function safeDispose(chart: echarts.ECharts | null) {
  if (!chart) return
  try {
    chart.dispose()
  } catch {
    // 忽略测试无 Canvas 上下文的清理异常
  }
}

function renderChart() {
  safeDispose(chartInstance)
  chartInstance = null
  if (!hasMetricValues.value || !chartHost.value || !canRenderCanvas()) return
  try {
    chartInstance = echarts.init(chartHost.value)
    chartInstance.setOption(buildExperimentBarOption(deltaSummary.value.rows, isDark.value))
  } catch {
    safeDispose(chartInstance)
    chartInstance = null
  }
}

function resizeChart() {
  try {
    chartInstance?.resize()
  } catch {
    // 忽略隐藏容器 resize
  }
}

function formatDelta(val?: number): string {
  if (val === undefined) return '—'
  return val >= 0 ? `+${val}%` : `${val}%`
}

watch(
  () => [props.results, props.metrics, isDark.value],
  () => {
    void nextTick(renderChart)
  },
  { deep: true }
)

onMounted(() => {
  void nextTick(renderChart)
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  safeDispose(chartInstance)
  chartInstance = null
})
</script>

<template>
  <section
    v-if="deltaSummary.rows.length && hasMetricValues"
    class="exp-compare-visual"
    data-testid="experiment-comparison-charts"
    aria-label="多条件自动指标对比图"
  >
    <div class="exp-compare-head">
      <div class="exp-compare-title">
        <TrendingUp :size="16" aria-hidden="true" />
        <strong>多条件客观指标对比与基线差值</strong>
      </div>
      <small v-if="deltaSummary.baselineLabel">基线参照：{{ deltaSummary.baselineLabel }}</small>
    </div>

    <div v-if="deltaSummary.baselineKey && deltaSummary.rows.length > 1" class="exp-delta-strip" data-testid="experiment-delta-strip">
      <article
        v-for="row in deltaSummary.rows.filter((r) => r.key !== deltaSummary.baselineKey)"
        :key="row.key"
        class="exp-delta-pill"
      >
        <span class="exp-delta-name">{{ row.label }}</span>
        <div class="exp-delta-metrics">
          <span v-if="row.rougeDeltaPct !== undefined" :class="{ positive: row.rougeDeltaPct > 0, negative: row.rougeDeltaPct < 0 }">
            ROUGE-L {{ formatDelta(row.rougeDeltaPct) }}
          </span>
          <span v-if="row.entityRecallDeltaPct !== undefined" :class="{ positive: row.entityRecallDeltaPct > 0, negative: row.entityRecallDeltaPct < 0 }">
            实体召回 {{ formatDelta(row.entityRecallDeltaPct) }}
          </span>
        </div>
      </article>
    </div>

    <div ref="chartHost" class="exp-compare-chart" role="img" aria-label="各实验条件 ROUGE-L、BLEU 与实体召回率分组柱状图"></div>
  </section>
</template>

<style scoped>
.exp-compare-visual {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 14px;
  background: var(--surface-soft);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
}

.exp-compare-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.exp-compare-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text);
  font-size: 13.5px;
}

.exp-compare-title svg {
  color: var(--accent);
}

.exp-compare-head small {
  color: var(--text-subtle);
  font-size: 12px;
}

.exp-delta-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.exp-delta-pill {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 7px 10px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xs);
  font-size: 12px;
}

.exp-delta-name {
  font-weight: 600;
  color: var(--text);
}

.exp-delta-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.exp-delta-metrics .positive {
  color: var(--accent-strong);
  font-weight: 650;
}

.exp-delta-metrics .negative {
  color: var(--danger);
  font-weight: 650;
}

.exp-compare-chart {
  width: 100%;
  height: 228px;
}
</style>
