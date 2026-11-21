import { api } from './http'
import type { AiDependencySummary, SystemStatus } from '../domain/systemStatus'

export const dependencies = () => api.get<AiDependencySummary>('/ai/dependencies').then((r) => r.data)
export const systemStatus = () => api.get<SystemStatus>('/system/status').then((r) => r.data)
