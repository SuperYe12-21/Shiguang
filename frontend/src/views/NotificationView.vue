<template>
  <div class="nv" :class="{ dark: !isPc }">
    <header class="nv-top">
      <button class="nv-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        <span>返回</span>
      </button>
      <h1 class="nv-title">消息</h1>
      <div class="nv-top-space"></div>
    </header>

    <nav class="nv-tabs">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        class="nv-tab"
        :class="{ on: tab.key === category }"
        @click="switchTab(tab.key)"
      >
        <span>{{ tab.label }}</span>
        <UnreadBadge :count="notification.unread[tab.key]" />
      </button>
    </nav>

    <main class="nv-list">
      <div v-if="loading && !items.length" class="nv-state">加载中…</div>
      <div v-else-if="!items.length" class="nv-state">{{ emptyText }}</div>

      <div
        v-for="n in items"
        :key="n.id"
        class="nv-item"
        :class="{ unread: !n.read }"
        @click="openItem(n)"
      >
        <img class="nv-avatar" :src="avatarOf(n)" alt="头像" loading="lazy" />
        <div class="nv-body">
          <p class="nv-text">
            <span class="nv-name">{{ namesOf(n) }}</span>&nbsp;{{ actionOf(n) }}
          </p>
          <p v-if="n.content" class="nv-content">{{ n.content }}</p>
          <span class="nv-time">{{ relTime(n.updatedAt) }}</span>
        </div>
        <img v-if="n.postCoverUrl" class="nv-thumb" :src="n.postCoverUrl" alt="" loading="lazy" />
      </div>

      <div class="nv-more">
        <span v-if="loadingMore">加载中…</span>
        <span v-else-if="items.length && !hasMore">— 没有更多了 —</span>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchNotifications, markNotificationsRead } from '../api/notifications'
import { useNotificationStore } from '../stores/notification'
import UnreadBadge from '../components/UnreadBadge.vue'

const TABS = [
  { key: 'like', label: '赞与收藏' },
  { key: 'comment', label: '评论' },
  { key: 'follow', label: '新增关注' }
]

const EMPTY_TEXT = {
  like: '还没有新的赞与收藏',
  comment: '还没有新的评论',
  follow: '还没有新的关注'
}

const route = useRoute()
const router = useRouter()
const notification = useNotificationStore()

const isPc = ref(window.innerWidth >= 768)
const category = ref(TABS.some((t) => t.key === route.query.category) ? route.query.category : 'like')
const items = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
let scrollHandler = null

const emptyText = computed(() => EMPTY_TEXT[category.value] || '还没有消息')

function avatarOf(n) {
  if (n.actor?.avatarUrl) return n.actor.avatarUrl
  const id = n.actor?.id || 0
  const ch = (n.actor?.nickname || '拾').charAt(0)
  const hue = (id * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

/** 折叠行最多给三个昵称，其余用「等 N 人」 */
function namesOf(n) {
  const actors = n.actors?.length ? n.actors : n.actor ? [n.actor] : []
  const names = actors.map((a) => a.nickname || '拾光用户')
  const count = n.actorCount || names.length
  if (!names.length) return '有人'
  if (count > names.length) {
    return `${names.join('、')} 等 ${count} 人`
  }
  return names.join('、')
}

function actionOf(n) {
  switch (n.type) {
    case 'LIKE_POST':
      return '赞了你的作品'
    case 'LIKE_COMMENT':
      return '赞了你的评论'
    case 'COMMENT_POST':
      return '评论了你的作品'
    case 'REPLY_COMMENT':
      return '回复了你的评论'
    case 'FOLLOW':
      return '关注了你'
    default:
      return '与你互动'
  }
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
    const data = await fetchNotifications(category.value, '', 20)
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
  if (loadingMore.value || !hasMore.value || loading.value) return
  loadingMore.value = true
  try {
    const data = await fetchNotifications(category.value, cursor.value, 20)
    const seen = new Set(items.value.map((x) => x.id))
    for (const item of data.items || []) {
      if (!seen.has(item.id)) {
        items.value.push(item)
        seen.add(item.id)
      }
    }
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
  } catch (e) {
    // 静默
  } finally {
    loadingMore.value = false
  }
}

/** 列表拉完再标记已读：先刷新红点，用户不会看到「已读行还挂着未读徽标」 */
async function markRead() {
  const current = category.value
  try {
    await markNotificationsRead(current)
  } catch (e) {
    return
  }
  notification.clear(current)
  notification.refresh()
}

async function switchTab(key) {
  if (key === category.value) return
  category.value = key
  items.value = []
  cursor.value = null
  hasMore.value = true
  window.scrollTo({ top: 0 })
  const query = { ...route.query }
  if (key === 'like') delete query.category
  else query.category = key
  router.replace({ path: '/notifications', query })
  await loadFirst()
  markRead()
}

function openItem(n) {
  if (n.type === 'FOLLOW') {
    const id = n.actor?.id
    if (id) router.push('/user/' + id)
    return
  }
  if (!n.postId) return
  // 进单作品页而不是首页流：看完点返回就回到消息页
  const query = { comment: 1 }
  if (n.commentId) {
    query.rootId = n.rootId || n.commentId
    query.commentId = n.commentId
    query.highlight = 1
  }
  router.push({ path: '/post/' + n.postId, query })
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
  markRead()
  scrollHandler = () => {
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 400) {
      loadMore()
    }
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })
  window.addEventListener('resize', syncIsPc)
})

