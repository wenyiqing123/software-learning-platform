<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BarChart3, BookOpenCheck, CircleAlert, Download, RotateCcw, Target } from '@lucide/vue'
import { api, apiError, learningApi, type LearningReport } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
import LearningAnalyticsCharts from '../components/LearningAnalyticsCharts.vue'

interface Course { id: number; name: string; term: string }
const courses = ref<Course[]>([])
const courseId = ref<number | undefined>()
const days = ref(7)
const report = ref<LearningReport | null>(null)
const loading = ref(true)
const exporting = ref(false)
const error = ref('')
const completionRate = computed(() => report.value?.reading.totalSections ? Math.round(report.value.reading.completedSections / report.value.reading.totalSections * 100) : 0)
const planRate = computed(() => report.value?.plans.totalTasks ? Math.round(report.value.plans.completedTasks / report.value.plans.totalTasks * 100) : 0)
const reportHeatmapDays = computed(() =>
  (report.value?.dailyActivity ?? []).map((day) => ({
    date: day.date,
    count: (day.attempts || 0) + (day.readingCompleted || 0),
    correct: day.correct,
    readingCompleted: day.readingCompleted,
  })),
)
const reportRadarPoints = computed(() =>
  (report.value?.knowledge ?? []).map((kp) => ({
    name: kp.pointName,
    value: kp.mastery,
  })),
)

async function loadCourses() { try { courses.value = (await api.get<Course[]>('/courses')).data } catch { courses.value = [] } }
async function load() { loading.value = true; error.value = ''; try { report.value = await learningApi.learningReport(days.value, courseId.value) } catch (cause) { error.value = apiError(cause, '阶段性学习报告暂时无法加载。') } finally { loading.value = false } }
async function download() {
  exporting.value = true
  try {
    const blob = await learningApi.exportLearningReport(days.value, courseId.value)
    const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = `learning-report-${report.value?.from || 'export'}.csv`; link.click(); URL.revokeObjectURL(url)
  } catch (cause) { error.value = apiError(cause, '报告导出失败，请稍后重试。') } finally { exporting.value = false }
}
onMounted(async () => { await loadCourses(); await load() })
</script>

<template>
  <div class="page-stack report-page">
    <SectionHeading eyebrow="学习过程记录" title="阶段性学习报告" description="按时间范围汇总可追溯的阅读、练习、复习和计划执行数据，不用单一分数给学习者贴标签。">
      <div class="report-actions"><button class="button button-outline" aria-label="刷新学习报告" :disabled="loading" @click="load"><RotateCcw :size="15" />刷新</button><button class="button button-dark" :disabled="!report || exporting" @click="download"><Download :size="15" />{{ exporting ? '导出中…' : '导出 CSV' }}</button></div>
    </SectionHeading>
    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16" />{{ error }}<button class="text-button" @click="load">重试</button></div>
    <section class="report-controls"><label><BarChart3 :size="15" />统计范围<select v-model.number="days" data-testid="report-days" @change="load"><option :value="7">近 7 天</option><option :value="14">近 14 天</option><option :value="30">近 30 天</option></select></label><label><BookOpenCheck :size="15" />课程<select v-model.number="courseId" @change="load"><option :value="undefined">全部课程</option><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label><span v-if="report" class="report-period">{{ report.from }} 至 {{ report.to }} · {{ report.courseName }}</span></section>
    <FeedbackState v-if="loading" :loading="loading" loading-title="正在汇总学习记录" loading-text="正在读取阅读、练习、复习和计划数据，请稍候。" />
    <template v-else-if="report">
      <section class="report-kpis"><article><span>课程阅读</span><strong>{{ report.reading.completedSections }} / {{ report.reading.totalSections }}</strong><small>总完成率 {{ completionRate }}% · 本期新增 {{ report.reading.completedInPeriod }} 节</small></article><article><span>练习正确率</span><strong>{{ report.practice.accuracy }}%</strong><small>{{ report.practice.correct }} / {{ report.practice.attempts }} 次答题</small></article><article><span>复习正确率</span><strong>{{ report.reviews.accuracy }}%</strong><small>{{ report.reviews.correct }} / {{ report.reviews.reviews }} 次到期复习</small></article><article><span>计划执行</span><strong>{{ report.plans.completedMinutes }} / {{ report.plans.plannedMinutes }} 分钟</strong><small>{{ report.plans.completedTasks }} / {{ report.plans.totalTasks }} 个任务 · {{ planRate }}%</small></article></section>
      <LearningAnalyticsCharts :heatmap-days="reportHeatmapDays" :radar-points="reportRadarPoints" />
      <section class="report-columns">
        <div class="report-panel">
          <div class="report-panel-heading"><h2 id="report-activity-title">每日学习活动</h2><Target :size="18" /></div>
          <div v-if="report.dailyActivity.length" class="report-table-wrap">
            <table class="report-activity" aria-labelledby="report-activity-title">
              <thead><tr><th scope="col">日期</th><th scope="col">答题</th><th scope="col">答对</th><th scope="col">读完</th></tr></thead>
              <tbody><tr v-for="day in report.dailyActivity" :key="day.date"><td>{{ day.date }}</td><td>{{ day.attempts }}</td><td>{{ day.correct }}</td><td>{{ day.readingCompleted }}</td></tr></tbody>
            </table>
          </div>
          <p v-else class="report-empty">当前范围内暂无学习活动。</p>
        </div>
        <div class="report-panel">
          <div class="report-panel-heading"><h2>章节阅读进度</h2><BookOpenCheck :size="18" /></div>
          <div v-if="report.chapters.length" class="chapter-list"><article v-for="chapter in report.chapters" :key="chapter.chapterId"><div><strong>{{ chapter.chapterName }}</strong><span>{{ chapter.completedSections }} / {{ chapter.totalSections }}</span></div><div class="chapter-progress"><i :style="{ width: (chapter.totalSections ? chapter.completedSections / chapter.totalSections * 100 : 0) + '%' }"></i></div><small>本期完成 {{ chapter.completedInPeriod }} 节</small></article></div>
          <p v-else class="report-empty">当前范围内暂无章节进度。</p>
        </div>
      </section>
      <section class="report-panel knowledge-report"><div class="report-panel-heading"><div><p class="eyebrow">BY KNOWLEDGE POINT</p><h2>知识点掌握记录</h2></div><span>掌握度来自答题统计</span></div><div class="knowledge-report-list"><article v-for="point in report.knowledge" :key="point.pointId"><div class="knowledge-name"><strong>{{ point.pointName }}</strong><span>{{ Math.round(point.mastery) }}%</span></div><div class="knowledge-progress"><i :style="{ width: point.mastery + '%' }"></i></div><small>{{ point.correct }} / {{ point.attempts }} 次答对<span v-if="point.dueReviewCount"> · 待复习 {{ point.dueReviewCount }}</span></small></article></div></section>
    </template>
    <FeedbackState v-if="!loading && !report && !error" :empty="true" empty-title="暂无学习报告" empty-text="完成一次阅读或练习后，这里会显示阶段性记录。" />
  </div>
