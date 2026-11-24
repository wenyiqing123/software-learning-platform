import { api } from './http'
import type { ExperimentGroup, ExperimentTask } from './types'

export const adminExperimentTasks = () => api.get<ExperimentTask[]>('/admin/experiment-tasks').then((r) => r.data)
export const createAdminExperimentTask = (payload: { title: string; description: string; experimentType: 'METHODS' | 'QWEN_MATRIX'; question: string; referenceAnswer: string; expectedEntities: string[]; includeAblations: boolean }) => api.post<ExperimentTask>('/admin/experiment-tasks', payload).then((r) => r.data)
export const setAdminExperimentTaskStatus = (id: number, status: 'DRAFT' | 'OPEN' | 'CLOSED') => api.put<ExperimentTask>('/admin/experiment-tasks/' + id + '/status', { status }).then((r) => r.data)
export const listExperimentGroups = (taskId: number) => api.get<ExperimentGroup[]>('/admin/experiment-tasks/' + taskId + '/groups').then((r) => r.data)
export const createExperimentGroup = (taskId: number, payload: { name: string; conditionKey: string; sortOrder?: number; taskOrder: 'A→B' | 'B→A' }) => api.post<ExperimentGroup>('/admin/experiment-tasks/' + taskId + '/groups', payload).then((r) => r.data)
export const assignExperimentGroup = (taskId: number, payload: { groupId: number; userIds: number[] }) => api.post<void>('/admin/experiment-tasks/' + taskId + '/assign', payload).then((r) => r.data)
