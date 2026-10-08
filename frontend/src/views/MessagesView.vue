<template>
  <div class="mv" :class="{ dark: !isPc }">
    <header class="mv-top">
      <button class="mv-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        <span>返回</span>
      </button>
      <h1 class="mv-title">消息</h1>
      <div class="mv-top-space"></div>
    </header>

    <main class="mv-list">
      <!-- 互动消息入口：点击跳转到对应的消息列表 -->
      <nav class="mv-tiles">
        <button v-for="t in TILES" :key="t.key" class="mv-tile" @click="openInteractions(t.key)">
          <span class="mv-tile-ico">
            <svg viewBox="0 0 24 24" fill="currentColor"><path :d="t.icon" /></svg>
            <UnreadBadge :count="notification.unread[t.key]" variant="float" />
          </span>
          <span class="mv-tile-text">{{ t.label }}</span>
        </button>
      </nav>

      <div class="mv-section">
        <span class="mv-section-title">私信</span>
        <span v-if="message.unread > 0" class="mv-section-hint">{{ message.unread > 99 ? '99+' : message.unread }} 条未读</span>
      </div>

      <div v-if="loading && !items.length" class="mv-state">加载中…</div>
      <div v-else-if="!items.length" class="mv-state">
        <p>还没有私信</p>
        <p class="mv-sub">去别人的主页点「私信」，打个招呼吧</p>
      </div>

      <div v-for="c in items" :key="c.id" class="mv-item" @click="openChat(c)">
        <img class="mv-avatar" :src="avatarOf(c.peer)" alt="" />
        <div class="mv-body">
          <p class="mv-name">{{ c.peer?.nickname || '拾光用户' }}</p>
          <p class="mv-preview">{{ previewOf(c) }}</p>
        </div>
        <div class="mv-meta">
          <span class="mv-time">{{ relTime(c.lastMessageAt) }}</span>
          <span v-if="c.unread > 0" class="mv-badge">{{ c.unread > 99 ? '99+' : c.unread }}</span>
        </div>
      </div>

      <div class="mv-more">
        <span v-if="loadingMore">加载中…</span>
        <span v-else-if="items.length && !hasMore">— 没有更多了 —</span>
      </div>
    </main>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchConversations } from '../api/messages'
import { useMessageStore } from '../stores/message'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notification'
import UnreadBadge from '../components/UnreadBadge.vue'

const router = useRouter()
const message = useMessageStore()
const auth = useAuthStore()
const notification = useNotificationStore()

// 互动消息入口（与通知中心的三个分类一一对应），私信列表才是本页主体
const TILES = [
  { key: 'like', label: '赞与收藏', icon: 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z' },
  { key: 'comment', label: '评论', icon: 'M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z' },
  { key: 'follow', label: '新增关注', icon: 'M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm-9-2V7H4v3H1v2h3v3h2v-3h3v-2H6zm9 4c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z' }
]

function openInteractions(key) {
  router.push({ path: '/notifications', query: key === 'like' ? {} : { category: key } })
}

const isPc = ref(window.innerWidth >= 768)
const items = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
let unsubscribe = null
let scrollHandler = null

function avatarOf(peer) {
  if (peer?.avatarUrl) return peer.avatarUrl
  const id = peer?.id || 0
  const ch = (peer?.nickname || '拾').charAt(0)
  const hue = (id * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

function previewOf(c) {
  const mine = c.lastSenderId != null && c.lastSenderId === auth.userId
  return (mine ? '我：' : '') + (c.lastContent || '')
}

function relTime(s) {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  const days = Math.floor(diff / 86400000)
  if (days === 1) return '昨天'
  if (days === 2) return '前天'
  const sameYear = d.getFullYear() === new Date().getFullYear()
  const md = `${d.getMonth() + 1}-${String(d.getDate()).padStart(2, '0')}`
  return sameYear ? md : `${d.getFullYear()}-${md}`
}

async function loadFirst() {
  loading.value = true
  try {
    const data = await fetchConversations('', 20)
    items.value = data.items || []
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
  } catch (e) {
    // 错误已提示
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value || loading.value || !cursor.value) return
  loadingMore.value = true
  try {
    const data = await fetchConversations(cursor.value, 20)
    const seen = new Set(items.value.map((x) => x.id))
    for (const item of data.items || []) {
      if (!seen.has(item.id)) items.value.push(item)
    }
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
  } catch (e) {
    // 静默
  } finally {
    loadingMore.value = false
  }
}

function openChat(c) {
  if (c.peer?.id) router.push('/chat/' + c.peer.id)
}

/** 实时新消息：更新对应会话并置顶；列表里没有就重新拉一页 */
function onIncoming(m) {
  const peerId = m.senderId === auth.userId ? m.receiverId : m.senderId
  const idx = items.value.findIndex((c) => c.peer?.id === peerId)
  if (idx < 0) {
    loadFirst()
    return
  }
  const conv = items.value[idx]
  conv.lastContent = m.content
  conv.lastSenderId = m.senderId
  conv.lastMessageAt = m.createdAt
  if (m.receiverId === auth.userId) {
    conv.unread = (conv.unread || 0) + 1
  }
  items.value.splice(idx, 1)
  items.value.unshift(conv)
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/feed')
  }
}

function syncIsPc() {
  isPc.value = window.innerWidth >= 768
}

onMounted(async () => {
  await loadFirst()
  message.refresh()
  unsubscribe = message.subscribe(onIncoming)
  scrollHandler = () => {
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 400) {
      loadMore()
    }
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })
  window.addEventListener('resize', syncIsPc)
})

