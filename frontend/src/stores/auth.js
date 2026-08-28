import { defineStore } from 'pinia'
import { login as apiLogin, sendSmsCode as apiSendCode } from '../api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('sg_token') || ''
  }),
  getters: {
    isLoggedIn: (state) => !!state.token
  },
  actions: {
    async sendCode(phone) {
      return apiSendCode(phone)
    },
    async login(phone, code) {
      const data = await apiLogin(phone, code)
      this.token = data.accessToken
      localStorage.setItem('sg_token', data.accessToken)
      return data
    },
    logout() {
      this.token = ''
      localStorage.removeItem('sg_token')
    }
  }
})