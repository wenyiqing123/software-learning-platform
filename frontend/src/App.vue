<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import ElConfigProvider from 'element-plus/es/components/config-provider/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { Activity, BarChart3, BrainCircuit, CalendarCheck, ChevronDown, CircleHelp, Compass, GraduationCap, LayoutDashboard, Network, RefreshCw, Settings2, Target, TriangleAlert, BookOpenCheck, RotateCcw, FlaskConical, KeyRound, LogOut } from '@lucide/vue'
import { learningApi } from './api/client'
import { resetLearningMetrics, useLearningMetrics } from './composables/useLearningMetrics'
import { useSystemStatus } from './composables/useSystemStatus'
import { statusLabel, statusTone } from './domain/systemStatus'
import { resetSession, useSession } from './state/session'
import StudentLearningSections, { findGroupByPath } from './components/StudentLearningSections.vue'
import ChangePasswordDialog from './components/ChangePasswordDialog.vue'

const router = useRouter()
const route = useRoute()
const session = useSession()
const loggingOut = ref(false)
const userMenuOpen = ref(false)
const showChangePassword = ref(false)
const isAdmin = computed(() => session.user.value?.role === 'ADMIN')
const isStudent = computed(() => session.user.value?.role === 'STUDENT')
const currentStudentGroupId = computed(() => {
  if (!isStudent.value) return null
  return findGroupByPath(activePath.value)
})
const { overview, loading: metricsLoading, error: metricsError, reload } = useLearningMetrics(computed(() => session.user.value?.role))
const { status: systemStatus, loading: statusLoading, error: statusError, reload: reloadStatus } = useSystemStatus()
const healthOpen = ref(false)
const primaryNav = ref<HTMLElement | null>(null)
const navOpen = ref<Record<string, boolean>>({ admin: true })
const userName = computed(() => session.user.value?.displayName || session.user.value?.username || (session.loading.value ? '正在确认账户' : '账户未确认'))
const avatar = computed(() => Array.from(session.user.value?.displayName || session.user.value?.username || '')[0] || '·')
const activePath = computed(() => route.path)
const activeNavPath = computed(() => activePath.value === '/experiments' ? '/admin/experiments' : activePath.value)
const homePath = computed(() => isAdmin.value ? '/teaching-dashboard' : '/dashboard')
const roleNotice = computed(() => route.query.notice === 'admin' ? '当前为管理员视图，不包含学生个人学习记录。' : route.query.notice === 'student' ? '当前为学生视图，管理功能仅对管理员开放。' : '')
const titles: Record<string, string> = { '/': '学习空间', '/dashboard': '学习概览', '/courses': '课程列表', '/knowledge': '知识图谱', '/mastery-map': '个性化掌握图谱', '/tutor': 'AI 学习助教', '/practice': '课程练习', '/adaptive-practice': '自适应专项练习', '/wrong-book': '错题复习', '/review': '间隔复习', '/study-plan': '每日计划', '/micro-lessons': '证据微课', '/evaluation': '学习评估', '/learning-report': '阶段性学习报告', '/recommendations': '个性化推荐', '/experiment-tasks': '实验任务', '/teaching-dashboard': '教学总览', '/admin/content': '内容与题库中心', '/admin/experiments': '实验与评价中心' }
const pageTitle = computed(() => titles[route.path] || '学习空间')
const wrongCount = computed(() => overview.value?.wrongCount ?? '—')

interface StudentNavGroup {
  id: string
  path: string
  label: string
  icon: any
  subRoutes: string[]
}

