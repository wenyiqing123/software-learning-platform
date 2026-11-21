<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import ElConfigProvider from 'element-plus/es/components/config-provider/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { Activity, BarChart3, BrainCircuit, CalendarCheck, ChevronDown, CircleHelp, Compass, GraduationCap, LayoutDashboard, Network, RefreshCw, Settings2, Target, TriangleAlert, BookOpenCheck, RotateCcw, FlaskConical } from '@lucide/vue'
import { learningApi } from './api/client'
import { resetLearningMetrics, useLearningMetrics } from './composables/useLearningMetrics'
import { useSystemStatus } from './composables/useSystemStatus'
import { statusLabel, statusTone } from './domain/systemStatus'
import { resetSession, useSession } from './state/session'

const router = useRouter()
const route = useRoute()
const session = useSession()
const loggingOut = ref(false)
const isAdmin = computed(() => session.user.value?.role === 'ADMIN')
const { overview, loading: metricsLoading, error: metricsError, reload } = useLearningMetrics(computed(() => session.user.value?.role))
const { status: systemStatus, loading: statusLoading, error: statusError, reload: reloadStatus } = useSystemStatus()
const healthOpen = ref(false)
const navOpen = ref<Record<string, boolean>>({ learning: true, practice: true, research: true, admin: false })
const userName = computed(() => session.user.value?.displayName || session.user.value?.username || (session.loading.value ? '正在确认账户' : '账户未确认'))
const avatar = computed(() => Array.from(session.user.value?.displayName || session.user.value?.username || '')[0] || '·')
const activePath = computed(() => route.path)
const homePath = computed(() => isAdmin.value ? '/teaching-dashboard' : '/dashboard')
const roleNotice = computed(() => route.query.notice === 'admin' ? '当前为管理员视图，不包含学生个人学习记录。' : route.query.notice === 'student' ? '当前为学生视图，管理功能仅对管理员开放。' : '')
const titles: Record<string, string> = { '/': '学习空间', '/dashboard': '学习概览', '/courses': '课程列表', '/knowledge': '知识图谱', '/mastery-map': '个性化掌握图谱', '/tutor': 'AI 学习助教', '/practice': '课程练习', '/adaptive-practice': '自适应专项练习', '/wrong-book': '错题复习', '/review': '间隔复习', '/study-plan': '每日计划', '/micro-lessons': '证据微课', '/evaluation': '学习评估', '/learning-report': '阶段性学习报告', '/recommendations': '个性化推荐', '/experiment-tasks': '实验任务', '/teaching-dashboard': '教学总览', '/admin/content': '内容与题库中心', '/admin/experiments': '实验与评价中心' }
const pageTitle = computed(() => titles[route.path] || '学习空间')
const wrongCount = computed(() => overview.value?.wrongCount ?? '—')
const navGroups = [
  { id: 'learning', title: '学习闭环', items: [{ path: '/dashboard', label: '学习概览', icon: LayoutDashboard }, { path: '/courses', label: '课程阅读', icon: BookOpenCheck }, { path: '/knowledge', label: '知识图谱', icon: Network }, { path: '/mastery-map', label: '掌握图谱', icon: Target }, { path: '/micro-lessons', label: '证据微课', icon: BookOpenCheck }, { path: '/tutor', label: 'AI 学习助教', icon: BrainCircuit }] },
  { id: 'practice', title: '练习与巩固', items: [{ path: '/practice', label: '课程练习', icon: BookOpenCheck }, { path: '/adaptive-practice', label: '专项练习', icon: Target }, { path: '/wrong-book', label: '错题整理', icon: TriangleAlert }, { path: '/review', label: '到期复习', icon: RotateCcw }, { path: '/study-plan', label: '每日计划', icon: CalendarCheck }, { path: '/recommendations', label: '为我推荐', icon: Compass }] },
  { id: 'research', title: '研究与评估', items: [{ path: '/evaluation', label: '学习评估', icon: FlaskConical }, { path: '/learning-report', label: '学习报告', icon: BarChart3 }, { path: '/experiment-tasks', label: '实验任务', icon: FlaskConical }] },
]
const adminItems = [{ path: '/teaching-dashboard', label: '教学总览', icon: BarChart3 }, { path: '/admin/content', label: '内容与题库中心', icon: BookOpenCheck }, { path: '/admin/experiments', label: '实验与评价中心', icon: FlaskConical }]
const healthTone = computed(() => statusTone(systemStatus.value?.overallStatus || 'NOT_CHECKED'))
const healthLabel = computed(() => statusLabel(systemStatus.value?.overallStatus || 'NOT_CHECKED'))
const healthIssues = computed(() => systemStatus.value?.dependencies.filter(item => item.status !== 'UP') || [])
const healthCheckedAt = computed(() => systemStatus.value ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(systemStatus.value.checkedAt)) : '尚未检查')
function sectionIsActive(items: Array<{ path: string }>) { return items.some(item => activePath.value === item.path || (item.path !== '/' && activePath.value.startsWith(item.path + '/'))) }
function sectionOpen(id: string) { return navOpen.value[id] ?? true }
function toggleSection(id: string) { navOpen.value = { ...navOpen.value, [id]: !sectionOpen(id) } }
function ensureActiveSection() {
  for (const group of [...navGroups, { id: 'admin', title: '管理工作台', items: adminItems }]) if (sectionIsActive(group.items)) navOpen.value = { ...navOpen.value, [group.id]: true }
}
function navigate(view: string, entity?: string) {
  const paths: Record<string, string> = { dashboard: '/dashboard', courses: '/courses', knowledge: '/knowledge', tutor: '/tutor', practice: '/practice', 'adaptive-practice': '/adaptive-practice', 'wrong-book': '/wrong-book', review: '/review', 'study-plan': '/study-plan', evaluation: '/evaluation', recommendations: '/recommendations', experiments: '/admin/experiments' }
  void router.push({ path: paths[view] || (isAdmin.value ? '/teaching-dashboard' : '/dashboard'), query: entity ? { entity } : undefined })
}
function refreshShell() {
  if (route.path === '/login') { resetSession(); resetLearningMetrics(); return }
  void session.refresh()
  void reload()
  void reloadStatus()
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
    localStorage.removeItem('slp-user-role')
    resetSession()
    resetLearningMetrics()
    loggingOut.value = false
    void router.replace('/login')
  }
}
watch(() => route.fullPath, () => { refreshShell(); ensureActiveSection() }, { immediate: true })
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
      <a class="brand" :href="homePath" @click.prevent="router.push(homePath)"><span class="brand-mark"><GraduationCap :size="21" /></span><span><strong>知序</strong><small>{{ isAdmin ? '管理工作台' : '学习平台' }}</small></span></a>
      <button v-if="!isAdmin" class="course-switch" @click="router.push('/courses')"><span class="course-badge"><BookOpenCheck :size="18" /></span><span class="course-switch-copy"><strong>课程空间</strong><small>查看与选择课程</small></span><ChevronDown :size="16" /></button>
      <nav v-if="!isAdmin" class="primary-nav" aria-label="学生主导航">
        <div v-for="group in navGroups" :key="group.id" class="nav-group">
          <button class="nav-group-toggle" :aria-expanded="sectionOpen(group.id)" @click="toggleSection(group.id)"><span class="nav-label">{{ group.title }}</span><ChevronDown :size="14" :class="{ rotated: sectionOpen(group.id) }" /></button>
          <div v-show="sectionOpen(group.id)" class="nav-group-items">
            <button v-for="item in group.items" :key="item.path" class="nav-item" :class="{ active: activePath === item.path }" @click="router.push(item.path)"><component :is="item.icon" :size="18" :stroke-width="1.8" /><span>{{ item.label }}</span><i v-if="item.path === '/wrong-book'" class="nav-count" :title="metricsError || (metricsLoading ? '正在刷新待复习数量' : '待复习错题数量')">{{ wrongCount }}</i></button>
          </div>
        </div>
        <div v-if="isAdmin" class="nav-group">
          <button class="nav-group-toggle" :aria-expanded="sectionOpen('admin')" @click="toggleSection('admin')"><span class="nav-label">管理工作台</span><ChevronDown :size="14" :class="{ rotated: sectionOpen('admin') }" /></button>
          <div v-show="sectionOpen('admin')" class="nav-group-items"><button v-for="item in adminItems" :key="item.path" class="nav-item" :class="{ active: activePath === item.path }" @click="router.push(item.path)"><component :is="item.icon" :size="18" :stroke-width="1.8" /><span>{{ item.label }}</span></button></div>
        </div>
      </nav>
      <div class="sidebar-bottom"><div v-if="!isAdmin" class="help-box"><span class="help-icon"><CircleHelp :size="17" /></span><strong>需要学习帮助？</strong><small>让 AI 助教为你讲解知识点</small><button @click="navigate('tutor')">开始提问 <ChevronDown :size="14" /></button></div><small class="sidebar-version">知序学习平台 · {{ currentYear }}</small></div>
    </aside>
    <main class="main-area"><header class="topbar"><div class="breadcrumb"><span>{{ isAdmin ? '管理空间' : '学习空间' }}</span><i>/</i><strong>{{ pageTitle }}</strong></div><div class="topbar-actions"><span class="course-status"><i></i>{{ session.user.value ? '已登录' : '账户待确认' }}</span><div class="system-status-wrap"><button class="system-status-button" :class="'tone-' + healthTone" :aria-expanded="healthOpen" aria-haspopup="dialog" title="查看系统依赖状态" @click="healthOpen = !healthOpen"><Activity :size="15" /><span>依赖 {{ healthLabel }}</span><ChevronDown :size="13" /></button><div v-if="healthOpen" class="system-status-panel" role="dialog" aria-label="系统依赖状态"><div class="system-status-heading"><strong>系统依赖状态</strong><button class="icon-button" title="重新检查依赖" :disabled="statusLoading" @click="reloadStatus"><RefreshCw :size="15" :class="{ spinning: statusLoading }" /></button></div><p v-if="statusError" class="system-status-error">{{ statusError }}</p><ul v-if="systemStatus" class="system-status-list"><li v-for="item in systemStatus.dependencies" :key="item.key"><span class="system-status-item-name"><i :class="'status-dot tone-' + statusTone(item.status)"></i>{{ item.label }}</span><strong :class="'tone-text-' + statusTone(item.status)">{{ statusLabel(item.status) }}</strong><small v-if="item.status !== 'UP'">{{ item.impact }}</small></li></ul><p v-if="systemStatus && !healthIssues.length" class="system-status-ok">课程、题库、错题和推荐依赖均可访问。</p><p class="system-status-foot">最近检查 {{ healthCheckedAt }} · 状态异常时不会影响已加载的本地学习内容。</p></div></div><button v-if="!isAdmin" class="help-quick" title="学习帮助" @click="navigate('tutor')"><CircleHelp :size="18" /></button><button class="user-menu" :title="loggingOut ? '正在退出' : '退出登录'" :disabled="loggingOut" @click="logout"><span class="avatar">{{ avatar }}</span><span>{{ userName }}</span><ChevronDown :size="14" /></button></div></header><div class="page-content"><div v-if="roleNotice" class="inline-notice role-notice" role="status">{{ roleNotice }}</div><div v-if="session.error.value" class="inline-notice" role="alert">{{ session.error.value }}<button class="text-button" @click="session.refresh()">重试</button></div><RouterView v-slot="{ Component }"><component :is="Component" @navigate="navigate" /></RouterView></div></main>
  </div>
  <RouterView v-else />
  </ElConfigProvider>
</template>
