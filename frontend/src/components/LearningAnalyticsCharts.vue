<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type * as echarts from 'echarts'
import { Download } from '@lucide/vue'
import { useTheme } from '../composables/useTheme'
import { exportChartAsPng, safeDisposeChart, safeInitChart } from '../domain/chartExport'
import {
  buildHeatmapActivityOption,
  buildMasteryRadarOption,
  type HeatmapActivityPoint,
  type MasteryRadarPoint,
} from '../domain/learningAnalyticsCharts'

const props = withDefaults(
  defineProps<{
    heatmapDays?: HeatmapActivityPoint[]
    radarPoints?: MasteryRadarPoint[]
    showHeatmap?: boolean
    showRadar?: boolean
    heatmapTitle?: string
    radarTitle?: string
  }>(),
  {
    heatmapDays: () => [],
    radarPoints: () => [],
    showHeatmap: true,
    showRadar: true,
    heatmapTitle: '学习活动热力分布',
    radarTitle: '知识点掌握多维雷达',
  }
)

const { isDark } = useTheme()
const heatmapHost = ref<HTMLElement | null>(null)
const radarHost = ref<HTMLElement | null>(null)
let heatmapChart: echarts.ECharts | null = null
let radarChart: echarts.ECharts | null = null

function exportHeatmap() {
  exportChartAsPng(heatmapChart, 'slp-learning-heatmap', isDark.value)
}

function exportRadar() {
  exportChartAsPng(radarChart, 'slp-mastery-radar', isDark.value)
}

function renderCharts() {
  safeDisposeChart(heatmapChart)
  safeDisposeChart(radarChart)
  heatmapChart = null
  radarChart = null

  if (props.showHeatmap && props.heatmapDays.length && heatmapHost.value) {
    heatmapChart = safeInitChart(heatmapHost.value)
    try {
      heatmapChart?.setOption(buildHeatmapActivityOption(props.heatmapDays, isDark.value))
    } catch {
      // 忽略受限渲染上下文
    }
  }
  if (props.showRadar && props.radarPoints.length >= 3 && radarHost.value) {
    radarChart = safeInitChart(radarHost.value)
    try {
      radarChart?.setOption(buildMasteryRadarOption(props.radarPoints, isDark.value))
    } catch {
      // 忽略受限渲染上下文
    }
  }
}

function resizeCharts() {
  try {
    heatmapChart?.resize()
    radarChart?.resize()
  } catch {
    // 忽略不可见容器 resize 异常
  }
}

watch(
  () => [props.heatmapDays, props.radarPoints, props.showHeatmap, props.showRadar, isDark.value],
  () => {
    void nextTick(renderCharts)
  },
  { deep: true }
)

onMounted(() => {
  void nextTick(renderCharts)
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  safeDisposeChart(heatmapChart)
  safeDisposeChart(radarChart)
  heatmapChart = null
  radarChart = null
})
</script>

<template>
  <section
    v-if="(showHeatmap && heatmapDays.length) || (showRadar && radarPoints.length >= 3)"
    class="learning-analytics-grid"
    :class="{ 'is-single': !(showHeatmap && heatmapDays.length && showRadar && radarPoints.length >= 3) }"
    aria-label="学习趋势与能力分布图表"
    data-testid="learning-analytics-charts"
  >
    <article v-if="showHeatmap && heatmapDays.length" class="analytics-chart-card">
      <header class="analytics-chart-head">
        <div>
          <p class="eyebrow">ACTIVITY HEATMAP</p>
          <h2>{{ heatmapTitle }}</h2>
        </div>
        <div class="chart-head-actions no-print">
          <small>按日期与星期矩阵呈现活跃度</small>
          <button
            type="button"
            class="chart-export-btn"
            data-testid="export-heatmap-png"
            aria-label="导出学习热力图 PNG"
            @click="exportHeatmap"
          >
            <Download :size="13" aria-hidden="true" />
            <span>导出 PNG</span>
          </button>
        </div>
      </header>
      <div ref="heatmapHost" class="analytics-chart-canvas" role="img" :aria-label="heatmapTitle" data-testid="analytics-heatmap-chart"></div>
    </article>

    <article v-if="showRadar && radarPoints.length >= 3" class="analytics-chart-card">
      <header class="analytics-chart-head">
        <div>
          <p class="eyebrow">MASTERY RADAR</p>
          <h2>{{ radarTitle }}</h2>
        </div>
        <div class="chart-head-actions no-print">
          <small>最多展示 8 个核心知识点当前保持度</small>
          <button
            type="button"
            class="chart-export-btn"
            data-testid="export-radar-png"
            aria-label="导出掌握度雷达图 PNG"
            @click="exportRadar"
          >
            <Download :size="13" aria-hidden="true" />
            <span>导出 PNG</span>
          </button>
        </div>
      </header>
      <div ref="radarHost" class="analytics-chart-canvas" role="img" :aria-label="radarTitle" data-testid="analytics-radar-chart"></div>
    </article>
  </section>
</template>

<style scoped>
.learning-analytics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.learning-analytics-grid.is-single {
  grid-template-columns: minmax(0, 1fr);
}

.analytics-chart-card {
  min-width: 0;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.analytics-chart-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-bottom: 10px;
}

.analytics-chart-head h2,
.analytics-chart-head p {
  margin: 0;
}

.analytics-chart-head h2 {
  font-size: 16px;
  color: var(--text);
}

.analytics-chart-head small {
  color: var(--text-muted);
  font-size: 12px;
}

.analytics-chart-canvas {
  width: 100%;
  height: 248px;
}

@media (max-width: 850px) {
  .learning-analytics-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
