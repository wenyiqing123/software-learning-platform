<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import ElConfigProvider from 'element-plus/es/components/config-provider/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { BarChart3, BrainCircuit, CalendarCheck, ChevronDown, CircleHelp, Compass, GraduationCap, LayoutDashboard, Network, Settings2, Target, TriangleAlert, BookOpenCheck, RotateCcw, FlaskConical } from '@lucide/vue'
import { learningApi } from './api/client'
import { resetLearningMetrics, useLearningMetrics } from './composables/useLearningMetrics'
import { resetSession, useSession } from './state/session'

const router = useRouter()
const route = useRoute()
const session = useSession()
const loggingOut = ref(false)
const { overview, loading: metricsLoading, error: metricsError, reload } = useLearningMetrics()
const userName = computed(() => session.user.value?.displayName || session.user.value?.username || (session.loading.value ? '正在确认账户' : '账户未确认'))
const avatar = computed(() => Array.from(session.user.value?.displayName || session.user.value?.username || '')[0] || '·')
const isAdmin = computed(() => session.user.value?.role === 'ADMIN')
const activePath = computed(() => route.path)
const titles: Record<string, string> = { '/': '学习概览', '/courses': '课程列表', '/knowledge': '知识图谱', '/mastery-map': '个性化掌握图谱', '/tutor': 'AI 学习助教', '/practice': '课程练习', '/adaptive-practice': '自适应专项练习', '/wrong-book': '错题复习', '/review': '间隔复习', '/study-plan': '每日计划', '/micro-lessons': '证据微课', '/evaluation': '学习评估', '/learning-report': '阶段性学习报告', '/recommendations': '个性化推荐', '/experiments': '问答实验', '/experiment-tasks': '实验任务', '/admin': '管理后台', '/admin/teaching-dashboard': '教学看板', '/admin/question-bank': '题库版本', '/admin/micro-lessons': '微课草稿', '/admin/experiment-tasks': '实验任务管理', '/admin/diagnostics': '错因关联配置', '/admin/evaluations': '评估汇总' }
const pageTitle = computed(() => titles[route.path] || '学习空间')
const wrongCount = computed(() => overview.value?.wrongCount ?? '—')
const navGroups = [
  { title: '学习空间', items: [{ path: '/', label: '学习概览', icon: LayoutDashboard }, { path: '/knowledge', label: '知识图谱', icon: Network }, { path: '/mastery-map', label: '掌握图谱', icon: Target }, { path: '/micro-lessons', label: '证据微课', icon: BookOpenCheck }, { path: '/tutor', label: 'AI 学习助教', icon: BrainCircuit }] },
  { title: '练习与巩固', items: [{ path: '/practice', label: '课程练习', icon: BookOpenCheck }, { path: '/adaptive-practice', label: '专项练习', icon: Target }, { path: '/wrong-book', label: '错题整理', icon: TriangleAlert }, { path: '/review', label: '到期复习', icon: RotateCcw }, { path: '/study-plan', label: '每日计划', icon: CalendarCheck }, { path: '/evaluation', label: '学习评估', icon: FlaskConical }, { path: '/learning-report', label: '学习报告', icon: BarChart3 }, { path: '/recommendations', label: '为我推荐', icon: Compass }] },
]
function navigate(view: string, entity?: string) {
  const paths: Record<string, string> = { dashboard: '/', courses: '/courses', knowledge: '/knowledge', tutor: '/tutor', practice: '/practice', 'adaptive-practice': '/adaptive-practice', 'wrong-book': '/wrong-book', review: '/review', 'study-plan': '/study-plan', evaluation: '/evaluation', recommendations: '/recommendations', experiments: '/experiments' }
  void router.push({ path: paths[view] || '/', query: entity ? { entity } : undefined })
}
function refreshShell() {
  if (route.path === '/login') { resetSession(); resetLearningMetrics(); return }
  void session.refresh()
  void reload()
}
function onStorage(event: StorageEvent) {
  if (event.key === null || event.key === 'slp-access-token') refreshShell()
}
async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  const refreshToken = localStorage.getItem('slp-refresh-token')
  try {
    if (refreshToken) await learningApi.logout(refreshToken)
  } catch { /* Clear local credentials even when server sign-out is unavailable. */ }
  finally {
    localStorage.removeItem('slp-access-token')
    localStorage.removeItem('slp-refresh-token')
    localStorage.removeItem('slp-user-name')
    resetSession()
    resetLearningMetrics()
    loggingOut.value = false
    void router.replace('/login')
  }
}
watch(() => route.fullPath, refreshShell, { immediate: true })
watch(() => route.query.entity, (entity) => {
  if (typeof entity === 'string' && route.path === '/knowledge') window.dispatchEvent(new CustomEvent('knowledge:select', { detail: entity }))
})
onMounted(() => window.addEventListener('storage', onStorage))
onBeforeUnmount(() => window.removeEventListener('storage', onStorage))
const currentYear = new Date().getFullYear()
</script>

