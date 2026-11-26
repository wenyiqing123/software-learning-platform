export interface SectionItem {
  path: string
  label: string
  query?: Record<string, string>
}

export const STUDENT_SECTIONS: Record<string, SectionItem[]> = {
  overview: [
    { path: '/dashboard', label: '学习概览' },
    { path: '/courses', label: '课程阅读' },
    { path: '/notes', label: '笔记与收藏' },
  ],
  'graph-tutor': [
    { path: '/knowledge', label: '知识图谱' },
    { path: '/mastery-map', label: '掌握图谱' },
    { path: '/tutor', label: 'AI 学习助教' },
    { path: '/micro-lessons', label: '证据微课' },
  ],
  practice: [
    { path: '/practice', label: '课程练习' },
    { path: '/adaptive-practice', label: '专项练习' },
    { path: '/wrong-book', label: '错题整理' },
    { path: '/review', label: '到期复习' },
    { path: '/flashcards', label: '概念闪卡' },
  ],
  plan: [
    { path: '/study-plan', label: '每日计划' },
    { path: '/recommendations', label: '为我推荐' },
  ],
  experiment: [
    { path: '/experiment-tasks', label: '实验任务' },
    { path: '/evaluation', label: '学习评估' },
    { path: '/learning-report', label: '学习报告' },
  ],
}

export const ADMIN_SECTIONS: Record<string, SectionItem[]> = {
  'admin-teaching': [
    { path: '/teaching-dashboard', label: '教学总览' },
    { path: '/admin/users', label: '用户管理' },
  ],
  'admin-content': [
    { path: '/admin/content', label: '课程与知识点', query: { tab: 'course' } },
    { path: '/admin/content', label: '题库与版本', query: { tab: 'question-bank' } },
    { path: '/admin/content', label: '错因诊断', query: { tab: 'diagnosis' } },
    { path: '/admin/content', label: '微课管理', query: { tab: 'micro-lessons' } },
  ],
  'admin-experiments': [
    { path: '/admin/experiments', label: '问答对照实验', query: { tab: 'comparison' } },
    { path: '/admin/experiments', label: '任务与分组', query: { tab: 'tasks' } },
    { path: '/admin/experiments', label: '评价统计', query: { tab: 'evaluation' } },
  ],
}

const ADMIN_TAB_CONFIG: Record<string, { defaultTab: string; validTabs: string[] }> = {
  '/admin/content': { defaultTab: 'course', validTabs: ['course', 'question-bank', 'diagnosis', 'micro-lessons'] },
  '/admin/experiments': { defaultTab: 'comparison', validTabs: ['comparison', 'tasks', 'evaluation'] },
}

const COURSE_QUERY_KEYS = ['courseId', 'sectionId', 'knowledgePointId', 'entity']
const EXPERIMENT_QUERY_KEYS = ['taskId', 'assignmentId', 'conditionKey', 'taskKey', 'taskOrder']

export function resolveItemQuery(targetPath: string, rawQuery: Record<string, any> = {}): Record<string, any> | undefined {
  const clean: Record<string, any> = {}
  if (targetPath === '/courses') {
    for (const k of COURSE_QUERY_KEYS) {
      if (rawQuery[k] !== undefined && rawQuery[k] !== null && rawQuery[k] !== '') {
        clean[k] = rawQuery[k]
      }
    }
  } else if (['/evaluation', '/experiment-tasks', '/learning-report'].includes(targetPath)) {
    for (const k of EXPERIMENT_QUERY_KEYS) {
      if (rawQuery[k] !== undefined && rawQuery[k] !== null && rawQuery[k] !== '') {
        clean[k] = rawQuery[k]
      }
    }
  }
  return Object.keys(clean).length > 0 ? clean : undefined
}

export function resolveSubItemTo(item: SectionItem, rawQuery: Record<string, any> = {}) {
  if (item.query) {
    return { path: item.path, query: { ...item.query } }
  }
  const query = resolveItemQuery(item.path, rawQuery)
  return query ? { path: item.path, query } : { path: item.path }
}

export function findGroupByPath(targetPath: string): string | null {
  for (const [gid, items] of Object.entries(STUDENT_SECTIONS)) {
    if (items.some(item => item.path === targetPath)) {
      return gid
    }
  }
  return null
}

export function findAdminGroupByPath(targetPath: string): string | null {
  const normalized = targetPath === '/experiments' ? '/admin/experiments' : targetPath
  for (const [gid, items] of Object.entries(ADMIN_SECTIONS)) {
    if (items.some(item => item.path === normalized)) {
      return gid
    }
  }
  return null
}

export function isNavSubItemActive(item: SectionItem, currentNavPath: string, currentQuery: Record<string, any> = {}): boolean {
  if (currentNavPath !== item.path) return false
  if (!item.query?.tab) return true
  const tabMeta = ADMIN_TAB_CONFIG[currentNavPath]
  if (!tabMeta) return true
  const rawTab = typeof currentQuery.tab === 'string' ? currentQuery.tab : ''
  const activeTab = tabMeta.validTabs.includes(rawTab) ? rawTab : tabMeta.defaultTab
  return activeTab === item.query.tab
}
