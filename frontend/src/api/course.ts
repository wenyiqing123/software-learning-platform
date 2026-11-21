import { api } from './http'

export const overview = () => api.get('/dashboard/overview').then((r) => r.data)
export const courses = () => api.get('/courses').then((r) => r.data)
