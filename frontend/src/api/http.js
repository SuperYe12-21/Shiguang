import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import router from '../router'
import { loginLocation } from '../auth/redirect'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  if (config.skipAuthRefresh) return config
  const auth = useAuthStore()
  if (config.authRetried && config.authSessionVersion !== auth.sessionVersion) {
    return Promise.reject(new Error('登录状态已改变'))
  }
  const token = auth.token
  config.authToken = token
  config.authSessionVersion = auth.sessionVersion
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  } else {
    delete config.headers.Authorization
  }
  return config
})

let loginNavigation = null

async function returnToLogin() {
  if (router.currentRoute.value.name === 'login') return
  if (!loginNavigation) {
    loginNavigation = router.replace(loginLocation(router.currentRoute.value.fullPath, router))
      .finally(() => { loginNavigation = null })
  }
  await loginNavigation
}

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  async (err) => {
    const config = err.config
    if (err.response?.status === 401 && config && !config.skipAuthRefresh) {
      const auth = useAuthStore()
      if (config.authSessionVersion !== auth.sessionVersion) return Promise.reject(err)
      if (config.authRetried) {
        auth.logout()
        await returnToLogin()
        return Promise.reject(err)
      }
      config.authRetried = true
      if (!auth.token || config.authToken === auth.token) {
        try {
          await auth.refreshSession()
        } catch (refreshError) {
          if (refreshError.invalidSession) {
            await returnToLogin()
          } else if (config.authSessionVersion === auth.sessionVersion) {
            ElMessage.error({ message: refreshError.response?.data?.message || '登录续期暂时失败，请稍后重试', grouping: true })
          }
          return Promise.reject(refreshError)
        }
      }
      if (config.authSessionVersion !== auth.sessionVersion) return Promise.reject(err)
      return http(config)
    } else {
      const msg = err.response?.data?.message || '网络异常，请稍后重试'
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default http
