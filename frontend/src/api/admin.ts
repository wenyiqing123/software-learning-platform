import { api } from './http'
import type { ExperimentTask } from './types'

export const adminExperimentTasks = () => api.get<ExperimentTask[]>('/admin/experiment-tasks').then((r) => r.data)
export const createAdminExperimentTask = (payload: { title: string; description: string; experimentType: 'METHODS' | 'QWEN_MATRIX'; question: string; referenceAnswer: string; expectedEntities: string[]; includeAblations: boolean }) => api.post<ExperimentTask>('/admin/experiment-tasks', payload).then((r) => r.data)
export const setAdminExperimentTaskStatus = (id: number, status: 'DRAFT' | 'OPEN' | 'CLOSED') => api.put<ExperimentTask>('/admin/experiment-tasks/' + id + '/status', { status }).then((r) => r.data)
