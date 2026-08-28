import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('sg_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('sg_token')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    } else {
      const msg = err.response?.data?.message || '网络异常，请稍后重试'
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default http