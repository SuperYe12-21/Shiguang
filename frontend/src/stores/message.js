import { defineStore } from 'pinia'
import { fetchMessageUnread } from '../api/messages'

const HEARTBEAT_INTERVAL = 30000
const RECONNECT_DELAY = 3000
const POLL_INTERVAL = 30000

// WebSocket 实例与定时器不放进 state：被 Vue 响应式包装后会出问题
let socket = null
let heartbeatTimer = null
let reconnectTimer = null
let pollTimer = null
let listening = false
let visHandler = null
const listeners = new Set()

export const useMessageStore = defineStore('message', {
  state: () => ({
    unread: 0,
    connected: false
  }),
  actions: {
    /** 未读以接口为准（轮询兜底也走这里） */
    async refresh() {
      if (!localStorage.getItem('sg_token')) return
      try {
        const data = await fetchMessageUnread()
        this.unread = data?.messages || 0
      } catch (e) {
        // 静默，等下一次
      }
    },
    start() {
      this.stop()
      this.refresh()
      this.connect()
      this.startPolling()
    },
    /** 长连接失效时（例如反向代理没放行 /ws）兜底，30 秒对一次未读数 */
    startPolling() {
      this.stopPolling()
      pollTimer = setInterval(() => this.refresh(), POLL_INTERVAL)
      visHandler = () => {
        if (document.visibilityState === 'visible') {
          this.refresh()
        }
      }
      document.addEventListener('visibilitychange', visHandler)
      listening = true
    },
    stopPolling() {
      if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
      }
      if (listening && visHandler) {
        document.removeEventListener('visibilitychange', visHandler)
        listening = false
        visHandler = null
      }
    },
    connect() {
      const token = localStorage.getItem('sg_token')
      if (!token || socket) return
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
      try {
        socket = new WebSocket(`${proto}//${location.host}/ws?token=${encodeURIComponent(token)}`)
      } catch (e) {
        socket = null
        this.scheduleReconnect()
        return
      }
      socket.onopen = () => {
        this.connected = true
        this.startHeartbeat()
      }
      socket.onmessage = (event) => {
        let payload = null
        try {
          payload = JSON.parse(event.data)
        } catch (e) {
          return
        }
        this.handle(payload)
      }
      socket.onclose = () => {
        socket = null
        this.connected = false
        this.stopHeartbeat()
        this.scheduleReconnect()
      }
    },
    handle(payload) {
      if (!payload || !payload.type) return
      if (payload.type === 'message') {
        for (const fn of listeners) {
          try {
            // 第二个参数是发送者资料（后端随推送一起下发，用于新消息提示）
            fn(payload.data || {}, payload.peer || null)
          } catch (e) {
            // 单个订阅者异常不影响其他
          }
        }
      } else if (payload.type === 'unread') {
        this.unread = payload.data?.messages || 0
      }
    },
    /** 订阅新私信事件（聊天页 / 会话列表用），返回取消函数 */
    subscribe(fn) {
      listeners.add(fn)
      return () => listeners.delete(fn)
    },
    startHeartbeat() {
      this.stopHeartbeat()
      heartbeatTimer = setInterval(() => {
        if (socket && socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: 'ping' }))
        }
      }, HEARTBEAT_INTERVAL)
    },
    stopHeartbeat() {
      if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
        heartbeatTimer = null
      }
    },
    scheduleReconnect() {
      if (reconnectTimer || !localStorage.getItem('sg_token')) return
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        this.connect()
      }, RECONNECT_DELAY)
    },
    stop() {
      this.stopHeartbeat()
      this.stopPolling()
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
      if (socket) {
        const ws = socket
        socket = null
        ws.onclose = null
        try {
          ws.close()
        } catch (e) {
          // 忽略
        }
      }
      this.connected = false
      this.unread = 0
    }
  }
})
