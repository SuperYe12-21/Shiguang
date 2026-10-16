<template>
  <div class="mv">
    <AppSideNav
      v-if="isPc"
      active="message"
      :user="auth.user"
      :unread-count="message.unread + notification.unread.total"
      :friends-unread="notification.friendsUnread"
    />
    <header class="mv-top">
      <button v-if="!isPc" class="mv-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        <span>返回</span>
      </button>
      <h1 class="mv-title">消息</h1>
      <span v-if="isPc" class="mv-top-note">新的连接，就在这里</span>
      <div v-else class="mv-top-space"></div>
    </header>

    <main ref="listEl" class="mv-list" aria-label="私信会话" @scroll.passive="onListScroll">
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

      <button
        v-for="c in items"
        :key="c.id"
        class="mv-item"
        :class="{ selected: isPc && selectedPeerId === c.peer?.id }"
        :aria-pressed="isPc ? selectedPeerId === c.peer?.id : undefined"
        @click="openChat(c)"
      >
        <img class="mv-avatar" :src="avatarOf(c.peer)" alt="" />
        <div class="mv-body">
          <p class="mv-name">{{ c.peer?.nickname || '拾光用户' }}</p>
          <p class="mv-preview">{{ previewOf(c) }}</p>
        </div>
        <div class="mv-meta">
          <span class="mv-time">{{ relTime(c.lastMessageAt) }}</span>
          <span v-if="c.unread > 0" class="mv-badge">{{ c.unread > 99 ? '99+' : c.unread }}</span>
        </div>
      </button>

      <div class="mv-more">
        <span v-if="loadingMore">加载中…</span>
        <span v-else-if="items.length && !hasMore">— 没有更多了 —</span>
      </div>
    </main>
    <section v-if="isPc" class="mv-chat" aria-label="聊天对话">
      <ChatView
        v-if="selectedPeerId"
        :key="selectedPeerId"
        :peer-user-id="selectedPeerId"
        embedded
        @read="onConversationRead"
      />
      <div v-else class="mv-chat-empty">
        <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.2" aria-hidden="true"><path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H7l-5 2 2-5v-5.5a8.5 8.5 0 0 1 17 0Z"/></svg>
        <p>选一段对话，聊聊今天</p>
        <span>从左侧选择会话，继续你们的故事</span>
      </div>
    </section>
    <BottomNav v-else active="message" />
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
import AppSideNav from '../components/AppSideNav.vue'
import BottomNav from '../components/mobile/BottomNav.vue'
import ChatView from './ChatView.vue'

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
const selectedPeerId = ref(null)
const listEl = ref(null)
const items = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
let unsubscribe = null
let disposed = false

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
  if (!c.peer?.id) return
  if (isPc.value) {
    selectedPeerId.value = c.peer.id
  } else {
    router.push('/chat/' + c.peer.id)
  }
}

function onConversationRead(peerId) {
  const conversation = items.value.find((item) => item.peer?.id === peerId)
  if (conversation) conversation.unread = 0
}

function onListScroll() {
  const element = listEl.value
  if (element && element.scrollTop + element.clientHeight >= element.scrollHeight - 400) {
    loadMore()
  }
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
  window.addEventListener('resize', syncIsPc)
  await loadFirst()
  if (disposed) return
  message.refresh()
  unsubscribe = message.subscribe(onIncoming)
})

onBeforeUnmount(() => {
  disposed = true
  if (unsubscribe) unsubscribe()
  window.removeEventListener('resize', syncIsPc)
})
</script>

