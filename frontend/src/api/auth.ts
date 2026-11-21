import { api } from './http'

export const register = (payload: { username: string; password: string; displayName: string }) => api.post('/auth/register', payload).then((r) => r.data)
export const login = (payload: { username: string; password: string }) => api.post('/auth/login', payload).then((r) => r.data)
export const logout = (refreshToken: string) => api.post('/auth/logout', { refreshToken }).then((r) => r.data)
export const currentUser = () => api.get('/auth/me').then((r) => r.data)
