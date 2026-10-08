<template>
  <div class="fr" :class="{ 'is-pc': isPc }">
    <!-- PC 顶栏 -->
    <nav v-if="isPc" class="fr-pc-top">
      <span class="fr-pc-logo">朋友</span>
      <button class="fr-pc-back" @click="router.push('/feed')">返回首页</button>
      <span class="fr-pc-space"></span>
    </nav>

    <header class="fr-head">
      <div class="fr-head-main">
        <h1 class="fr-title">朋友</h1>
        <span v-if="friendsLoaded" class="fr-count">{{ totalCount }} 位互关好友</span>
      </div>
      <button class="fr-search-btn" aria-label="搜索用户" @click="router.push('/search')">
        <svg viewBox="0 0 24 24" width="19" height="19" fill="currentColor"><path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z"/></svg>
      </button>
    </header>

    <nav class="fr-tabs">
      <button class="fr-tab" :class="{ on: tab === 'friends' }" @click="switchTab('friends')">我的好友</button>
      <button class="fr-tab" :class="{ on: tab === 'feed' }" @click="switchTab('feed')">朋友动态</button>
    </nav>

    <!-- 我的好友 -->
    <main v-if="tab === 'friends'" ref="listEl" class="fr-body" @scroll.passive="onListScroll">
      <div v-if="loading && !items.length" class="fr-state">加载中…</div>

      <div v-else-if="!items.length" class="fr-empty">
        <span class="fr-empty-ico">
          <svg viewBox="0 0 24 24" width="30" height="30" fill="currentColor"><path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/></svg>
        </span>
        <p class="fr-empty-title">还没有互相关注的好友</p>
        <p class="fr-empty-desc">去首页看看别人的作品，互相关注后就会出现在这里</p>
        <button class="fr-empty-btn" @click="router.push('/search')">去找朋友</button>
      </div>

      <template v-else>
        <div v-for="u in items" :key="u.id" class="fr-item" @click="goUser(u)">
          <img class="fr-avatar" :src="u.avatarUrl || fallbackAvatar(u)" alt="头像" loading="lazy" @error="onAvatarError" />
          <div class="fr-info">
            <div class="fr-name-row">
              <span class="fr-name">{{ u.nickname || '拾光用户' }}</span>
              <span class="fr-tag">互相关注</span>
            </div>
            <p class="fr-bio">{{ u.bio || '这个人很懒，什么都没写～' }}</p>
          </div>
          <div class="fr-acts">
            <button class="fr-msg" @click.stop="goChat(u)">私信</button>
            <button class="fr-unfollow" @click.stop="unfollow(u)">取消关注</button>
          </div>
        </div>

        <div class="fr-more">
          <span v-if="loadingMore">加载中…</span>
          <span v-else-if="!hasMore">— 没有更多了 —</span>
        </div>
      </template>
    </main>

    <!-- 朋友动态 -->
    <main v-else ref="feedEl" class="fr-body" @scroll.passive="onFeedScroll">
      <div v-if="feed.loading && !feed.items.length" class="fr-state">加载中…</div>

      <div v-else-if="!feed.items.length" class="fr-empty">
        <span class="fr-empty-ico">
          <svg viewBox="0 0 24 24" width="30" height="30" fill="currentColor"><path d="M4 4h16v12H4z" opacity=".2"/><path d="M20 2H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4l4 4 4-4h4a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z"/></svg>
        </span>
        <p class="fr-empty-title">朋友动态还空着</p>
        <p class="fr-empty-desc">互关好友发布的作品会出现在这里，像首页一样上下滑着看</p>
      </div>

      <template v-else>
        <div class="fr-grid">
          <div v-for="p in feed.items" :key="p.id" class="fr-cell" @click="goPost(p)">
            <img class="fr-cover" :src="p.coverUrl || (p.images && p.images[0]) || ''" :alt="p.title || '作品'" loading="lazy" />
            <span v-if="p.type === 'VIDEO'" class="fr-play">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
            </span>
            <span class="fr-likes">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
              {{ formatCount(p.likeCount) }}
            </span>
            <span class="fr-author">{{ p.author?.nickname || '拾光用户' }}</span>
          </div>
        </div>

        <div class="fr-more">
          <span v-if="feed.loadingMore">加载中…</span>
          <span v-else-if="!feed.hasMore">— 没有更多了 —</span>
        </div>
      </template>
    </main>

    <BottomNav v-if="!isPc" active="friends" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import BottomNav from '../components/mobile/BottomNav.vue'
