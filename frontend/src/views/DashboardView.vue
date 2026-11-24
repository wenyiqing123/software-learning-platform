<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowUpRight, BookOpen, BrainCircuit, CalendarCheck, CircleCheck, Clock3, Flame, Network, RotateCcw, Sparkles } from '@lucide/vue'
import { useLearningMetrics, weeklyActivity } from '../composables/useLearningMetrics'
import MetricTile from '../components/MetricTile.vue'
import SectionHeading from '../components/SectionHeading.vue'
import { courseHref, readLastLearningContext, practiceHref } from '../domain/learningContext'

const emit = defineEmits<{ navigate: [view: string, entity?: string] }>()
const { points, overview, loading, error, reload } = useLearningMetrics()
const now = ref(new Date())
let clock: ReturnType<typeof setInterval> | undefined
onMounted(() => { clock = setInterval(() => { now.value = new Date() }, 60_000) })
onBeforeUnmount(() => clearInterval(clock))
const completion = computed(() => overview.value && overview.value.completed > 0 && Number.isFinite(overview.value.mastery)
  ? Math.max(0, Math.min(100, overview.value.mastery)) : null)
const milestones = computed(() => points.value.slice(0, 3).map((point, index) => ({
  title: point.name, entity: point.graphEntity, text: point.graphEntity,
  progress: typeof point.mastery === 'number' && Number.isFinite(point.mastery) ? Math.max(0, Math.min(100, point.mastery)) : null,
  color: ['mint', 'coral', 'sky'][index], icon: [BrainCircuit, Network, BookOpen][index],
})))
const days = computed(() => weeklyActivity(overview.value?.activity || [], now.value))
const weeklyTotal = computed(() => days.value.reduce((sum, day) => sum + day.count, 0))
const maxActivity = computed(() => Math.max(...days.value.map(day => day.count), 1))
const accuracyDetail = computed(() => !overview.value ? '答题记录暂不可用'
  : overview.value.completed === 0 ? '尚无答题记录' : '正确率 ' + overview.value.accuracy + '%')
const lastContext = ref(readLastLearningContext())
const nextPoint = computed(() => points.value.find(point => (point.mastery ?? 0) < 60) || points.value[0])
const lastLearningHref = computed(() => lastContext.value ? courseHref(lastContext.value) : { path: '/courses' })
const nextPracticeHref = computed(() => nextPoint.value ? practiceHref({ knowledgePointId: nextPoint.value.id, courseId: nextPoint.value.courseId, entity: nextPoint.value.graphEntity }) : { path: '/practice' })
function refreshContext() { lastContext.value = readLastLearningContext() }
onMounted(() => window.addEventListener('learning:changed', refreshContext))
onBeforeUnmount(() => window.removeEventListener('learning:changed', refreshContext))
</script>