onBeforeUnmount(() => {
  if (unsubscribe) unsubscribe()
  if (scrollHandler) window.removeEventListener('scroll', scrollHandler)
  window.removeEventListener('resize', syncIsPc)
})
</script>

<style scoped>
.mv {
  --bg: #f7f7f5;
  --card: #fff;
  --text: #26221f;
  --text-2: #8a837d;
  --line: rgba(38, 34, 31, 0.08);
  --btn-bg: #fff;
  min-height: 100vh;
  background: var(--bg);
  color: var(--text);
  font-family: var(--sg-font);
  -webkit-font-smoothing: antialiased;
}

.mv.dark {
  --bg: #0b0b0e;
  --card: rgba(255, 255, 255, 0.055);
  --text: #f5f2ee;
  --text-2: rgba(245, 242, 238, 0.5);
  --line: rgba(255, 255, 255, 0.09);
  --btn-bg: rgba(255, 255, 255, 0.08);
  padding-bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom) + 16px);
}

.mv-top {
  max-width: 720px;
  margin: 0 auto;
  padding: 26px 18px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.mv.dark .mv-top {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 14px 16px;
  background: rgba(11, 11, 14, 0.88);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--line);
}

.mv-back {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  padding: 0 16px 0 10px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--btn-bg);
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.mv-back:hover {
  border-color: rgba(255, 92, 92, 0.45);
  color: #ff5c5c;
}

.mv-title {
  flex: 1;
  text-align: center;
  font-size: 19px;
  font-weight: 800;
}

.mv-top-space {
  width: 76px;
  flex: none;
}

.mv-list {
  max-width: 720px;
  margin: 0 auto;
  padding: 6px 18px 70px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mv-tiles {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 10px;
}

.mv-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px 14px;
  border-radius: 16px;
  background: var(--card);
  border: 1px solid transparent;
  color: var(--text);
  font-family: inherit;
  cursor: pointer;
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;
}

.mv:not(.dark) .mv-tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 26px rgba(38, 34, 31, 0.09);
  border-color: rgba(255, 92, 92, 0.22);
}

.mv.dark .mv-tile:hover {
  background: rgba(255, 255, 255, 0.09);
}

.mv-tile-ico {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 92, 92, 0.12);
  color: #ff5c5c;
}

.mv-tile-ico svg {
  width: 22px;
  height: 22px;
}

.mv-tile-text {
  font-size: 12.5px;
  font-weight: 600;
}

.mv-section {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 4px 2px;
  font-size: 14px;
  font-weight: 700;
}

.mv-section-hint {
  font-size: 12px;
  font-weight: 600;
  color: #ff5c5c;
}

.mv-state {
  padding: 80px 0;
  text-align: center;
  color: var(--text-2);
  font-size: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mv-sub {
  font-size: 12px;
  opacity: 0.75;
}

.mv-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--card);
  border: 1px solid transparent;
  cursor: pointer;
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;
}

.mv:not(.dark) .mv-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 26px rgba(38, 34, 31, 0.09);
  border-color: rgba(255, 92, 92, 0.22);
}

.mv.dark .mv-item:hover {
  background: rgba(255, 255, 255, 0.09);
}

.mv-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
  background: var(--btn-bg);
  border: 2px solid rgba(255, 92, 92, 0.25);
}

.mv-body {
  flex: 1;
  min-width: 0;
}

.mv-name {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 4px;
}

.mv-preview {
  font-size: 13px;
  color: var(--text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mv-meta {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.mv-time {
  font-size: 12px;
  color: var(--text-2);
}

.mv-badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #ff5c5c;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mv-more {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  font-size: 12px;
}
</style>
