import { api } from './http'
import type { AdminExperimentAssignment, AdminUserPage, AdminUserProfile, AdminUserSummary, CoverageAuditReport, ExperimentGroup, ExperimentTask } from './types'

export const adminExperimentTasks = () => api.get<ExperimentTask[]>('/admin/experiment-tasks').then((r) => r.data)
export const createAdminExperimentTask = (payload: { title: string; description: string; experimentType: 'METHODS' | 'QWEN_MATRIX'; question: string; referenceAnswer: string; expectedEntities: string[]; includeAblations: boolean }) => api.post<ExperimentTask>('/admin/experiment-tasks', payload).then((r) => r.data)
export const setAdminExperimentTaskStatus = (id: number, status: 'DRAFT' | 'OPEN' | 'CLOSED') => api.put<ExperimentTask>('/admin/experiment-tasks/' + id + '/status', { status }).then((r) => r.data)
export const listExperimentGroups = (taskId: number) => api.get<ExperimentGroup[]>('/admin/experiment-tasks/' + taskId + '/groups').then((r) => r.data)
export const listExperimentAssignments = (taskId: number) => api.get<AdminExperimentAssignment[]>('/admin/experiment-tasks/' + taskId + '/assignments').then((r) => r.data)
export const exportExperimentTasksCsv = (taskId?: number) => api.get('/admin/experiment-tasks/export', { params: taskId ? { taskId } : undefined, responseType: 'blob' }).then((r) => r.data as Blob)
export const createExperimentGroup = (taskId: number, payload: { name: string; conditionKey: string; sortOrder?: number; taskOrder: 'A→B' | 'B→A' }) => api.post<ExperimentGroup>('/admin/experiment-tasks/' + taskId + '/groups', payload).then((r) => r.data)
export const assignExperimentGroup = (taskId: number, payload: { groupId: number; userIds: number[] }) => api.post<void>('/admin/experiment-tasks/' + taskId + '/assign', payload).then((r) => r.data)

export const listAdminUsers = (params?: { keyword?: string; role?: 'ALL' | 'STUDENT' | 'ADMIN'; enabled?: boolean; page?: number; size?: number }) =>
  api.get<AdminUserPage>('/admin/users', { params }).then((r) => r.data)

export const createAdminUser = (payload: { username: string; password: string; displayName: string; role: 'STUDENT' | 'ADMIN'; enabled?: boolean }) =>
  api.post<AdminUserSummary>('/admin/users', payload).then((r) => r.data)

export const updateAdminUser = (id: number, payload: { displayName: string; role: 'STUDENT' | 'ADMIN' }) =>
  api.put<AdminUserSummary>(`/admin/users/${id}`, payload).then((r) => r.data)

export const setAdminUserEnabled = (id: number, enabled: boolean) =>
  api.put<AdminUserSummary>(`/admin/users/${id}/enabled`, { enabled }).then((r) => r.data)

export const resetAdminUserPassword = (id: number, newPassword: string) =>
  api.put<void>(`/admin/users/${id}/password`, { newPassword }).then((r) => r.data)

export const revokeAdminUserTokens = (id: number) =>
  api.post<{ revokedCount: number }>(`/admin/users/${id}/revoke-tokens`).then((r) => r.data)

export const getAdminUserProfile = (id: number) =>
  api.get<AdminUserProfile>(`/admin/users/${id}/profile`).then((r) => r.data)

export const adminCoverageAudit = (courseId?: number) =>
  api.get<CoverageAuditReport>('/admin/content/coverage-audit', { params: courseId ? { courseId } : undefined }).then((r) => r.data)
