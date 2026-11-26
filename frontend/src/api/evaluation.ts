import { api } from './http'
import type { EvaluationSession, EvaluationSummary } from './types'

export const evaluationSessions = () => api.get<EvaluationSession[]>('/evaluations/sessions').then((r) => r.data)
export const startEvaluation = (payload: { conditionKey: string; taskKey: string; taskOrder?: string; assignmentId?: number; consentVersion: string; consentAccepted: boolean }) => api.post<EvaluationSession>('/evaluations/sessions', payload).then((r) => r.data)
export const recordEvaluationMeasurement = (id: number, payload: { phase: 'PRE' | 'POST' | 'DELAYED' | 'EQUIVALENT'; score: number; responses?: string[] }) => api.post('/evaluations/sessions/' + id + '/measurements', payload).then((r) => r.data)
export const completeEvaluation = (id: number, payload: { elapsedSeconds: number; psychologicalEffort: number; feedback: string }) => api.post<EvaluationSession>('/evaluations/sessions/' + id + '/complete', payload).then((r) => r.data)
export const abandonEvaluation = (id: number) => api.delete('/evaluations/sessions/' + id).then((r) => r.data)
export const evaluationSummary = (conditionKey?: string) => api.get<EvaluationSummary[]>('/admin/evaluations/summary', { params: conditionKey ? { conditionKey } : undefined }).then((r) => r.data)
export const exportEvaluationSummary = (conditionKey?: string) => api.get('/admin/evaluations/export', { params: conditionKey ? { conditionKey } : undefined, responseType: 'blob' }).then((r) => r.data)
