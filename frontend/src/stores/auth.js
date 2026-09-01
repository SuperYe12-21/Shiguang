import { defineStore } from 'pinia'
import { login as apiLogin, sendSmsCode as apiSendCode } from '../api/auth'
import { fetchMe } from '../api/user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('sg_token') || '',
    user: JSON.parse(localStorage.getItem('sg_user') || 'null')
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
      this.token = data.accessToken
      this.user = data.user || null
      localStorage.setItem('sg_token', data.accessToken)
      if (data.user) localStorage.setItem('sg_user', JSON.stringify(data.user))
      return data
    },
    async ensureUser() {
      if (!this.token || this.user) return
      try {
        this.user = await fetchMe()
        localStorage.setItem('sg_user', JSON.stringify(this.user))
      } catch (e) {
        // 网络异常静默，下次进入再补
      }
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('sg_token')
      localStorage.removeItem('sg_user')
    }
  }
})