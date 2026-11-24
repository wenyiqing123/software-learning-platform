import { api } from './http'
import type { ExperimentAssignment } from './types'

export const compare = (question: string) => api.post('/ai/compare', { question }, { timeout: 180_000 }).then((r) => r.data)
export const listExperiments = () => api.get('/experiments').then((r) => r.data)
export const runExperiment = (payload: { question?: string; experimentType?: 'METHODS' | 'QWEN_MATRIX'; includeAblations?: boolean; referenceAnswer?: string; expectedEntities?: string[]; taskId?: number }) => api.post('/experiments/run', payload, { timeout: 600_000 }).then((r) => r.data)
export const experimentAssignments = () => api.get<ExperimentAssignment[]>('/experiment-tasks').then((r) => r.data)
export const studentAssignments = () => api.get<ExperimentAssignment[]>('/experiment-tasks').then((r) => r.data)
export const startExperimentTask = (assignmentId: number) => api.post<ExperimentAssignment>('/experiment-tasks/' + assignmentId + '/start').then((r) => r.data)
export const rateExperiment = (id: string, ratings: Record<string, number>) => api.put('/experiments/' + id + '/ratings', { ratings }).then((r) => r.data)
export const exportExperimentsCsv = () => api.get('/experiments/export', { responseType: 'blob' }).then((r) => r.data)
export const exportExperimentsJson = () => api.get('/experiments/export/json').then((r) => r.data)
