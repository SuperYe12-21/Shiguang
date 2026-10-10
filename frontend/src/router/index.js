import { createRouter, createWebHistory } from 'vue-router'
import { handleOverlayPop, syncOverlayLocation } from '../utils/overlayHistory'

const routes = [
  { path: '/', redirect: '/feed' },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/feed', name: 'feed', component: () => import('../views/FeedView.vue') },
  { path: '/post/:id', name: 'post', component: () => import('../views/FeedView.vue') },
  { path: '/publish', name: 'publish', component: () => import('../views/PublishView.vue'), meta: { requiresAuth: true } },
  { path: '/me', name: 'me', component: () => import('../views/ProfileView.vue'), meta: { requiresAuth: true } },
  { path: '/notifications', name: 'notifications', component: () => import('../views/NotificationView.vue'), meta: { requiresAuth: true } },
  { path: '/messages', name: 'messages', component: () => import('../views/MessagesView.vue'), meta: { requiresAuth: true } },
  { path: '/chat/:userId', name: 'chat', component: () => import('../views/ChatView.vue'), meta: { requiresAuth: true } },
  { path: '/settings', name: 'settings', component: () => import('../views/SettingsView.vue'), meta: { requiresAuth: true } },
  { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { requiresAuth: true } },
  { path: '/history', name: 'history', component: () => import('../views/HistoryView.vue'), meta: { requiresAuth: true } },
  { path: '/search', name: 'search', component: () => import('../views/SearchView.vue') },
  { path: '/friends', name: 'friends', component: () => import('../views/FriendsView.vue'), meta: { requiresAuth: true } },
  { path: '/friends/feed', name: 'friends-feed', component: () => import('../views/FeedView.vue'), meta: { requiresAuth: true } },
  { path: '/user/:id', name: 'user', component: () => import('../views/ProfileView.vue') },
  { path: '/user/:id/followers', name: 'user-followers', component: () => import('../views/FollowListView.vue') },
  { path: '/user/:id/following', name: 'user-following', component: () => import('../views/FollowListView.vue') }
  ,
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('sg_token')
  if (to.meta.requiresAuth && !token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && token) {
    return { name: 'feed' }
  }
  return true
})

// 给每个站内历史条目打标记（首页"返回即退出"逻辑靠它识别站内历史边界）
router.afterEach((to, from) => {
  const s = window.history.state
  if (s && typeof s === 'object' && !s.sgIn) {
    window.history.replaceState({ ...s, sgIn: 1 }, '')
  }
  // 同 URL 的返回（面板关层、连退经过同 URL 条目）也会触发 afterEach，
  // 这时不能覆盖遮罩层模块记录的"当前所在条目"，否则会吞掉返回关面板
  if (to.fullPath !== from.fullPath) syncOverlayLocation()
})

// 首页"返回即退出"：只在纯首页（/feed）按返回键时，连续后退跳过全部站内历史，
// 直接退回进入拾光前的页面（微信内表现为直接关闭页面回到聊天）；
// 其他页面保持正常的"返回上一页"行为。监听器放全局，避免页面卸载打断连退。
let exitChain = false
let exitTimer = null
function armExitTimer() {
  if (exitTimer) clearTimeout(exitTimer)
  exitTimer = setTimeout(() => {
    exitChain = false
  }, 1500)
}
window.addEventListener('popstate', () => {
  // 评论/更多/分享等面板占的历史条目：返回键优先关面板
  if (handleOverlayPop()) return
  const st = window.history.state
  const inStation = !!(st && typeof st === 'object' && st.sgIn)
  if (!exitChain) {
    // 触发条件：从纯首页按返回（目标条目的 forward 即"离开前的页面"，由 vue-router 维护，
    // 不受导航完成时序影响），且退到的上一条仍在站内
    const fromHome = !!(st && typeof st === 'object' && st.forward === '/feed')
    if (fromHome && inStation) {
      exitChain = true
      window.history.back()
      armExitTimer()
    }
    return
  }
  // 连退中：还在站内就继续后退；退到站外时浏览器会直接跨页导航，无需处理
  if (inStation) {
    window.history.back()
    armExitTimer()
  } else {
    exitChain = false
    if (exitTimer) clearTimeout(exitTimer)
  }
})

export default router
