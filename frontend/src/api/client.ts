import axios from 'axios'
import { SseDecoder } from '../domain/sse'
import type { AiDependencySummary, SystemStatus } from '../domain/systemStatus'
export const api = axios.create({ baseURL: '/api', timeout: 20_000, headers: { 'Content-Type': 'application/json' } })
let refreshInFlight: Promise<string> | null = null
function clearSession() {
  for (const key of ['slp-access-token', 'slp-refresh-token', 'slp-user-name']) localStorage.removeItem(key)
  window.dispatchEvent(new Event('session:changed'))
  if (window.location.pathname !== '/login') window.location.assign('/login')
}
export function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem('slp-refresh-token')
  if (!refreshToken) { clearSession(); return Promise.reject(new Error('请重新登录')) }
  refreshInFlight ??= axios.post('/api/auth/refresh', { refreshToken }, { timeout: 10_000 }).then(({ data }) => {
    localStorage.setItem('slp-access-token', data.accessToken)
    localStorage.setItem('slp-refresh-token', data.refreshToken)
    return data.accessToken as string
  }).catch((error) => {
    if (error.response?.status === 401 || error.response?.status === 403) clearSession()
    throw error
  }).finally(() => { refreshInFlight = null })
  return refreshInFlight
}
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('slp-access-token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})
api.interceptors.response.use((response) => response, async (error) => {
  const config = error.config as (typeof error.config & { _slpRetried?: boolean }) | undefined
  if (error.response?.status !== 401 || !config || config._slpRetried || !shouldRefreshAfterUnauthorized(config.url)) return Promise.reject(error)
  config._slpRetried = true
  const token = await refreshAccessToken()
  config.headers.Authorization = 'Bearer ' + token
  return api(config)
})
export function apiError(error: unknown, fallback = '请求未完成，请稍后重试'): string {
  if (axios.isAxiosError(error)) return error.response?.data?.message || (error.code === 'ECONNABORTED' ? '请求耗时较长，请稍后重试' : fallback)
  return error instanceof Error ? error.message : fallback
}
export function shouldRefreshAfterUnauthorized(url?: string): boolean {
  if (!url) return false
  const endpoint = url.split('?')[0]
  return !['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'].includes(endpoint)
}

export interface GraphNode {
  id: string
  label: string
  category?: string
}

export interface GraphEdge {
  source: string
  target: string
  label: string
  score?: number
}

export interface GraphResponse {
  nodes: GraphNode[]
  edges: GraphEdge[]
  evidence: unknown[]
  available: boolean
}

export interface KnowledgePoint {
  id: number
  courseId: number
  chapterId: number
  name: string
  graphEntity: string
  description?: string
  sortOrder: number
  mastery: number
}

export interface PrerequisitePathNode {
  id: number
  courseId: number
  chapterId: number
  name: string
  graphEntity: string
  mastery: number
  prerequisiteIds: number[]
  status: 'MASTERED' | 'IN_PROGRESS' | 'UP_NEXT' | 'BLOCKED'
}

export interface PrerequisitePath {
  targetId: number
  courseId: number
  nodes: PrerequisitePathNode[]
  cycleDetected: boolean
}

export interface PrerequisiteQualityIssue {
  code: string
  severity: 'ERROR' | 'WARNING'
  pointId: number | null
  pointName: string | null
  relatedPointId: number | null
  relatedPointName: string | null
  message: string
}

export interface PrerequisiteQualityReport {
  courseId: number
  pointCount: number
  validEdgeCount: number
  errorCount: number
  warningCount: number
  valid: boolean
  issues: PrerequisiteQualityIssue[]
}
export interface MasteryMapNode { id: number; courseId: number; chapterId?: number; name: string; graphEntity: string; mastery: number; attemptCount: number; correctCount: number; dueReviewCount: number; status: 'MASTERED' | 'IN_PROGRESS' | 'BLOCKED' | 'UP_NEXT'; prerequisiteIds: number[] }
export interface MasteryMapEdge { source: number; target: number; relation: string }
export interface MasteryMap { courseId: number; courseName: string; nodes: MasteryMapNode[]; edges: MasteryMapEdge[] }
export interface LearningReport { from: string; to: string; days: number; courseId?: number; courseName: string; reading: { totalSections: number; completedSections: number; completedInPeriod: number }; practice: { attempts: number; correct: number; accuracy: number }; reviews: { reviews: number; correct: number; accuracy: number }; plans: { totalTasks: number; completedTasks: number; plannedMinutes: number; completedMinutes: number }; dailyActivity: { date: string; attempts: number; correct: number; readingCompleted: number }[]; chapters: { chapterId: number; chapterName: string; totalSections: number; completedSections: number; completedInPeriod: number }[]; knowledge: { pointId: number; pointName: string; mastery: number; attempts: number; correct: number; dueReviewCount: number }[] }
export interface LearningNote { id: number; sectionId?: number; knowledgePointId?: number; title: string; content: string; tags: string[]; createdAt?: string; updatedAt?: string }
export interface LearningBookmark { targetType: 'SECTION' | 'KNOWLEDGE_POINT'; targetId: number; bookmarked: boolean }
export interface TeachingDashboard { from: string; to: string; days: number; courseId?: number; courseName: string; overview: { activeLearners: number; totalSections: number; completedReads: number; attempts: number; correct: number; openWrong: number; averageMastery: number }; dailyActivity: { date: string; activeLearners: number; attempts: number; correct: number; completedReads: number }[]; weakPoints: { pointId: number; pointName: string; averageMastery: number; attempts: number; learners: number }[] }
export interface Course { id: number; code: string; name: string; term: string; description?: string; graphSpaceId?: number; enabled?: boolean }
export interface QuestionBankVersion { id: number; courseId: number; courseName: string; versionName: string; status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'; sourceFilename: string; createdBy?: number; createdAt?: string; publishedAt?: string; questionCount: number }
export interface QuestionBankImportError { rowNumber: number; message: string }
export interface QuestionBankImportResult { importId?: number; versionId: number; totalRows: number; successCount: number; errorCount: number; errors: QuestionBankImportError[] }
export interface MicroLessonTriple { subject: string; predicate: string; object: string; score: number }
export interface MicroLessonCommonError { stem: string; explanation: string; wrongCount: number }
export interface MicroLessonSelfTest { questionId: number; stem: string; options: { key: string; text: string }[] }
export interface MicroLessonPrerequisite { id: number; name: string; graphEntity: string }
export interface MicroLessonDraft { id: number; courseId: number; sectionId: number; title: string; status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'; summary?: string; generatedText: string; objectives: string[]; triples: MicroLessonTriple[]; commonErrors: MicroLessonCommonError[]; selfTests: MicroLessonSelfTest[]; prerequisites: MicroLessonPrerequisite[]; evidence?: { courseId?: number; sectionId?: number; knowledgePointId?: number; graphEntity: string; tripleCount: number; generatedAt?: string }; createdAt?: string; publishedAt?: string }
export interface ExperimentTask { id: number; title: string; description: string; experimentType: 'METHODS' | 'QWEN_MATRIX'; question: string; referenceAnswer: string; expectedEntities: string[]; includeAblations: boolean; status: 'DRAFT' | 'OPEN' | 'CLOSED'; createdBy?: number; groupCount: number; assignmentCount: number }
export interface ExperimentAssignment { id: number; taskId: number; groupId: number; userId: number; status: 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED'; title: string; description: string; experimentType: 'METHODS' | 'QWEN_MATRIX'; question: string; referenceAnswer: string; taskStatus: string; groupName: string; conditionKey: string; startedAt?: string; completedAt?: string }

export interface SpacedReviewCard { wrongQuestionId: number; questionId: number; question: string; knowledgePoint?: string; questionType: string; options: { key: string; text: string }[]; wrongCount: number; dueAt: string; previousIntervalMinutes: number; sectionId?: number }
export interface SpacedReviewResult { attemptId: number; questionId: number; correct: boolean; correctAnswer: string[]; explanation?: string; recallRating: number; intervalMinutes: number; nextDueAt: string; mastered: boolean; consecutiveSuccessfulReviews: number }
export interface StudyPlanTask { id: number; type: 'REVIEW' | 'READING' | 'PRACTICE'; targetId: number; title: string; detail: string; minutes: number; reason: string; state: 'PENDING' | 'COMPLETED' | 'SKIPPED'; path: string }
export interface StudyPlan { id: number; date: string; budgetMinutes: number; tasks: StudyPlanTask[] }
export interface AdaptivePracticeItem { questionId: number; stem: string; knowledgePointId?: number; knowledgePointName?: string; difficulty?: number; mastery: number; wrongCount: number; due: boolean; score: number; reason: string }
export interface QuizDiagnosis { optionKey: string; optionText: string; knowledgePointId: number; knowledgePointName: string; rationale: string; sectionId?: number; sectionTitle?: string; practiceQuestionId?: number }
export interface ConfiguredQuizDiagnosis { id: number; optionKey: string; optionText: string; knowledgePointId: number; knowledgePointName: string; rationale: string; enabled: boolean }
export interface EvaluationSession { id: number; conditionKey: string; taskKey: string; consentVersion: string; startedAt: string; completedAt?: string; elapsedSeconds?: number; psychologicalEffort?: number; feedback?: string }
export interface EvaluationSummary { conditionKey: string; sampleCount: number; meanPreScore?: number; meanPostScore?: number; meanGain?: number; meanEffort?: number; meanElapsedSeconds?: number; feedbackCount: number }
export interface TutorHintEvidence { subject: string; predicate: string; object: string; score: number }
export interface TutorHintResult { question: string; level: number; content: string; hasEvidence: boolean; evidence: TutorHintEvidence[]; nextLevelAvailable: boolean; source: string }
export const learningApi = {
  register: (payload: { username: string; password: string; displayName: string }) => api.post('/auth/register', payload).then((r) => r.data),
  login: (payload: { username: string; password: string }) => api.post('/auth/login', payload).then((r) => r.data),
  logout: (refreshToken: string) => api.post('/auth/logout', { refreshToken }).then((r) => r.data),
  currentUser: () => api.get('/auth/me').then((r) => r.data),
  overview: () => api.get('/dashboard/overview').then((r) => r.data),
  courses: () => api.get('/courses').then((r) => r.data),
  knowledgePoints: (courseId?: number) => api.get<KnowledgePoint[]>('/knowledge/points', { params: courseId ? { courseId } : undefined }).then((r) => r.data),
  learningPath: (targetId: number) => api.get<PrerequisitePath>('/knowledge/learning-path', { params: { targetId } }).then((r) => r.data),
  prerequisiteQuality: (courseId: number) => api.get<PrerequisiteQualityReport>('/admin/courses/' + courseId + '/prerequisite-quality').then((r) => r.data),
  masteryMap: (courseId?: number) => api.get<MasteryMap>('/knowledge/mastery-map', { params: courseId ? { courseId } : undefined }).then((r) => r.data),
  learningReport: (days = 7, courseId?: number) => api.get<LearningReport>('/reports/learning', { params: { days, ...(courseId ? { courseId } : {}) } }).then((r) => r.data),
  exportLearningReport: (days = 7, courseId?: number) => api.get('/reports/learning/export', { params: { days, ...(courseId ? { courseId } : {}) }, responseType: 'blob' }).then((r) => r.data as Blob),
  graph: (entity: string) => api.get<GraphResponse>('/knowledge/graph', { params: { entity } }).then((r) => r.data),
  questions: (knowledgePointId?: number) => api.get('/quizzes', { params: { knowledgePointId } }).then((r) => r.data),
  submitAnswer: (questionId: number, answer: string[]) => api.post('/quizzes/' + questionId + '/submit', { answer }).then((r) => r.data),
  wrongBook: () => api.get('/wrong-book').then((r) => r.data),
  reviewWrong: (id: number, answer: string[]) => api.post('/wrong-book/' + id + '/review', { answer }).then((r) => r.data),
  markWrongMastered: (id: number) => api.post('/wrong-book/' + id + '/mastered').then((r) => r.data),
  spacedReviewsDue: (wrongQuestionId?: number) => api.get<SpacedReviewCard[]>('/spaced-reviews/due', { params: wrongQuestionId ? { wrongQuestionId } : undefined }).then((r) => r.data),
  spacedReviewsPaused: () => api.get<SpacedReviewCard[]>('/spaced-reviews/paused').then((r) => r.data),
  submitSpacedReview: (id: number, payload: { answer: string[]; recallRating: number; requestKey: string }) => api.post<SpacedReviewResult>('/spaced-reviews/' + id + '/review', payload).then((r) => r.data),
  setSpacedReviewState: (id: number, state: 'ACTIVE' | 'PAUSED' | 'MASTERED') => api.put('/spaced-reviews/' + id + '/state', { state }).then((r) => r.data),
  studyPlanToday: () => api.get<StudyPlan>('/study-plans/today').then((r) => r.data),
  generateStudyPlan: (budgetMinutes: number) => api.post<StudyPlan>('/study-plans', { budgetMinutes }).then((r) => r.data),
  updateStudyPlanTask: (taskId: number, action: 'COMPLETE' | 'SKIP') => api.put<StudyPlanTask>('/study-plans/tasks/' + taskId, { action }).then((r) => r.data),
  adaptivePractice: (params: { limit?: number; courseId?: number } = {}) => api.get<AdaptivePracticeItem[]>('/adaptive-practice', { params }).then((r) => r.data),
  diagnoseAnswer: (questionId: number, selected: string[]) => api.post<QuizDiagnosis[]>('/quizzes/' + questionId + '/diagnosis', { selected }).then((r) => r.data),
  evaluationSessions: () => api.get<EvaluationSession[]>('/evaluations/sessions').then((r) => r.data),
  startEvaluation: (payload: { conditionKey: string; taskKey: string; consentVersion: string; consentAccepted: boolean }) => api.post<EvaluationSession>('/evaluations/sessions', payload).then((r) => r.data),
  recordEvaluationMeasurement: (id: number, payload: { phase: 'PRE' | 'POST' | 'EQUIVALENT'; score: number; responses?: string[] }) => api.post('/evaluations/sessions/' + id + '/measurements', payload).then((r) => r.data),
  completeEvaluation: (id: number, payload: { elapsedSeconds: number; psychologicalEffort: number; feedback: string }) => api.post<EvaluationSession>('/evaluations/sessions/' + id + '/complete', payload).then((r) => r.data),
  evaluationSummary: (conditionKey?: string) => api.get<EvaluationSummary[]>('/admin/evaluations/summary', { params: conditionKey ? { conditionKey } : undefined }).then((r) => r.data),
  exportEvaluationSummary: () => api.get('/admin/evaluations/export', { responseType: 'blob' }).then((r) => r.data),
  recommendations: () => api.get('/recommendations').then((r) => r.data),
  ask: (question: string, mode: string) => api.post('/ai/ask', { question, mode }).then((r) => r.data),
  tutorHint: (question: string, level: number, historyMessages: string[] = [], historyEntities: string[] = []) => api.post<TutorHintResult>('/ai/tutor/hint', { question, level, historyMessages, historyEntities }).then((r) => r.data),
  compare: (question: string) => api.post('/ai/compare', { question }, { timeout: 180_000 }).then((r) => r.data),
  listExperiments: () => api.get('/experiments').then((r) => r.data),
  runExperiment: (payload: { question: string; experimentType?: 'METHODS' | 'QWEN_MATRIX'; includeAblations?: boolean; referenceAnswer?: string; expectedEntities?: string[]; taskId?: number }) => api.post('/experiments/run', payload, { timeout: 600_000 }).then((r) => r.data),
  experimentAssignments: () => api.get<ExperimentAssignment[]>('/experiment-tasks').then((r) => r.data),
  startExperimentTask: (assignmentId: number) => api.post<ExperimentAssignment>('/experiment-tasks/' + assignmentId + '/start').then((r) => r.data),
  adminExperimentTasks: () => api.get<ExperimentTask[]>('/admin/experiment-tasks').then((r) => r.data),
  createAdminExperimentTask: (payload: { title: string; description: string; experimentType: 'METHODS' | 'QWEN_MATRIX'; question: string; referenceAnswer: string; expectedEntities: string[]; includeAblations: boolean }) => api.post<ExperimentTask>('/admin/experiment-tasks', payload).then((r) => r.data),
  setAdminExperimentTaskStatus: (id: number, status: 'DRAFT' | 'OPEN' | 'CLOSED') => api.put<ExperimentTask>('/admin/experiment-tasks/' + id + '/status', { status }).then((r) => r.data),
  sessions: () => api.get('/tutor/sessions').then(r => r.data),
  createSession: (title: string) => api.post('/tutor/sessions', { title }).then(r => r.data),
  sessionMessages: (id: number) => api.get('/tutor/sessions/' + id + '/messages').then(r => r.data),
  saveMessage: (id: number, payload: { role: string; content: string; mode: string; metadata?: unknown }) => api.post('/tutor/sessions/' + id + '/messages', payload).then(r => r.data),
  deleteSession: (id: number) => api.delete('/tutor/sessions/' + id).then(r => r.data),
  dependencies: () => api.get<AiDependencySummary>('/ai/dependencies').then(r => r.data),
  systemStatus: () => api.get<SystemStatus>('/system/status').then(r => r.data),
  rateExperiment: (id: string, ratings: Record<string, number>) => api.put('/experiments/' + id + '/ratings', { ratings }).then((r) => r.data),
  streamAsk: async (question: string, mode: string, onEvent: (name: string, payload: any) => void, signal?: AbortSignal, history?: { historyMessages: string[]; historyEntities: string[] }) => {
    const request = () => fetch('/api/ai/ask/stream', {
      method: 'POST', signal,
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + (localStorage.getItem('slp-access-token') || ''), Accept: 'text/event-stream' },
      body: JSON.stringify({ question, mode, ...history }),
    })
    let response = await request()
    if (response.status === 401) { await refreshAccessToken(); response = await request() }
    if (!response.ok || !response.body) {
      const error = await response.json().catch(() => ({}))
      throw new Error(error.message || error.detail || 'AI 服务暂不可用')
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let completed: any = null
    const parser = new SseDecoder(({ event, data }) => {
      let payload: any
      try { payload = JSON.parse(data) } catch { payload = { content: data } }
      if (event === 'error') throw new Error(payload.message || 'AI 请求失败')
      if (event === 'done') completed = payload
      onEvent(event, payload)
    })
    try {
      while (true) {
        const { value, done } = await reader.read()
        parser.push(decoder.decode(value || new Uint8Array(), { stream: !done }))
        if (done) break
      }
      parser.finish()
    } finally {
      await reader.cancel().catch(() => {})
      reader.releaseLock()
    }
    if (!completed) throw new Error('AI 流式响应未正常结束，请重试')
    return completed
  },
}
