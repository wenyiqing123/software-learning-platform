<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type * as echarts from 'echarts'
import { Download } from '@lucide/vue'
import { useTheme } from '../composables/useTheme'
import { exportChartAsPng, safeDisposeChart, safeInitChart } from '../domain/chartExport'
import { buildActivityChartOption, buildWeakPointChartOption, type TeachingActivityPoint, type TeachingWeakPoint } from '../domain/teachingDashboardCharts'

const props = defineProps<{ dailyActivity: TeachingActivityPoint[]; weakPoints: TeachingWeakPoint[] }>()
const { isDark } = useTheme()
const activityHost = ref<HTMLElement | null>(null)
const weakHost = ref<HTMLElement | null>(null)
let activityChart: echarts.ECharts | null = null
let weakChart: echarts.ECharts | null = null

function exportActivity() {
  exportChartAsPng(activityChart, 'slp-teaching-activity', isDark.value)
}

function exportWeakPoints() {
  exportChartAsPng(weakChart, 'slp-teaching-weakpoints', isDark.value)
}

function renderCharts() {
  safeDisposeChart(activityChart)
  safeDisposeChart(weakChart)
  activityChart = safeInitChart(activityHost.value)
  weakChart = safeInitChart(weakHost.value)
  try {
    activityChart?.setOption(buildActivityChartOption(props.dailyActivity, isDark.value))
    weakChart?.setOption(buildWeakPointChartOption(props.weakPoints, isDark.value))
  } catch {
    // 忽略无 Canvas 上下文的受限环境
  }
}
function resizeCharts() {
  try {
    activityChart?.resize()
    weakChart?.resize()
  } catch {
    // 忽略隐藏容器 resize
  }
}
watch(() => [props.dailyActivity, props.weakPoints, isDark.value], () => void nextTick(renderCharts), { deep: true })
onMounted(() => { void nextTick(renderCharts); window.addEventListener('resize', resizeCharts) })
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  safeDisposeChart(activityChart)
  safeDisposeChart(weakChart)
  activityChart = null
  weakChart = null
})
</script>

<template>
  <section class="teaching-chart-grid" aria-label="教学数据可视化">
    <article class="teaching-chart-card">
      <div class="teaching-panel-heading">
        <div><p class="eyebrow">LEARNING ACTIVITY</p><h2>学习活动趋势</h2></div>
        <div class="chart-head-actions no-print">
          <small>答题、答对与阅读完成</small>
          <button v-if="dailyActivity.length" type="button" class="chart-export-btn" data-testid="export-teaching-activity-png" aria-label="导出教学活动趋势图 PNG" @click="exportActivity">
            <Download :size="13" aria-hidden="true" />
            <span>导出 PNG</span>
          </button>
        </div>
      </div>
      <div v-if="dailyActivity.length" ref="activityHost" class="teaching-chart" role="img" aria-label="每日学习活动趋势图"></div>
      <div v-else class="teaching-chart-empty">当前范围内暂无学习活动数据。</div>
    </article>
    <article class="teaching-chart-card">
      <div class="teaching-panel-heading">
        <div><p class="eyebrow">MASTERY DISTRIBUTION</p><h2>薄弱知识点掌握度</h2></div>
        <div class="chart-head-actions no-print">
          <small>按平均掌握度升序</small>
          <button v-if="weakPoints.length" type="button" class="chart-export-btn" data-testid="export-teaching-weakpoints-png" aria-label="导出薄弱知识点排行图 PNG" @click="exportWeakPoints">
            <Download :size="13" aria-hidden="true" />
            <span>导出 PNG</span>
          </button>
        </div>
      </div>
      <div v-if="weakPoints.length" ref="weakHost" class="teaching-chart" role="img" aria-label="薄弱知识点掌握度排行图"></div>
      <div v-else class="teaching-chart-empty">当前范围内暂无薄弱知识点数据。</div>
    </article>
  </section>
</template>

<style scoped>
.teaching-chart-grid{display:grid;grid-template-columns:minmax(0,1.25fr) minmax(0,.95fr);gap:16px;align-items:start}
.teaching-chart-card{min-width:0;padding:16px;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);box-shadow:var(--shadow-xs)}
.teaching-panel-heading{display:flex;align-items:end;justify-content:space-between;gap:6px 12px;flex-wrap:wrap;margin-bottom:12px}
.teaching-panel-heading h2,.teaching-panel-heading p{margin:0}.teaching-panel-heading h2{font-size:16px}.teaching-panel-heading small{color:var(--text-muted);font-size:12px}
.teaching-chart{width:100%;height:252px}
.teaching-chart-empty{display:grid;place-items:center;min-height:80px;padding:12px;text-align:center;color:var(--text-muted);font-size:13px;background:var(--surface-soft);border-radius:var(--radius-sm)}
@media(max-width:850px){.teaching-chart-grid{grid-template-columns:minmax(0,1fr)}}
</style>
