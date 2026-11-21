import { api } from './http'

export const recommendations = () => api.get('/recommendations').then((r) => r.data)