import { fetchFriends } from '../api/friends'
import { fetchFriendsFeed } from '../api/posts'
import { unfollowUser } from '../api/user'
import { useNotificationStore } from '../stores/notification'

const router = useRouter()
const notification = useNotificationStore()

const isPc = ref(typeof window !== 'undefined' ? window.innerWidth >= 768 : false)
const tab = ref('friends')
const listEl = ref(null)
const feedEl = ref(null)

const items = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const friendsLoaded = ref(false)
const totalCount = ref(0)

const feed = reactive({ items: [], cursor: null, hasMore: true, loading: false, loadingMore: false, loaded: false })

function fallbackAvatar(u) {
  const name = u.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((u.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

function onAvatarError(e) {
  e.target.style.visibility = 'hidden'
}

function formatCount(n) {
  const v = Number(n) || 0
  if (v >= 10000) return (v / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (v >= 1000) return (v / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(v)
}

async function loadFriends(first = false) {
  if (loading.value || (!first && !hasMore.value)) return
  if (first) {
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const data = await fetchFriends(first ? '' : cursor.value, 20)
    const list = data.items || []
    const seen = new Set(items.value.map((x) => x.id))
    for (const u of list) {
      if (!seen.has(u.id)) {
        items.value.push(u)
        seen.add(u.id)
      }
    }
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
    friendsLoaded.value = true
    totalCount.value = items.value.length + (hasMore.value ? 1 : 0)
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadFeed(first = false) {
  if (feed.loading || (!first && (!feed.hasMore || feed.loadingMore))) return
  if (first) {
    feed.loading = true
  } else {
    feed.loadingMore = true
  }
  try {
    const data = await fetchFriendsFeed(first ? '' : feed.cursor, 12)
    const list = data.items || []
    const seen = new Set(feed.items.map((x) => x.id))
    for (const p of list) {
      if (!seen.has(p.id)) {
        feed.items.push(p)
        seen.add(p.id)
      }
    }
    feed.cursor = data.nextCursor || null
    feed.hasMore = !!data.hasMore
    feed.loaded = true
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    feed.loading = false
    feed.loadingMore = false
  }
}

function switchTab(next) {
  // 移动端：朋友动态直接进沉浸式流（和首页一样上下滑），不再用宫格
  if (next === 'feed' && !isPc.value) {
    router.push('/friends/feed')
    return
  }
  if (tab.value === next) return
  tab.value = next
  if (next === 'feed' && !feed.loaded) loadFeed(true)
}

function onListScroll() {
  const el = listEl.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 220) loadFriends(false)
}

function onFeedScroll() {
  const el = feedEl.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 320) loadFeed(false)
}

async function unfollow(u) {
  try {
    await unfollowUser(u.id)
    items.value = items.value.filter((x) => x.id !== u.id)
    totalCount.value = Math.max(0, totalCount.value - 1)
    feed.items = feed.items.filter((p) => p.author?.id !== u.id)
    ElMessage.success('已取消关注')
  } catch (e) {
    // 错误提示由拦截器处理
  }
}

function goUser(u) {
  router.push('/user/' + u.id)
}

function goChat(u) {
  router.push('/chat/' + u.id)
}

function goPost(p) {
  router.push('/post/' + p.id)
}

function syncIsPc() {
  isPc.value = window.innerWidth >= 768
}

onMounted(() => {
  loadFriends(true)
  notification.clearFriendsDot()
  window.addEventListener('resize', syncIsPc)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncIsPc)
})
</script>

<style scoped>
.fr {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: var(--sg-bg);
  color: var(--sg-text);
}

.fr-pc-top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 22px;
  background: #14110f;
  color: #fff;
}

.fr-pc-logo {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 2px;
  background: var(--sg-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.fr-pc-back {
  padding: 6px 14px;
  border-radius: var(--sg-radius-full);
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.fr-pc-back:hover {
  background: rgba(255, 255, 255, 0.24);
}

.fr-pc-space {
  flex: 1;
}

.fr-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px 10px;
  padding-top: calc(14px + env(safe-area-inset-top));
}

.fr-head-main {
  flex: 1;
  min-width: 0;
}

.fr-title {
  margin: 0;
  font-size: 21px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.fr-count {
  font-size: 12px;
  color: var(--sg-text-2);
}

.fr-search-btn {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid var(--sg-line);
  color: var(--sg-text);
  flex-shrink: 0;
}

.fr-tabs {
  display: flex;
  gap: 22px;
  padding: 4px 18px 0;
  border-bottom: 1px solid var(--sg-line);
}

.fr-tab {
  position: relative;
  padding: 8px 2px 12px;
  font-size: 14.5px;
  color: var(--sg-text-2);
}

.fr-tab.on {
  color: var(--sg-text);
  font-weight: 700;
}

.fr-tab.on::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 4px;
  width: 24px;
  height: 3px;
  border-radius: 2px;
  background: var(--sg-primary);
  transform: translateX(-50%);
}

.fr-body {
  flex: 1;
  overflow-y: auto;
  padding-bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom) + 20px);
}

.fr.is-pc .fr-body {
  padding-bottom: 40px;
}

.fr-state {
  text-align: center;
  color: var(--sg-text-3);
  font-size: 13px;
  padding: 56px 24px;
}

.fr-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 62px 32px;
}

.fr-empty-ico {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 66px;
  height: 66px;
  border-radius: 50%;
  background: var(--sg-primary-soft);
  color: var(--sg-primary);
  margin-bottom: 14px;
}

.fr-empty-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.fr-empty-desc {
  margin: 6px 0 18px;
  font-size: 12.5px;
  color: var(--sg-text-2);
  line-height: 1.6;
  max-width: 260px;
}

.fr-empty-btn {
  height: 36px;
  padding: 0 22px;
  border-radius: 999px;
  background: var(--sg-primary);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(232, 75, 75, 0.28);
}

.fr.is-pc .fr-item {
  max-width: 620px;
  margin: 0 auto;
  border-radius: var(--sg-radius);
}

.fr-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  cursor: pointer;
}

.fr-item:active {
  background: rgba(38, 34, 31, 0.04);
}

.fr-avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--sg-bg-deep);
  flex-shrink: 0;
}

