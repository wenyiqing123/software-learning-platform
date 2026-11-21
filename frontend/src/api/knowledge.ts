import { api } from './http'
import type { GraphResponse, KnowledgePoint, LearningReport, MasteryMap, PrerequisitePath, PrerequisiteQualityReport } from './types'

export const knowledgePoints = (courseId?: number) => api.get<KnowledgePoint[]>('/knowledge/points', { params: courseId ? { courseId } : undefined }).then((r) => r.data)
export const learningPath = (targetId: number) => api.get<PrerequisitePath>('/knowledge/learning-path', { params: { targetId } }).then((r) => r.data)
export const prerequisiteQuality = (courseId: number) => api.get<PrerequisiteQualityReport>('/admin/courses/' + courseId + '/prerequisite-quality').then((r) => r.data)
export const masteryMap = (courseId?: number) => api.get<MasteryMap>('/knowledge/mastery-map', { params: courseId ? { courseId } : undefined }).then((r) => r.data)
export const learningReport = (days = 7, courseId?: number) => api.get<LearningReport>('/reports/learning', { params: { days, ...(courseId ? { courseId } : {}) } }).then((r) => r.data)
export const exportLearningReport = (days = 7, courseId?: number) => api.get('/reports/learning/export', { params: { days, ...(courseId ? { courseId } : {}) }, responseType: 'blob' }).then((r) => r.data as Blob)
export const graph = (entity: string) => api.get<GraphResponse>('/knowledge/graph', { params: { entity } }).then((r) => r.data)
