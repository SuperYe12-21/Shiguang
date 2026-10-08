import { defineStore } from 'pinia'
import { fetchUnread } from '../api/notifications'
import { fetchFriendsUnread, markFriendsSeen } from '../api/friends'

const POLL_INTERVAL = 30000

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unread: { total: 0, like: 0, comment: 0, follow: 0 },
    /** 朋友页红点：有比上次查看更新的好友作品 */
    friendsUnread: false,
    timer: null,
    listening: false
  }),
  actions: {
    async refresh() {
      if (!localStorage.getItem('sg_token')) return
      this.refreshFriendsUnread()
      try {
        const data = await fetchUnread()
        this.unread = {
          total: data?.total || 0,
          like: data?.like || 0,
          comment: data?.comment || 0,
          follow: data?.follow || 0
        }
      } catch (e) {
        // 轮询失败静默，等下一次
      }
    },
    async refreshFriendsUnread() {
      try {
        const data = await fetchFriendsUnread()
        this.friendsUnread = !!data?.unread
      } catch (e) {
        // 静默，等下一次
      }
    },
    /** 进入朋友页 / 朋友动态后清红点：把"上次查看的好友最新作品"写回后端 */
    async clearFriendsDot() {
      if (!this.friendsUnread) return
      try {
        await markFriendsSeen()
      } catch (e) {
        return
      }
      this.friendsUnread = false
    },
    /** 本地先行递减，避免标记已读后红点还要等一轮轮询 */
    clear(category) {
      if (this.unread[category] == null) return
      this.unread.total = Math.max(0, this.unread.total - this.unread[category])
      this.unread[category] = 0
    },
    start() {
      this.stop()
      this.refresh()
      this.timer = setInterval(() => this.refresh(), POLL_INTERVAL)
      if (!this.listening) {
        document.addEventListener('visibilitychange', this.onVisible)
        this.listening = true
      }
    },
    onVisible() {
      if (document.visibilityState === 'visible') {
        this.refresh()
      }
    },
    stop() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
      if (this.listening) {
        document.removeEventListener('visibilitychange', this.onVisible)
        this.listening = false
      }
      this.unread = { total: 0, like: 0, comment: 0, follow: 0 }
      this.friendsUnread = false
    }
  }
})