.fr-info {
  flex: 1;
  min-width: 0;
}

.fr-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.fr-name {
  font-size: 14.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fr-tag {
  flex-shrink: 0;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--sg-primary-soft);
  color: var(--sg-primary-deep);
  font-weight: 500;
}

.fr-bio {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--sg-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fr-acts {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.fr-msg {
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--sg-primary);
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
}

.fr-unfollow {
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--sg-line);
  color: var(--sg-text-2);
  font-size: 12.5px;
  background: #fff;
}

.fr-more {
  text-align: center;
  color: var(--sg-text-3);
  font-size: 12px;
  padding: 16px 0 6px;
}

.fr-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
  padding: 4px 3px 0;
}

.fr.is-pc .fr-grid {
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  padding: 16px 22px 0;
  max-width: 1180px;
  margin: 0 auto;
}

.fr-cell {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: 6px;
  overflow: hidden;
  background: var(--sg-bg-deep);
  cursor: pointer;
}

.fr.is-pc .fr-cell {
  border-radius: var(--sg-radius);
}

.fr-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.fr-play {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  color: #fff;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.5));
}

.fr-likes {
  position: absolute;
  left: 6px;
  bottom: 6px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #fff;
  font-size: 11px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.55);
}

.fr-likes svg {
  color: #ff5c5c;
}

.fr-author {
  position: absolute;
  right: 6px;
  bottom: 6px;
  max-width: 66%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.55);
}
</style>
