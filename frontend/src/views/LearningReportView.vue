<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BarChart3, BookOpenCheck, CircleAlert, Download, RotateCcw, Target } from '@lucide/vue'
import { api, apiError, learningApi, type LearningReport } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'

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
      <button class="button button-outline" aria-label="刷新学习报告" :disabled="loading" @click="load"><RotateCcw :size="15" />刷新</button><button class="button button-dark" :disabled="!report || exporting" @click="download"><Download :size="15" />{{ exporting ? '导出中…' : '导出 CSV' }}</button>
    </SectionHeading>
    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16" />{{ error }}<button class="text-button" @click="load">重试</button></div>
    <section class="report-controls"><label><BarChart3 :size="15" />统计范围<select v-model.number="days" data-testid="report-days"><option :value="7">近 7 天</option><option :value="14">近 14 天</option><option :value="30">近 30 天</option></select></label><label><BookOpenCheck :size="15" />课程<select v-model.number="courseId" @change="load"><option :value="undefined">全部课程</option><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label><span v-if="report" class="report-period">{{ report.from }} 至 {{ report.to }} · {{ report.courseName }}</span></section>
    <div v-if="loading" class="empty-state"><span class="spinner"></span><strong>正在汇总学习记录</strong></div>
    <template v-else-if="report">
      <section class="report-kpis"><article><span>课程阅读</span><strong>{{ report.reading.completedSections }} / {{ report.reading.totalSections }}</strong><small>总完成率 {{ completionRate }}% · 本期新增 {{ report.reading.completedInPeriod }} 节</small></article><article><span>练习正确率</span><strong>{{ report.practice.accuracy }}%</strong><small>{{ report.practice.correct }} / {{ report.practice.attempts }} 次答题</small></article><article><span>复习正确率</span><strong>{{ report.reviews.accuracy }}%</strong><small>{{ report.reviews.correct }} / {{ report.reviews.reviews }} 次到期复习</small></article><article><span>计划执行</span><strong>{{ report.plans.completedMinutes }} / {{ report.plans.plannedMinutes }} 分钟</strong><small>{{ report.plans.completedTasks }} / {{ report.plans.totalTasks }} 个任务 · {{ planRate }}%</small></article></section>
      <section class="report-columns"><div class="report-panel"><div class="report-panel-heading"><div><p class="eyebrow">DAILY ACTIVITY</p><h2>每日学习活动</h2></div><Target :size="18" /></div><div class="activity-table"><div class="activity-row activity-head"><span>日期</span><span>答题</span><span>答对</span><span>阅读完成</span></div><div v-for="day in report.dailyActivity" :key="day.date" class="activity-row"><span>{{ day.date }}</span><span>{{ day.attempts }}</span><span>{{ day.correct }}</span><span>{{ day.readingCompleted }}</span></div></div></div><div class="report-panel"><div class="report-panel-heading"><div><p class="eyebrow">BY CHAPTER</p><h2>章节阅读进度</h2></div><BookOpenCheck :size="18" /></div><div class="chapter-list"><article v-for="chapter in report.chapters" :key="chapter.chapterId"><div><strong>{{ chapter.chapterName }}</strong><span>{{ chapter.completedSections }} / {{ chapter.totalSections }}</span></div><div class="chapter-progress"><i :style="{ width: (chapter.totalSections ? chapter.completedSections / chapter.totalSections * 100 : 0) + '%' }"></i></div><small>本期完成 {{ chapter.completedInPeriod }} 节</small></article></div></div></section>
      <section class="report-panel knowledge-report"><div class="report-panel-heading"><div><p class="eyebrow">BY KNOWLEDGE POINT</p><h2>知识点掌握记录</h2></div><span>掌握度来自答题统计</span></div><div class="knowledge-report-list"><article v-for="point in report.knowledge" :key="point.pointId"><div class="knowledge-name"><strong>{{ point.pointName }}</strong><span>{{ Math.round(point.mastery) }}%</span></div><div class="knowledge-progress"><i :style="{ width: point.mastery + '%' }"></i></div><small>{{ point.correct }} / {{ point.attempts }} 次答对<span v-if="point.dueReviewCount"> · 待复习 {{ point.dueReviewCount }}</span></small></article></div></section>
    </template>
  </div>
</template>

<style scoped>
.report-controls,.report-kpis,.report-panel { background:#fff; border:1px solid var(--line); border-radius:8px; }.report-controls{display:flex;align-items:center;flex-wrap:wrap;gap:15px;padding:15px 18px}.report-controls label{display:flex;align-items:center;gap:8px;color:#56635a;font-size:12px}.report-controls select{min-height:34px;padding:0 28px 0 9px;border:1px solid var(--line);border-radius:5px;background:#fff;color:var(--ink)}.report-period{margin-left:auto;color:var(--muted);font-size:11px}.report-kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));overflow:hidden}.report-kpis article{display:grid;gap:7px;padding:18px;border-right:1px solid var(--line)}.report-kpis article:last-child{border-right:0}.report-kpis span{color:var(--muted);font-size:11px}.report-kpis strong{color:var(--ink);font-size:22px}.report-kpis small{color:#7b877f;font-size:10px;line-height:1.5}.report-columns{display:grid;grid-template-columns:1fr 1fr;gap:16px}.report-panel{padding:20px}.report-panel-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:17px;color:var(--green)}.report-panel-heading .eyebrow{margin:0 0 5px}.report-panel-heading h2{margin:0;color:var(--ink);font-size:15px}.report-panel-heading span{color:var(--muted);font-size:10px}.activity-table{display:grid}.activity-row{display:grid;grid-template-columns:1.4fr repeat(3,1fr);gap:8px;padding:10px 0;border-top:1px solid #edf0ed;color:#68766d;font-size:11px}.activity-head{border-top:0;color:var(--muted);font-size:10px}.activity-row span:not(:first-child){text-align:right}.chapter-list,.knowledge-report-list{display:grid;gap:15px}.chapter-list article{display:grid;gap:7px}.chapter-list article>div:first-child,.knowledge-name{display:flex;justify-content:space-between;gap:10px;color:#4f5f55;font-size:11px}.chapter-list article span,.chapter-list small,.knowledge-report small{color:var(--muted);font-size:10px}.chapter-progress,.knowledge-progress{height:5px;overflow:hidden;background:#edf0ed;border-radius:3px}.chapter-progress i,.knowledge-progress i{display:block;height:100%;background:#4b986b;border-radius:inherit}.knowledge-report{padding-bottom:22px}.knowledge-report-list{grid-template-columns:repeat(2,minmax(0,1fr));gap:17px 25px}.knowledge-report-list article{display:grid;gap:7px}.knowledge-progress i{background:#c79a42}@media(max-width:900px){.report-kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.report-kpis article:nth-child(2){border-right:0}.report-kpis article:nth-child(n+3){border-top:1px solid var(--line)}}@media(max-width:700px){.report-columns,.knowledge-report-list{grid-template-columns:1fr}.report-period{width:100%;margin-left:0}.report-panel{padding:16px}}
</style>
