import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { learningApi } from './api/client'

export type UserRole = 'STUDENT' | 'ADMIN'

export function homeForRole(role: string | null | undefined) {
  return role === 'ADMIN' ? '/teaching-dashboard' : '/dashboard'
}

const routes: RouteRecordRaw[] = [
  { path: '/login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
  { path: '/', component: () => import('./views/DashboardView.vue'), meta: { roles: ['STUDENT', 'ADMIN'] } },
  { path: '/dashboard', component: () => import('./views/DashboardView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/courses', component: () => import('./views/CourseView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/knowledge', component: () => import('./views/GraphView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/mastery-map', component: () => import('./views/MasteryMapView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/tutor', component: () => import('./views/TutorView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/practice', component: () => import('./views/PracticeView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/adaptive-practice', component: () => import('./views/AdaptivePracticeView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/wrong-book', component: () => import('./views/WrongBookView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/review', component: () => import('./views/ReviewView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/study-plan', component: () => import('./views/StudyPlanView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/micro-lessons', component: () => import('./views/MicroLessonView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/evaluation', component: () => import('./views/EvaluationView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/learning-report', component: () => import('./views/LearningReportView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/recommendations', component: () => import('./views/RecommendationsView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/experiment-tasks', component: () => import('./views/ExperimentTasksView.vue'), meta: { roles: ['STUDENT'] } },
  { path: '/teaching-dashboard', component: () => import('./views/TeachingDashboardView.vue'), meta: { roles: ['ADMIN'] } },
  { path: '/admin/content', component: () => import('./views/AdminContentCenterView.vue'), meta: { roles: ['ADMIN'] } },
  { path: '/admin/experiments', component: () => import('./views/AdminExperimentCenterView.vue'), meta: { roles: ['ADMIN'] } },
  { path: '/experiments', component: () => import('./views/ExperimentsView.vue'), meta: { roles: ['ADMIN'] } },
  { path: '/admin', redirect: '/teaching-dashboard', meta: { roles: ['ADMIN'] } },
  { path: '/admin/teaching-dashboard', redirect: '/teaching-dashboard', meta: { roles: ['ADMIN'] } },
  { path: '/admin/question-bank', redirect: '/admin/content?tab=question-bank', meta: { roles: ['ADMIN'] } },
  { path: '/admin/micro-lessons', redirect: '/admin/content?tab=micro-lessons', meta: { roles: ['ADMIN'] } },
  { path: '/admin/diagnostics', redirect: '/admin/content?tab=diagnosis', meta: { roles: ['ADMIN'] } },
  { path: '/admin/experiment-tasks', redirect: '/admin/experiments?tab=tasks', meta: { roles: ['ADMIN'] } },
  { path: '/admin/evaluations', redirect: '/admin/experiments?tab=evaluation', meta: { roles: ['ADMIN'] } },
]

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to) {
    if (to.hash) return { el: to.hash, top: 84, behavior: 'smooth' }
    return { top: 0 }
  },
  routes,
})

async function resolveRole(): Promise<UserRole | null> {
  const cached = localStorage.getItem('slp-user-role')
  if (cached === 'STUDENT' || cached === 'ADMIN') return cached
  try {
    const account = await learningApi.currentUser()
    const role = account?.role === 'ADMIN' ? 'ADMIN' : account?.role === 'STUDENT' ? 'STUDENT' : null
    if (role) localStorage.setItem('slp-user-role', role)
    return role
  } catch { return null }
}

router.beforeEach(async (to) => {
  const token = localStorage.getItem('slp-access-token')
  if (to.path === '/login') {
    if (!token) return true
    const role = await resolveRole()
    return role ? homeForRole(role) : true
  }
  if (!token) return '/login'
  const role = await resolveRole()
  if (!role) return '/login'
  if (to.path === '/') return homeForRole(role)
  const allowed = (to.meta.roles as UserRole[] | undefined) || []
  if (allowed.includes(role)) return true
  return { path: homeForRole(role), query: { notice: role === 'ADMIN' ? 'admin' : 'student' } }
})

export default router
