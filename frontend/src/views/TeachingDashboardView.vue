<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BarChart3, CircleAlert, RefreshCw, Users } from '@lucide/vue'
import { api, apiError, type TeachingDashboard } from '../api/client'
import SectionHeading from '../components/SectionHeading.vue'
import FeedbackState from '../components/FeedbackState.vue'
import TeachingDashboardCharts from '../components/TeachingDashboardCharts.vue'

interface Course { id: number; name: string; term: string }
const courses=ref<Course[]>([]);const courseId=ref<number|undefined>();const days=ref(7);const dashboard=ref<TeachingDashboard|null>(null);const loading=ref(true);const error=ref('')
const accuracy=computed(()=>dashboard.value?.overview.attempts?Math.round(dashboard.value.overview.correct/dashboard.value.overview.attempts*100):0)
const readingRate=computed(()=>dashboard.value?.overview.totalSections?Math.round(dashboard.value.overview.completedReads/dashboard.value.overview.totalSections*100):0)
async function loadCourses(){try{courses.value=(await api.get<Course[]>('/admin/courses')).data;courseId.value=courses.value[0]?.id}catch(cause){error.value=apiError(cause,'课程列表暂时无法加载。')}}
async function load(){loading.value=true;error.value='';try{dashboard.value=(await api.get<TeachingDashboard>('/admin/teaching-dashboard',{params:{days:days.value,courseId:courseId.value}})).data}catch(cause){error.value=apiError(cause,'教学看板暂时无法加载。')}finally{loading.value=false}}
onMounted(async()=>{await loadCourses();await load()})
</script>

<template>
  <div class="page-stack teaching-dashboard-page">
    <SectionHeading eyebrow="管理员视角 · 匿名聚合" title="教学看板" description="只展示课程层面的学习趋势，不显示学生身份、私有笔记或原始评估文本。"><button class="button button-outline" aria-label="刷新教学看板" :disabled="loading" @click="load"><RefreshCw :size="15"/>刷新</button></SectionHeading>
    <div v-if="error" class="inline-notice" role="alert"><CircleAlert :size="16"/>{{ error }}<button class="text-button" @click="load">重试</button></div>
    <section class="dashboard-filters"><label><Users :size="15"/>课程<select v-model.number="courseId" @change="load"><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }} · {{ course.term }}</option></select></label><label><BarChart3 :size="15"/>范围<select v-model.number="days" data-testid="dashboard-days"><option :value="7">近 7 天</option><option :value="14">近 14 天</option><option :value="30">近 30 天</option></select></label><span v-if="dashboard" class="dashboard-period">{{ dashboard.from }} 至 {{ dashboard.to }} · {{ dashboard.courseName }}</span></section>
    <FeedbackState v-if="loading" :loading="loading" loading-title="正在汇总课程学习数据" loading-text="正在读取匿名聚合指标和薄弱知识点，请稍候。" />
    <template v-else-if="dashboard">
      <section class="teaching-kpis"><article><span>活跃学习者</span><strong>{{ dashboard.overview.activeLearners }} 人</strong><small>统计周期内有答题记录的账户</small></article><article><span>练习正确率</span><strong>{{ accuracy }}%</strong><small>{{ dashboard.overview.correct }} / {{ dashboard.overview.attempts }} 次答题</small></article><article><span>阅读完成</span><strong>{{ dashboard.overview.completedReads }} / {{ dashboard.overview.totalSections }}</strong><small>周期完成率 {{ readingRate }}%</small></article><article><span>平均掌握度</span><strong>{{ dashboard.overview.averageMastery }}%</strong><small>基于已产生掌握记录的匿名平均值</small></article><article><span>待处理错题</span><strong>{{ dashboard.overview.openWrong }}</strong><small>当前课程未掌握错题</small></article></section>
      <TeachingDashboardCharts :daily-activity="dashboard.dailyActivity" :weak-points="dashboard.weakPoints" />
      <section v-if="dashboard.dailyActivity.length || dashboard.weakPoints.length" class="teaching-columns" aria-label="学情明细">
        <section v-if="dashboard.dailyActivity.length" class="teaching-panel">
          <div class="teaching-panel-heading"><p class="eyebrow">活动明细</p><h2>每日学习活动</h2></div>
          <table aria-label="每日学习活动"><thead><tr><th scope="col">日期</th><th scope="col">活跃人数</th><th scope="col">答题</th><th scope="col">答对</th><th scope="col">阅读</th></tr></thead><tbody><tr v-for="row in dashboard.dailyActivity" :key="row.date"><td>{{ row.date }}</td><td>{{ row.activeLearners }}</td><td>{{ row.attempts }}</td><td>{{ row.correct }}</td><td>{{ row.completedReads }}</td></tr></tbody></table>
        </section>
        <section v-if="dashboard.weakPoints.length" class="teaching-panel">
          <div class="teaching-panel-heading"><p class="eyebrow">掌握明细</p><h2>薄弱知识点</h2></div>
          <div class="weak-point-list"><article v-for="point in dashboard.weakPoints" :key="point.pointId"><div><strong>{{ point.pointName }}</strong><span>{{ point.averageMastery }}%</span></div><div class="weak-progress"><i :style="{width:point.averageMastery+'%'}"></i></div><small>{{ point.learners }} 名学习者 · {{ point.attempts }} 次答题</small></article></div>
        </section>
      </section>
    </template>
    <FeedbackState v-if="!loading && !dashboard && !error" :empty="true" empty-title="暂无教学聚合数据" empty-text="当前筛选范围内还没有可展示的课程学习记录。" />
  </div>
</template>

<style scoped>
.dashboard-filters{display:flex;align-items:center;flex-wrap:wrap;gap:12px 20px;padding:10px 12px;background:var(--surface-tint);border-radius:var(--radius-md)}
.dashboard-filters label{display:flex;align-items:center;gap:8px;color:var(--text-muted);font-size:13px}
.dashboard-period{margin-left:auto;color:var(--text-muted);font-size:12px}
.teaching-kpis{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));overflow:hidden;background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md)}
.teaching-kpis article{display:grid;align-content:start;gap:5px;padding:16px;border-right:1px solid var(--border-soft)}
.teaching-kpis article:last-child{border-right:0}
.teaching-kpis span,.teaching-kpis small{font-size:12px;color:var(--text-muted)}
.teaching-kpis strong{color:var(--text);font-size:25px;line-height:1.3}
.teaching-columns{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,370px),1fr));align-items:start;gap:18px}
.teaching-panel{min-width:0;font-variant-numeric:tabular-nums}
.teaching-panel-heading{margin-bottom:10px}.teaching-panel-heading .eyebrow{margin-bottom:4px}.teaching-panel-heading h2{margin:0;font-size:16px}
th:not(:first-child),td:not(:first-child){text-align:right}
.weak-point-list{background:var(--surface);border:1px solid var(--border-default);border-radius:var(--radius-md);padding:4px 14px}
.weak-point-list article{display:grid;gap:7px;padding:12px 0}.weak-point-list article+article{border-top:1px solid var(--border-soft)}
.weak-point-list article>div:first-child{display:flex;justify-content:space-between;gap:8px}.weak-point-list small{color:var(--text-muted);font-size:12px}
.weak-progress{height:5px;overflow:hidden;background:var(--surface-tint);border-radius:var(--radius-pill)}.weak-progress i{display:block;height:100%;background:var(--accent);border-radius:inherit}
@media(max-width:850px){.teaching-kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.teaching-kpis article:nth-child(2n){border-right:0}.teaching-kpis article:nth-child(n+3){border-top:1px solid var(--border-soft)}.dashboard-period{margin-left:0}}
</style>