</template>

<style scoped>
.report-page{font-variant-numeric:tabular-nums}
.report-actions,.report-controls,.report-controls label,.report-panel-heading{display:flex;align-items:center;flex-wrap:wrap;gap:8px 12px}
.report-controls{padding:12px 16px;background:var(--surface-tint);border-radius:var(--radius-md);color:var(--text-muted);font-size:13px}
.report-controls select{min-height:var(--control-height-small)}
.report-period{margin-left:auto;font-size:12px}
.report-kpis,.report-panel{min-width:0;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md)}
.report-kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr))}
.report-kpis article{display:grid;align-content:start;gap:4px;padding:16px;border-inline-end:1px solid var(--border-soft)}
.report-kpis article:last-child{border-inline-end:0}
.report-kpis span,.report-empty,.report-panel-heading span,.chapter-list small,.knowledge-report small{color:var(--text-muted);font-size:13px}
.report-kpis strong{font-size:24px;line-height:1.35;overflow-wrap:anywhere}
.report-kpis small{color:var(--text-muted);font-size:12px}
.report-columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.report-panel{padding:18px}
.report-panel-heading{justify-content:space-between;margin-bottom:12px;color:var(--accent)}
.report-panel-heading h2{margin:0;color:var(--text);font-size:16px}
.report-table-wrap{overflow-x:auto}
.report-activity{width:100%;min-width:320px;border-collapse:collapse}
.report-activity :is(th,td):not(:first-child){text-align:right}
.chapter-list,.knowledge-report-list{display:grid;gap:12px 20px}
.chapter-list article,.knowledge-report-list article{display:grid;gap:6px;min-width:0}
.chapter-list article>div:first-child,.knowledge-name{display:flex;justify-content:space-between;gap:8px;color:var(--text);font-size:14px;overflow-wrap:anywhere}
.chapter-progress,.knowledge-progress{height:5px;overflow:hidden;background:var(--surface-tint);border-radius:var(--radius-pill)}
.chapter-progress i,.knowledge-progress i{display:block;height:100%;background:var(--accent);border-radius:inherit}
.knowledge-report-list{grid-template-columns:repeat(2,minmax(0,1fr))}
@media(max-width:900px){.report-kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.report-kpis article:nth-child(2){border-inline-end:0}.report-kpis article:nth-child(n+3){border-top:1px solid var(--border-soft)}}
@media(max-width:700px){.report-columns,.knowledge-report-list{grid-template-columns:1fr}.report-period{width:100%;margin-left:0}.report-panel{padding:14px}.report-activity :is(th,td){padding-inline:7px}}
</style>
