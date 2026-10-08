<template>
  <div class="sp">
    <div class="sp-shell">
      <header class="sp-head">
        <button class="sp-back" aria-label="返回" @click="goBack">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        </button>
        <div class="sp-box">
          <svg class="sp-ico" viewBox="0 0 24 24" width="17" height="17" fill="currentColor"><path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z"/></svg>
          <input
            ref="inputEl"
            v-model="keyword"
            class="sp-input"
            type="text"
            inputmode="search"
            enterkeyhint="search"
            placeholder="搜索用户 / 作品"
            maxlength="50"
            @input="onInput"
            @keydown.enter.prevent="onEnter"
          />
          <button v-if="keyword" class="sp-clear" aria-label="清空" @click="clearKeyword">×</button>
        </div>
      </header>

      <nav class="sp-tabs">
        <button class="sp-tab" :class="{ on: tab === 'users' }" @click="switchTab('users')">用户</button>
        <button class="sp-tab" :class="{ on: tab === 'posts' }" @click="switchTab('posts')">作品</button>
      </nav>

      <main ref="bodyEl" class="sp-body" @scroll.passive="onScroll">
        <div v-if="!activeKeyword" class="sp-state">
          <p>输入关键词，找用户或作品</p>
        </div>
        <template v-else>
          <div v-if="st.loading" class="sp-state">
            <p>搜索中…</p>
          </div>

          <template v-else-if="tab === 'users'">
            <div v-if="!st.items.length" class="sp-state">
              <p>没有找到「{{ activeKeyword }}」相关用户</p>
            </div>
            <div v-for="u in st.items" :key="u.id" class="sp-user" @click="goUser(u)">
              <img class="sp-avatar" :src="u.avatarUrl || fallbackAvatar(u)" alt="头像" loading="lazy" />
              <div class="sp-user-info">
                <div class="sp-user-name">
                  <span class="sp-name-text">{{ u.nickname || '拾光用户' }}</span>
                  <span v-if="u.id === auth.userId" class="sp-me">我</span>
                </div>
                <p class="sp-user-bio">{{ u.bio || '这个人很懒，什么都没写～' }}</p>
              </div>
              <button
                v-if="auth.isLoggedIn && u.id !== auth.userId"
                class="sp-follow"
                :class="{ on: u.followedByMe }"
                @click.stop="toggleFollow(u)"
              >{{ u.followedByMe ? (u.matched ? '互相关注' : '已关注') : '+ 关注' }}</button>
            </div>
          </template>

          <template v-else>
            <div v-if="!st.items.length" class="sp-state">
              <p>没有找到「{{ activeKeyword }}」相关作品</p>
            </div>
            <div v-else class="sp-grid">
              <div v-for="p in st.items" :key="p.id" class="sp-cell" @click="goPost(p)">
                <img class="sp-cover" :src="p.coverUrl || (p.images && p.images[0]) || ''" :alt="p.title || '作品'" loading="lazy" />
                <span v-if="p.type === 'VIDEO'" class="sp-play">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                </span>
                <span class="sp-likes">
                  <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
                  {{ formatCount(p.likeCount) }}
                </span>
              </div>
            </div>
          </template>

          <div v-if="st.items.length" class="sp-more">
            <span v-if="st.loadingMore">加载中…</span>
            <span v-else-if="!st.hasMore">— 没有更多了 —</span>
          </div>
        </template>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchPosts, searchUsers } from '../api/search'
import { followUser, unfollowUser } from '../api/user'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const keyword = ref('')
const tab = ref('users')
const inputEl = ref(null)
const bodyEl = ref(null)

const users = reactive({ items: [], cursor: null, hasMore: false, loading: false, loadingMore: false, loaded: false })
const posts = reactive({ items: [], cursor: null, hasMore: false, loading: false, loadingMore: false, loaded: false })

const st = computed(() => (tab.value === 'users' ? users : posts))
const activeKeyword = computed(() => keyword.value.trim())

const SEARCH_STATE_KEY = 'sg_search_state'
let inputTimer = null

