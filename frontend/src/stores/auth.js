import { defineStore } from 'pinia'
import { login as apiLogin, refresh as apiRefresh, sendSmsCode as apiSendCode } from '../api/auth'
import { fetchMe } from '../api/user'

let refreshInFlight = null

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('sg_token') || '',
    refreshToken: localStorage.getItem('sg_refresh_token') || '',
    user: JSON.parse(localStorage.getItem('sg_user') || 'null'),
    sessionVersion: 0
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    userId: (state) => (state.user && state.user.id) || null
  },
  actions: {
    async sendCode(phone) {
      return apiSendCode(phone)
    },
    async login(phone, code) {
      const data = await apiLogin(phone, code)
      this.setSession(data)
      this.sessionVersion += 1
      return data
    },
    setSession(data) {
      if (!data?.accessToken || !data?.refreshToken || !data?.user) {
        throw new Error('登录响应异常，请稍后重试')
      }
      localStorage.setItem('sg_token', data.accessToken)
      localStorage.setItem('sg_refresh_token', data.refreshToken)
      localStorage.setItem('sg_user', JSON.stringify(data.user))
      this.token = data.accessToken
      this.refreshToken = data.refreshToken
      this.user = data.user
    },
    async refreshSession() {
      if (!this.refreshToken) {
        this.logout()
        const error = new Error('登录已过期，请重新登录')
        error.invalidSession = true
        throw error
      }
      if (refreshInFlight?.store === this && refreshInFlight.version === this.sessionVersion) {
        return refreshInFlight.promise
      }
      const pending = { store: this, version: this.sessionVersion }
      pending.promise = (async () => {
        try {
          const data = await apiRefresh(this.refreshToken)
          if (this.sessionVersion !== pending.version) throw new Error('登录状态已改变')
          this.setSession(data)
          return data
        } catch (error) {
          if (this.sessionVersion === pending.version && (
            [400, 401, 403].includes(error.response?.status) ||
            [400, 401, 403].includes(error.response?.data?.code)
          )) {
            this.logout()
            error.invalidSession = true
          }
          throw error
        } finally {
          if (refreshInFlight === pending) refreshInFlight = null
        }
      })()
      refreshInFlight = pending
      return pending.promise
    },
    async ensureUser() {
      if (!this.token || this.user) return
      const version = this.sessionVersion
      try {
        const user = await fetchMe()
        if (this.sessionVersion !== version) return
        this.user = user
        localStorage.setItem('sg_user', JSON.stringify(this.user))
      } catch (e) {
        // 网络异常静默，下次进入再补
      }
    },
    logout() {
      this.sessionVersion += 1
      this.token = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem('sg_token')
      localStorage.removeItem('sg_refresh_token')
      localStorage.removeItem('sg_user')
    }
  }
})
