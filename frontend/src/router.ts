import { createRouter, createWebHistory } from 'vue-router'
import { learningApi } from './api/client'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to) {
    if (to.hash) return { el: to.hash, top: 84, behavior: 'smooth' }
    return { top: 0 }
  },
  routes: [
    { path: '/login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
    { path: '/', component: () => import('./views/DashboardView.vue') },
    { path: '/courses', component: () => import('./views/CourseView.vue') },
    { path: '/admin', component: () => import('./views/AdminView.vue'), meta: { admin: true } },
    { path: '/admin/teaching-dashboard', component: () => import('./views/TeachingDashboardView.vue'), meta: { admin: true } },
    { path: '/admin/question-bank', component: () => import('./views/QuestionBankView.vue'), meta: { admin: true } },
    { path: '/admin/micro-lessons', component: () => import('./views/MicroLessonAdminView.vue'), meta: { admin: true } },
    { path: '/admin/diagnostics', component: () => import('./views/AdminDiagnosisView.vue'), meta: { admin: true } },
    { path: '/knowledge', component: () => import('./views/GraphView.vue') },
    { path: '/mastery-map', component: () => import('./views/MasteryMapView.vue') },
    { path: '/tutor', component: () => import('./views/TutorView.vue') },
    { path: '/practice', component: () => import('./views/PracticeView.vue') },
    { path: '/adaptive-practice', component: () => import('./views/AdaptivePracticeView.vue') },
    { path: '/wrong-book', component: () => import('./views/WrongBookView.vue') },
    { path: '/review', component: () => import('./views/ReviewView.vue') },
    { path: '/study-plan', component: () => import('./views/StudyPlanView.vue') },
    { path: '/micro-lessons', component: () => import('./views/MicroLessonView.vue') },
    { path: '/evaluation', component: () => import('./views/EvaluationView.vue') },
    { path: '/learning-report', component: () => import('./views/LearningReportView.vue') },
    { path: '/admin/evaluations', component: () => import('./views/EvaluationAdminView.vue'), meta: { admin: true } },
    { path: '/recommendations', component: () => import('./views/RecommendationsView.vue') },
    { path: '/experiments', component: () => import('./views/ExperimentsView.vue') },
    { path: '/experiment-tasks', component: () => import('./views/ExperimentTasksView.vue') },
    { path: '/admin/experiment-tasks', component: () => import('./views/ExperimentTaskAdminView.vue'), meta: { admin: true } },
  ],
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('slp-access-token')
  if (to.path !== '/login' && !token) return '/login'
  if (to.path === '/login' && token) return '/'
  if (to.meta.admin) {
    try { if ((await learningApi.currentUser()).role !== 'ADMIN') return '/' } catch { return '/login' }
  }
  return true
})

export default router
