import axios from 'axios'
import http from './http'

const refreshHttp = axios.create({ baseURL: '/api', timeout: 15000 })

export const sendSmsCode = (phone) => http.post('/auth/sms-code', { phone }, { skipAuthRefresh: true })

export const login = (phone, code) => http.post('/auth/login', { phone, code }, { skipAuthRefresh: true })

export async function refresh(refreshToken) {
  const response = await refreshHttp.post('/auth/refresh', { refreshToken })
  if (response.data?.code !== 0) {
    const error = new Error(response.data?.message || '登录续期失败，请稍后重试')
    error.response = response
    throw error
  }
  return response.data.data
}