<style scoped>
.mv {
  --bg: var(--sg-bg, #f4f1e9);
  --card: var(--sg-card, #faf8f2);
  --text: var(--sg-text, #3b4239);
  --text-2: var(--sg-text-2, #99998d);
  --line: var(--sg-line, #e6e3d9);
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  grid-template-rows: 77px minmax(0, 1fr);
  height: 100vh;
  height: 100dvh;
  padding-left: var(--sg-sidebar-w, 188px);
  box-sizing: border-box;
  background: var(--bg);
  color: var(--text);
  font-family: var(--sg-font);
  -webkit-font-smoothing: antialiased;
}

.mv-top {
  grid-column: 1 / -1;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.mv-back {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 36px;
  padding: 0 12px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--card);
  color: var(--text);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.mv-title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
}

.mv-top-note, .mv-top-space {
  color: var(--text-2);
  font-size: 12px;
}

.mv-list {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 24px 18px;
  border-right: 1px solid var(--line);
}

.mv-tiles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 28px;
}

.mv-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 0;
  background: transparent;
  border: 0;
  color: var(--text-2);
  font-family: inherit;
  cursor: pointer;
}

.mv-tile-ico {
  position: relative;
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: var(--sg-primary-soft, #eee4d8);
  color: var(--sg-primary, #b66c4d);
}

.mv-tile:nth-child(2) .mv-tile-ico {
  background: #e5ebe0;
  color: #7b9176;
}

.mv-tile:nth-child(3) .mv-tile-ico {
  background: #e8e5d9;
  color: #999273;
}

.mv-tile-ico svg {
  width: 21px;
  height: 21px;
}

.mv-tile-text {
  font-size: 11px;
}

.mv-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 9px 14px;
  font-size: 12px;
  color: var(--text-2);
}

.mv-section-hint {
  font-size: 10px;
  color: var(--sg-primary, #b66c4d);
}

.mv-state {
  padding: 60px 8px;
  text-align: center;
  color: var(--text-2);
  font-size: 13px;
}

.mv-state p {
  margin: 0 0 10px;
}

.mv-sub {
  font-size: 11px;
  line-height: 1.8;
}

.mv-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 16px 10px;
  border-radius: 6px;
  background: transparent;
  border: 0;
  color: inherit;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.18s;
}

.mv-item:hover, .mv-item.selected {
  background: var(--sg-primary-soft, #eee4d8);
}

.mv-item:focus-visible, .mv-tile:focus-visible, .mv-back:focus-visible {
  outline: 2px solid var(--sg-primary, #b66c4d);
  outline-offset: 2px;
}

.mv-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
  background: var(--card);
}

.mv-body {
  flex: 1;
  min-width: 0;
}

.mv-name {
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 7px;
}

.mv-preview {
  margin: 0;
  font-size: 11px;
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
  gap: 8px;
  align-self: flex-start;
  padding-top: 3px;
}

.mv-time {
  font-size: 10px;
  color: var(--text-2);
}

.mv-badge {
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  box-sizing: border-box;
  border-radius: 999px;
  background: var(--sg-primary, #b66c4d);
  color: #fff;
  font-size: 10px;
  display: grid;
  place-items: center;
}

.mv-more {
  min-height: 40px;
  display: grid;
  place-items: center;
  color: var(--text-2);
  font-size: 11px;
}

.mv-chat {
  min-width: 0;
  min-height: 0;
}

.mv-chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  padding: 24px;
  box-sizing: border-box;
  text-align: center;
  color: var(--text-2);
}

.mv-chat-empty svg {
  color: var(--sg-primary, #b66c4d);
  opacity: 0.6;
}

.mv-chat-empty p {
  margin: 8px 0 0;
  color: var(--text);
  font-size: 16px;
  font-family: serif;
}

.mv-chat-empty span {
  font-size: 12px;
}

@media (max-width: 767px) {
  .mv {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: 62px minmax(0, 1fr);
    padding-left: 0;
    padding-bottom: calc(var(--sg-nav-h, 60px) + env(safe-area-inset-bottom));
    background: var(--card);
  }

  .mv-top {
    padding: 0 16px;
    border-bottom: 1px solid var(--line);
  }

  .mv-title {
    flex: 1;
    text-align: center;
    font-size: 17px;
  }

  .mv-top-space {
    width: 76px;
    flex: none;
  }

  .mv-list {
    padding: 26px 17px 16px;
    border: 0;
  }

  .mv-tiles {
    margin-bottom: 30px;
  }

  .mv-tile-ico {
    width: 49px;
    height: 49px;
  }

  .mv-item {
    padding: 17px 8px;
  }

  .mv-avatar {
    width: 45px;
    height: 45px;
  }

  .mv-preview {
    font-size: 12px;
  }
}
</style>