<template>
  <div class="page-stack">
    <SectionHeading eyebrow="课程学习空间" title="今天，从一个知识点开始" description="把知识点串起来，让每次练习都更有方向。">
      <button class="button button-outline" @click="emit('navigate', 'tutor')"><Sparkles :size="16" />问问 AI 助教</button>
    </SectionHeading>
    <section class="metric-grid" aria-label="学习统计" :aria-busy="loading">
      <MetricTile label="累计答题" :value="overview?.completed ?? '—'" detail="服务端保存的提交次数" tone="green" />
      <MetricTile label="待复习错题" :value="overview?.wrongCount ?? '—'" detail="根据个人错题记录统计" tone="orange" />
      <MetricTile label="近七天答题" :value="overview ? weeklyTotal + ' 题' : '—'" :detail="accuracyDetail" tone="blue" />
      <MetricTile label="记录掌握度" :value="completion === null ? '—' : completion + '%'" detail="练习记录统计，不代表课程完成度" tone="green" />
    </section>
    <div v-if="error" class="inline-notice" role="alert"><span class="notice-dot"></span>{{ error }}<button class="text-button" :disabled="loading" @click="reload()">重试</button></div>
    <section class="dashboard-hero" aria-label="课程学习概览">
      <div class="section-title-row"><div><p class="eyebrow">课程学习路径</p><h2>循序渐进，把软件工程学扎实。</h2></div><button class="text-button" @click="emit('navigate', 'knowledge')">探索知识 <ArrowUpRight :size="16" /></button></div>
      <section class="learning-next-strip" aria-label="继续学习入口">
        <RouterLink class="learning-next-card next-primary" :to="lastLearningHref"><span class="next-icon"><BookOpen :size="17" /></span><span><small>继续上次学习</small><strong>{{ lastContext ? '回到上次阅读的小节' : '选择课程开始阅读' }}</strong></span><ArrowUpRight :size="16" /></RouterLink>
        <RouterLink class="learning-next-card" to="/review"><span class="next-icon next-warm"><RotateCcw :size="17" /></span><span><small>今日待复习</small><strong>{{ overview?.wrongCount ? '有 ' + overview.wrongCount + ' 道错题待处理' : '查看到期复习队列' }}</strong></span><ArrowUpRight :size="16" /></RouterLink>
        <RouterLink class="learning-next-card" :to="nextPracticeHref"><span class="next-icon next-blue"><CalendarCheck :size="17" /></span><span><small>推荐下一步</small><strong>{{ nextPoint ? '练习“' + nextPoint.name + '”' : '打开专项练习' }}</strong></span><ArrowUpRight :size="16" /></RouterLink>
      </section>
    </section>
    <div class="dashboard-columns">
      <section class="content-section learning-section"><div class="section-title-row"><div><p class="eyebrow">课程知识路径</p><h2>知识点掌握记录</h2></div><button class="text-button" @click="emit('navigate', 'knowledge')">打开知识图谱 <ArrowUpRight :size="15" /></button></div>
        <div v-if="milestones.length" class="path-list"><button v-for="(item, index) in milestones" :key="item.entity" class="path-row" @click="emit('navigate', 'knowledge', item.entity)"><span class="path-index">0{{ index + 1 }}</span><span class="path-icon" :class="item.color"><component :is="item.icon" :size="18" /></span><span class="path-main"><strong>{{ item.title }}</strong><small>{{ item.text }}</small><span v-if="item.progress !== null" class="mini-progress"><i :style="{ width: item.progress + '%' }"></i></span></span><span class="path-percent">{{ item.progress === null ? '暂无记录' : item.progress + '%' }}</span></button></div>
        <div v-else class="empty-state"><strong>{{ loading ? '正在加载知识点' : error ? '知识点暂不可用' : '暂无课程知识点' }}</strong></div>
      </section>
      <section class="content-section activity-section"><div class="section-title-row"><div><p class="eyebrow">近七天 · 北京时间</p><h2>每日答题记录</h2></div><span class="week-total"><Clock3 :size="15" /> {{ days[0].key.slice(5) }} – {{ days[6].key.slice(5) }}</span></div>
        <div v-if="overview" class="weekly-chart" role="img" aria-label="按 Asia/Shanghai 日期统计的近七天每日答题量柱状图"><div v-for="(day, index) in days" :key="day.key" class="bar-column" :title="day.key + '：' + day.count + ' 题'"><span class="bar-value">{{ day.count }}题</span><div class="bar-track"><i :class="{ today: index === 6 }" :style="{ height: day.count / maxActivity * 100 + '%' }"></i></div><span class="bar-day">{{ day.label }}</span></div></div>
        <div v-else class="empty-state"><strong>{{ loading ? '正在读取答题记录' : '答题记录暂不可用' }}</strong></div>
        <div class="activity-footer"><span><Flame :size="15" />{{ overview ? '近七天共提交 ' + weeklyTotal + ' 题' : '等待服务端答题记录' }}</span><span class="week-total">按答题次数统计</span></div>
      </section>
    </div>
    <section class="recommend-strip"><div class="recommend-symbol"><Sparkles :size="20" /></div><div><p class="eyebrow">学习建议</p><h2>从知识图谱中选择概念，或先完成一组练习</h2><span>答题与复习记录将用于生成个性化学习建议</span></div><button class="button button-dark" @click="emit('navigate', 'recommendations')">查看推荐 <ArrowUpRight :size="16" /></button><CircleCheck class="recommend-check" :size="20" /></section>
  </div>
</template>