onBeforeUnmount(() => {
  if (scrollHandler) window.removeEventListener('scroll', scrollHandler)
  window.removeEventListener('resize', syncIsPc)
})
</script>

<style scoped>
.nv {
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

.nv.dark {
  --bg: #0b0b0e;
  --card: rgba(255, 255, 255, 0.055);
  --text: #f5f2ee;
  --text-2: rgba(245, 242, 238, 0.5);
  --line: rgba(255, 255, 255, 0.09);
  --btn-bg: rgba(255, 255, 255, 0.08);
  padding-bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom) + 16px);
}

.nv-top {
  max-width: 720px;
  margin: 0 auto;
  padding: 26px 18px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.nv.dark .nv-top {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 14px 16px;
  background: rgba(11, 11, 14, 0.88);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--line);
}

.nv-back {
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

.nv-back:hover {
  border-color: rgba(255, 92, 92, 0.45);
  color: #ff5c5c;
}

.nv-title {
  flex: 1;
  text-align: center;
  font-size: 19px;
  font-weight: 800;
}

.nv-top-space {
  width: 76px;
  flex: none;
}

.nv-tabs {
  max-width: 720px;
  margin: 0 auto;
  padding: 0 18px 8px;
  display: flex;
  gap: 8px;
}

.nv.dark .nv-tabs {
  position: sticky;
  top: 67px;
  z-index: 10;
  padding: 10px 16px;
  background: rgba(11, 11, 14, 0.88);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}

.nv-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--text-2);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s;
  font-family: inherit;
}

.nv-tab:hover {
  color: var(--text);
  border-color: rgba(255, 92, 92, 0.35);
}

.nv-tab.on {
  background: rgba(255, 92, 92, 0.14);
  border-color: rgba(255, 92, 92, 0.45);
  color: #ff5c5c;
}

.nv-list {
  max-width: 720px;
  margin: 0 auto;
  padding: 6px 18px 70px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nv-item {
  position: relative;
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

.nv:not(.dark) .nv-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 26px rgba(38, 34, 31, 0.09);
  border-color: rgba(255, 92, 92, 0.22);
}

.nv.dark .nv-item:hover {
  background: rgba(255, 255, 255, 0.09);
}

.nv-item.unread::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 50%;
  width: 6px;
  height: 6px;
  margin-top: -3px;
  border-radius: 50%;
  background: #ff5c5c;
}

.nv-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
  background: var(--btn-bg);
  border: 2px solid rgba(255, 92, 92, 0.25);
}

.nv-body {
  flex: 1;
  min-width: 0;
}

.nv-text {
  font-size: 14px;
  line-height: 1.45;
  color: var(--text-2);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.nv-name {
  color: var(--text);
  font-weight: 700;
}

.nv-content {
  margin-top: 4px;
  padding: 6px 10px;
  border-radius: 10px;
  background: rgba(127, 127, 127, 0.12);
  font-size: 13px;
  color: var(--text-2);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.nv-time {
  display: block;
  margin-top: 5px;
  font-size: 12px;
  color: var(--text-2);
  opacity: 0.8;
}

.nv-thumb {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  object-fit: cover;
  flex: none;
  background: var(--btn-bg);
}

.nv-state {
  padding: 90px 0;
  text-align: center;
  color: var(--text-2);
  font-size: 14px;
}

.nv-more {
  padding: 18px 0 6px;
  text-align: center;
  color: var(--text-2);
  font-size: 13px;
}
</style>
