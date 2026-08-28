import http from './http'

export const sendSmsCode = (phone) => http.post('/auth/sms-code', { phone })

export const login = (phone, code) => http.post('/auth/login', { phone, code })