function fallbackAvatar(u) {
  const name = u.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((u.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

function formatCount(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

async function load(reset = false) {
  const s = st.value
  const kw = activeKeyword.value
  if (!kw) return
  if (s.loading || s.loadingMore) return
  if (!reset && !s.hasMore) return
  if (reset) {
    s.items = []
    s.cursor = null
    s.hasMore = true
    s.loaded = false
    s.loading = true
  } else {
    s.loadingMore = true
  }
  try {
    const data = tab.value === 'users'
      ? await searchUsers(kw, s.cursor || '', 20)
      : await searchPosts(kw, s.cursor || '', 12)
    const items = (data && data.items) || []
    s.items = reset ? items : s.items.concat(items)
    s.cursor = (data && data.nextCursor) || null
    s.hasMore = !!(data && data.hasMore)
    s.loaded = true
  } catch (e) {
    if (reset) {
      s.items = []
      s.hasMore = false
    }
  } finally {
    s.loading = false
    s.loadingMore = false
  }
}

function resetStates() {
  for (const s of [users, posts]) {
    s.items = []
    s.cursor = null
    s.hasMore = false
    s.loaded = false
  }
}

function onInput() {
  if (inputTimer) clearTimeout(inputTimer)
  inputTimer = setTimeout(() => {
    inputTimer = null
    resetStates()
    if (activeKeyword.value) load(true)
    saveState()
  }, 350)
}

function onEnter() {
  if (inputTimer) {
    clearTimeout(inputTimer)
    inputTimer = null
  }
  resetStates()
  if (activeKeyword.value) load(true)
  saveState()
}

function clearKeyword() {
  keyword.value = ''
  resetStates()
  saveState()
  inputEl.value?.focus()
}

function switchTab(t) {
  if (tab.value === t) return
  tab.value = t
  saveState()
  const s = st.value
  if (activeKeyword.value && !s.loaded) load(true)
}

function onScroll(e) {
  if (!activeKeyword.value) return
  const el = e.target
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 260) {
    load(false)
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/feed')
}

function goUser(u) {
  router.push(`/user/${u.id}`)
}

function goPost(p) {
  router.push(`/post/${p.id}`)
}

async function toggleFollow(u) {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  try {
    if (u.followedByMe) {
      await unfollowUser(u.id)
      u.followedByMe = false
      u.matched = false
    } else {
      const r = await followUser(u.id)
      u.followedByMe = true
      u.matched = !!(r && r.matched)
    }
  } catch (e) {
    // 错误统一由拦截器提示
  }
}

function saveState() {
  try {
    sessionStorage.setItem(SEARCH_STATE_KEY, JSON.stringify({ keyword: keyword.value, tab: tab.value }))
  } catch (e) {
    // 隐私模式等场景忽略
  }
}

onMounted(() => {
  try {
    const raw = sessionStorage.getItem(SEARCH_STATE_KEY)
    if (raw) {
      const saved = JSON.parse(raw)
      if (saved && typeof saved.keyword === 'string' && saved.keyword.trim()) {
        keyword.value = saved.keyword
        tab.value = saved.tab === 'posts' ? 'posts' : 'users'
        load(true)
      }
    }
  } catch (e) {
    // 忽略
  }
  inputEl.value?.focus()
})

onBeforeUnmount(() => {
  if (inputTimer) clearTimeout(inputTimer)
  saveState()
})
</script>

<style scoped>
.sp {
  position: fixed;
  inset: 0;
  background: var(--sg-bg);
}

.sp-shell {
  max-width: 640px;
  height: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
}

.sp-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  padding-top: calc(10px + env(safe-area-inset-top));
  border-bottom: 1px solid var(--sg-line);
  background: var(--sg-bg);
}

.sp-back {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sg-text);
  flex-shrink: 0;
}

.sp-box {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 12px;
  border-radius: var(--sg-radius-full);
  background: #fff;
  border: 1px solid var(--sg-line);
  color: var(--sg-text-3);
}

.sp-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--sg-text);
}

.sp-clear {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(38, 34, 31, 0.16);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sp-tabs {
  display: flex;
  gap: 24px;
  padding: 8px 20px 0;
  background: var(--sg-bg);
}

.sp-tab {
  position: relative;
  padding: 6px 2px 10px;
  font-size: 14px;
  color: var(--sg-text-2);
}

.sp-tab.on {
  color: var(--sg-text);
  font-weight: 700;
}

.sp-tab.on::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 4px;
  width: 22px;
  height: 3px;
  border-radius: 2px;
  background: var(--sg-primary);
  transform: translateX(-50%);
}

.sp-body {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0 48px;
}

.sp-state {
  text-align: center;
  color: var(--sg-text-3);
  font-size: 13px;
  padding: 64px 24px;
}

.sp-state p {
  margin: 0;
}

.sp-user {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 18px;
  cursor: pointer;
}

.sp-user:active {
  background: rgba(38, 34, 31, 0.045);
}

.sp-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--sg-bg-deep);
  flex-shrink: 0;
}

.sp-user-info {
  flex: 1;
  min-width: 0;
}

.sp-user-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14.5px;
  font-weight: 600;
  color: var(--sg-text);
}

.sp-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp-me {
  flex-shrink: 0;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--sg-primary-soft);
  color: var(--sg-primary);
  font-weight: 500;
}

.sp-user-bio {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--sg-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp-follow {
  flex-shrink: 0;
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--sg-primary);
  background: var(--sg-primary);
  color: #fff;
  font-size: 12.5px;
  transition: all 0.18s ease;
}

.sp-follow.on {
  background: transparent;
  color: var(--sg-text-2);
  border-color: var(--sg-line);
}

.sp-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
  padding: 4px 3px 0;
}

.sp-cell {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: 6px;
  overflow: hidden;
  background: var(--sg-bg-deep);
  cursor: pointer;
}

.sp-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.sp-play {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  color: #fff;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.5));
}

.sp-likes {
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

.sp-likes svg {
  color: #ff5c5c;
}

.sp-more {
  text-align: center;
  color: var(--sg-text-3);
  font-size: 12px;
  padding: 14px 0 4px;
}
</style>