const studentNavGroups: StudentNavGroup[] = [
  { id: 'overview', path: '/dashboard', label: '概览与课程', icon: LayoutDashboard, subRoutes: ['/dashboard', '/courses'] },
  { id: 'graph-tutor', path: '/knowledge', label: '图谱与助教', icon: Network, subRoutes: ['/knowledge', '/mastery-map', '/tutor', '/micro-lessons'] },
  { id: 'practice', path: '/practice', label: '练习与复习', icon: BookOpenCheck, subRoutes: ['/practice', '/adaptive-practice', '/wrong-book', '/review'] },
  { id: 'plan', path: '/study-plan', label: '计划与推荐', icon: CalendarCheck, subRoutes: ['/study-plan', '/recommendations'] },
  { id: 'experiment', path: '/experiment-tasks', label: '实验与评估', icon: FlaskConical, subRoutes: ['/experiment-tasks', '/evaluation', '/learning-report'] },
]

const adminItems = [{ path: '/teaching-dashboard', label: '教学总览', icon: BarChart3 }, { path: '/admin/content', label: '内容与题库中心', icon: BookOpenCheck }, { path: '/admin/experiments', label: '实验与评价中心', icon: FlaskConical }]
const healthTone = computed(() => statusTone(systemStatus.value?.overallStatus || 'NOT_CHECKED'))
const healthLabel = computed(() => statusLabel(systemStatus.value?.overallStatus || 'NOT_CHECKED'))
const healthIssues = computed(() => systemStatus.value?.dependencies.filter(item => item.status !== 'UP') || [])
const healthCheckedAt = computed(() => systemStatus.value ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(systemStatus.value.checkedAt)) : '尚未检查')
function sectionIsActive(items: Array<{ path: string }>) { return items.some(item => activeNavPath.value === item.path) }
function sectionOpen(id: string) { return navOpen.value[id] ?? true }
function toggleSection(id: string) { navOpen.value = { ...navOpen.value, [id]: !sectionOpen(id) } }
function ensureActiveSection() {
  if (sectionIsActive(adminItems)) navOpen.value = { ...navOpen.value, admin: true }
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
watch(() => route.fullPath, () => {
  userMenuOpen.value = false
  refreshShell()
  ensureActiveSection()
}, { immediate: true })
watch([primaryNav, activeNavPath], ([nav]) => {
  const item = nav?.querySelector('.nav-item.active')
  if (!nav || !item) return
  const visible = nav.getBoundingClientRect()
  const current = item.getBoundingClientRect()
  if (current.top < visible.top) nav.scrollTop += current.top - visible.top
  else if (current.bottom > visible.bottom) nav.scrollTop += current.bottom - visible.bottom
  if (current.left < visible.left) nav.scrollLeft += current.left - visible.left
  else if (current.right > visible.right) nav.scrollLeft += current.right - visible.right
}, { flush: 'post' })
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
      <nav v-if="!isAdmin" ref="primaryNav" class="primary-nav" aria-label="学生主导航">
        <div class="nav-group">
          <div class="nav-group-items">
            <button
              v-for="item in studentNavGroups"
              :key="item.path"
              class="nav-item"
              :class="{ active: item.subRoutes.includes(activeNavPath) }"
              :aria-current="item.subRoutes.includes(activeNavPath) ? 'page' : undefined"
              :aria-label="item.label"
              :title="item.label"
              @click="router.push(item.path)"
            >
              <component :is="item.icon" :size="18" :stroke-width="1.8" />
              <span>{{ item.label }}</span>
            </button>
          </div>
        </div>
      </nav>
      <nav v-else ref="primaryNav" class="primary-nav" aria-label="管理员主导航">
        <div class="nav-group">
          <button class="nav-group-toggle" :aria-expanded="sectionOpen('admin')" @click="toggleSection('admin')"><span class="nav-label">管理工作台</span><ChevronDown :size="14" :class="{ rotated: sectionOpen('admin') }" /></button>
          <div v-show="sectionOpen('admin')" class="nav-group-items"><button v-for="item in adminItems" :key="item.path" class="nav-item" :class="{ active: activeNavPath === item.path }" :aria-current="activeNavPath === item.path ? 'page' : undefined" :aria-label="item.label" :title="item.label" @click="router.push(item.path)"><component :is="item.icon" :size="18" :stroke-width="1.8" /><span>{{ item.label }}</span></button></div>
        </div>
      </nav>
      <div class="sidebar-bottom"><div v-if="!isAdmin" class="help-box"><span class="help-icon"><CircleHelp :size="17" /></span><strong>需要学习帮助？</strong><small>让 AI 助教为你讲解知识点</small><button @click="navigate('tutor')">开始提问 <ChevronDown :size="14" /></button></div><small class="sidebar-version">知序学习平台 · {{ currentYear }}</small></div>
    </aside>
    <main class="main-area"><header class="topbar"><div class="breadcrumb"><span>{{ isAdmin ? '管理空间' : '学习空间' }}</span><i>/</i><strong>{{ pageTitle }}</strong></div><div class="topbar-actions"><span class="course-status"><i></i>{{ session.user.value ? '已登录' : '账户待确认' }}</span><div class="system-status-wrap"><button class="system-status-button" :class="'tone-' + healthTone" :aria-expanded="healthOpen" aria-haspopup="dialog" title="查看系统依赖状态" @click="healthOpen = !healthOpen"><Activity :size="15" /><span>依赖 {{ healthLabel }}</span><ChevronDown :size="13" /></button><div v-if="healthOpen" class="system-status-panel" role="dialog" aria-label="系统依赖状态"><div class="system-status-heading"><strong>系统依赖状态</strong><button class="icon-button" title="重新检查依赖" :disabled="statusLoading" @click="reloadStatus"><RefreshCw :size="15" :class="{ spinning: statusLoading }" /></button></div><p v-if="statusError" class="system-status-error">{{ statusError }}</p><ul v-if="systemStatus" class="system-status-list"><li v-for="item in systemStatus.dependencies" :key="item.key"><span class="system-status-item-name"><i :class="'status-dot tone-' + statusTone(item.status)"></i>{{ item.label }}</span><strong :class="'tone-text-' + statusTone(item.status)">{{ statusLabel(item.status) }}</strong><small v-if="item.status !== 'UP'">{{ item.impact }}</small></li></ul><p v-if="systemStatus && !healthIssues.length" class="system-status-ok">课程、题库、错题和推荐依赖均可访问。</p><p class="system-status-foot">最近检查 {{ healthCheckedAt }} · 状态异常时不会影响已加载的本地学习内容。</p></div></div><button v-if="!isAdmin" class="help-quick" title="学习帮助" @click="navigate('tutor')"><CircleHelp :size="18" /></button><div class="user-menu-wrap"><button class="user-menu" :aria-expanded="userMenuOpen" aria-haspopup="menu" :title="loggingOut ? '正在退出' : '用户菜单'" :disabled="loggingOut" @click="userMenuOpen = !userMenuOpen"><span class="avatar">{{ avatar }}</span><span>{{ userName }}</span><ChevronDown :size="14" :class="{ rotated: userMenuOpen }" /></button><div v-if="userMenuOpen" class="user-menu-dropdown" role="menu"><button class="user-menu-item" role="menuitem" @click="userMenuOpen = false; showChangePassword = true"><KeyRound :size="15" /><span>修改密码</span></button><button class="user-menu-item" role="menuitem" :disabled="loggingOut" @click="userMenuOpen = false; logout()"><LogOut :size="15" /><span>{{ loggingOut ? '正在退出' : '退出登录' }}</span></button></div></div></div></header>
<StudentLearningSections
  v-if="isStudent && currentStudentGroupId"
  :group-id="currentStudentGroupId"
  :path="activePath"
  :query="route.query"
/>
<div class="page-content"><div v-if="roleNotice" class="inline-notice role-notice" role="status">{{ roleNotice }}</div><div v-if="session.error.value" class="inline-notice" role="alert">{{ session.error.value }}<button class="text-button" @click="session.refresh()">重试</button></div><RouterView v-slot="{ Component }"><component :is="Component" @navigate="navigate" /></RouterView></div><ChangePasswordDialog v-model="showChangePassword" /></main>
  </div>
  <RouterView v-else />
  </ElConfigProvider>
</template>