<template>
  <ElConfigProvider :locale="zhCn">
  <div v-if="activePath !== '/login'" class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="/" @click.prevent="navigate('dashboard')"><span class="brand-mark"><GraduationCap :size="21" /></span><span><strong>知序</strong><small>学习平台</small></span></a>
      <button class="course-switch" @click="router.push('/courses')"><span class="course-badge"><BookOpenCheck :size="18" /></span><span class="course-switch-copy"><strong>课程空间</strong><small>查看与选择课程</small></span><ChevronDown :size="16" /></button>
      <nav class="primary-nav" aria-label="主导航"><div v-for="group in navGroups" :key="group.title" class="nav-group"><p class="nav-label">{{ group.title }}</p><button v-for="item in group.items" :key="item.path" class="nav-item" :class="{ active: activePath === item.path }" @click="router.push(item.path)"><component :is="item.icon" :size="18" :stroke-width="1.8" /><span>{{ item.label }}</span><i v-if="item.path === '/wrong-book'" class="nav-count" :title="metricsError || (metricsLoading ? '正在刷新待复习数量' : '待复习错题数量')">{{ wrongCount }}</i></button></div></nav>
      <div class="sidebar-bottom"><div class="help-box"><span class="help-icon"><CircleHelp :size="17" /></span><strong>需要学习帮助？</strong><small>让 AI 助教为你讲解知识点</small><button @click="navigate('tutor')">开始提问 <ChevronDown :size="14" /></button></div><button class="nav-item" :class="{ active: activePath === '/experiments' }" @click="navigate('experiments')"><Target :size="18" /><span>问答实验</span></button><button class="nav-item" :class="{ active: activePath === '/experiment-tasks' }" @click="router.push('/experiment-tasks')"><FlaskConical :size="18" /><span>实验任务</span></button><button v-if="isAdmin" class="nav-item" :class="{ active: activePath === '/admin' }" @click="router.push('/admin')"><Settings2 :size="18" /><span>管理后台</span></button><button v-if="isAdmin" class="nav-item" :class="{ active: activePath === '/admin/teaching-dashboard' }" @click="router.push('/admin/teaching-dashboard')"><BarChart3 :size="18" /><span>教学看板</span></button><button v-if="isAdmin" class="nav-item" :class="{ active: activePath === '/admin/question-bank' }" @click="router.push('/admin/question-bank')"><BookOpenCheck :size="18" /><span>题库版本</span></button><button v-if="isAdmin" class="nav-item" :class="{ active: activePath === '/admin/micro-lessons' }" @click="router.push('/admin/micro-lessons')"><BookOpenCheck :size="18" /><span>微课草稿</span></button><button v-if="isAdmin" class="nav-item" :class="{ active: activePath === '/admin/experiment-tasks' }" @click="router.push('/admin/experiment-tasks')"><FlaskConical :size="18" /><span>实验任务管理</span></button><button v-if="isAdmin" class="nav-item" :class="{ active: activePath === '/admin/diagnostics' }" @click="router.push('/admin/diagnostics')"><TriangleAlert :size="18" /><span>错因配置</span></button><button v-if="isAdmin" class="nav-item" :class="{ active: activePath === '/admin/evaluations' }" @click="router.push('/admin/evaluations')"><FlaskConical :size="18" /><span>评估汇总</span></button><small class="sidebar-version">知序学习平台 · {{ currentYear }}</small></div>
    </aside>
    <main class="main-area"><header class="topbar"><div class="breadcrumb"><span>学习空间</span><i>/</i><strong>{{ pageTitle }}</strong></div><div class="topbar-actions"><span class="course-status"><i></i>{{ session.user.value ? '已登录' : '账户待确认' }}</span><button class="help-quick" title="学习帮助" @click="navigate('tutor')"><CircleHelp :size="18" /></button><button class="user-menu" :title="loggingOut ? '正在退出' : '退出登录'" :disabled="loggingOut" @click="logout"><span class="avatar">{{ avatar }}</span><span>{{ userName }}</span><ChevronDown :size="14" /></button></div></header><div class="page-content"><div v-if="session.error.value" class="inline-notice" role="alert">{{ session.error.value }}<button class="text-button" @click="session.refresh()">重试</button></div><RouterView v-slot="{ Component }"><component :is="Component" @navigate="navigate" /></RouterView></div></main>
  </div>
  <RouterView v-else />
  </ElConfigProvider>
</template>
