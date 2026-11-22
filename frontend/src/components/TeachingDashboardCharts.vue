<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { buildActivityChartOption, buildWeakPointChartOption, type TeachingActivityPoint, type TeachingWeakPoint } from '../domain/teachingDashboardCharts'

const props = defineProps<{ dailyActivity: TeachingActivityPoint[]; weakPoints: TeachingWeakPoint[] }>()
const activityHost = ref<HTMLElement | null>(null)
const weakHost = ref<HTMLElement | null>(null)
let activityChart: echarts.ECharts | null = null
let weakChart: echarts.ECharts | null = null

function renderCharts() {
  activityChart?.dispose()
  weakChart?.dispose()
  activityChart = activityHost.value ? echarts.init(activityHost.value) : null
  weakChart = weakHost.value ? echarts.init(weakHost.value) : null
  activityChart?.setOption(buildActivityChartOption(props.dailyActivity))
  weakChart?.setOption(buildWeakPointChartOption(props.weakPoints))
}
function resizeCharts() { activityChart?.resize(); weakChart?.resize() }
watch(() => [props.dailyActivity, props.weakPoints], () => nextTick(renderCharts), { deep: true })
onMounted(() => { nextTick(renderCharts); window.addEventListener('resize', resizeCharts) })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeCharts); activityChart?.dispose(); weakChart?.dispose() })
</script>

<template>
  <section class="teaching-chart-grid" aria-label="教学数据可视化">
    <article class="teaching-panel teaching-chart-card">
      <div class="teaching-panel-heading"><div><p class="eyebrow">LEARNING ACTIVITY</p><h2>学习活动趋势</h2></div><small>答题、答对与阅读完成</small></div>
      <div v-if="dailyActivity.length" ref="activityHost" class="teaching-chart teaching-chart-activity" role="img" aria-label="每日学习活动趋势图"></div>
      <div v-else class="teaching-chart-empty">当前范围内暂无学习活动数据。</div>
    </article>
    <article class="teaching-panel teaching-chart-card">
      <div class="teaching-panel-heading"><div><p class="eyebrow">MASTERY DISTRIBUTION</p><h2>薄弱知识点掌握度</h2></div><small>按平均掌握度升序</small></div>
      <div v-if="weakPoints.length" ref="weakHost" class="teaching-chart teaching-chart-weak" role="img" aria-label="薄弱知识点掌握度排行图"></div>
      <div v-else class="teaching-chart-empty">当前范围内暂无薄弱知识点数据。</div>
    </article>
  </section>
</template>

<style scoped>
.teaching-chart-grid{display:grid;grid-template-columns:minmax(0,1.25fr) minmax(360px,.75fr);gap:16px}.teaching-chart-card{min-width:0}.teaching-panel-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.teaching-panel-heading small{color:var(--text-subtle);font-size:10px}.teaching-chart{width:100%;height:280px}.teaching-chart-empty{display:grid;place-items:center;height:280px;color:var(--text-muted);font-size:11px;background:var(--surface-soft);border:1px dashed var(--border-default);border-radius:var(--radius-sm)}@media(max-width:900px){.teaching-chart-grid{grid-template-columns:1fr}}@media(max-width:600px){.teaching-chart{height:240px}.teaching-chart-empty{height:240px}.teaching-panel-heading{display:block}.teaching-panel-heading small{display:block;margin-top:5px}}
</style>
