import { api } from './http'
import type { AdaptivePracticeItem, QuizDiagnosis, SpacedReviewCard, SpacedReviewResult, StudyPlan, StudyPlanTask } from './types'

export const questions = (knowledgePointId?: number) => api.get('/quizzes', { params: { knowledgePointId } }).then((r) => r.data)
export const submitAnswer = (questionId: number, answer: string[]) => api.post('/quizzes/' + questionId + '/submit', { answer }).then((r) => r.data)
export const wrongBook = () => api.get('/wrong-book').then((r) => r.data)
export const reviewWrong = (id: number, answer: string[]) => api.post('/wrong-book/' + id + '/review', { answer }).then((r) => r.data)
export const markWrongMastered = (id: number) => api.post('/wrong-book/' + id + '/mastered').then((r) => r.data)
export const spacedReviewsDue = (wrongQuestionId?: number) => api.get<SpacedReviewCard[]>('/spaced-reviews/due', { params: wrongQuestionId ? { wrongQuestionId } : undefined }).then((r) => r.data)
export const spacedReviewsPaused = () => api.get<SpacedReviewCard[]>('/spaced-reviews/paused').then((r) => r.data)
export const submitSpacedReview = (id: number, payload: { answer: string[]; recallRating: number; requestKey: string }) => api.post<SpacedReviewResult>('/spaced-reviews/' + id + '/review', payload).then((r) => r.data)
export const setSpacedReviewState = (id: number, state: 'ACTIVE' | 'PAUSED' | 'MASTERED') => api.put('/spaced-reviews/' + id + '/state', { state }).then((r) => r.data)
export const studyPlanToday = () => api.get<StudyPlan>('/study-plans/today').then((r) => r.data)
export const generateStudyPlan = (budgetMinutes: number) => api.post<StudyPlan>('/study-plans', { budgetMinutes }).then((r) => r.data)
export const updateStudyPlanTask = (taskId: number, action: 'COMPLETE' | 'SKIP') => api.put<StudyPlanTask>('/study-plans/tasks/' + taskId, { action }).then((r) => r.data)
export const adaptivePractice = (params: { limit?: number; courseId?: number } = {}) => api.get<AdaptivePracticeItem[]>('/adaptive-practice', { params }).then((r) => r.data)
export const diagnoseAnswer = (questionId: number, selected: string[]) => api.post<QuizDiagnosis[]>('/quizzes/' + questionId + '/diagnosis', { selected }).then((r) => r.data)
