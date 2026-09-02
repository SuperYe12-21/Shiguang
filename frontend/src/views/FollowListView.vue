<template>
  <div class="fl" :class="{ dark: !isPc }">
    <header class="fl-top">
      <button class="fl-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        <span>返回</span>
      </button>
      <div class="fl-title-wrap">
        <h1 class="fl-title">{{ ownerName }}的{{ isFollowers ? '粉丝' : '关注' }}</h1>
        <span class="fl-count">{{ totalCount }} 人</span>
      </div>
      <div class="fl-top-space"></div>
    </header>

    <main class="fl-list">
      <div v-if="loading && !items.length" class="fl-state">加载中…</div>
      <div v-else-if="!items.length" class="fl-state">{{ isFollowers ? '还没有粉丝' : '还没有关注任何人' }}</div>

      <div v-for="u in items" :key="u.id" class="fl-item" @click="goUser(u)">
        <img class="fl-avatar" :src="u.avatarUrl || fallbackAvatar(u)" alt="头像" loading="lazy" />
        <div class="fl-info">
          <div class="fl-name-row">
            <span class="fl-name">{{ u.nickname || '拾光用户' }}</span>
            <span v-if="u.id === auth.userId" class="fl-me">我</span>
          </div>
          <p class="fl-bio">{{ u.bio || '这个人很懒，什么都没写～' }}</p>
        </div>
        <button
          v-if="auth.isLoggedIn && u.id !== auth.userId"
          class="fl-btn"
          :class="{ on: u.followedByMe }"
          @click.stop="toggleFollow(u)"
        >{{ u.followedByMe ? '已关注' : '+ 关注' }}</button>
      </div>

      <div class="fl-more">
        <span v-if="loadingMore">加载中…</span>
        <span v-else-if="items.length && !hasMore">— 没有更多了 —</span>
      </div>
    </main>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchProfile, fetchUserFollowers, fetchUserFollowing, followUser, unfollowUser } from '../api/user'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const targetId = Number(route.params.id)
const isFollowers = route.path.endsWith('/followers')

const isPc = ref(window.innerWidth >= 768)
const ownerName = ref('')
const totalCount = ref(0)
const items = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
let scrollHandler = null

function fallbackAvatar(u) {
  const name = u.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((u.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

async function loadFirst() {
  loading.value = true
  try {
    const [profile, data] = await Promise.all([
      fetchProfile(targetId),
      isFollowers ? fetchUserFollowers(targetId, '', 20) : fetchUserFollowing(targetId, '', 20)
    ])
    ownerName.value = profile.nickname || '拾光用户'
    totalCount.value = isFollowers ? (profile.followerCount ?? 0) : (profile.followingCount ?? 0)
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
    const data = isFollowers
      ? await fetchUserFollowers(targetId, cursor.value, 20)
      : await fetchUserFollowing(targetId, cursor.value, 20)
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

async function toggleFollow(u) {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  try {
    const data = u.followedByMe ? await unfollowUser(u.id) : await followUser(u.id)
    u.followedByMe = data.following
  } catch (e) {
    // 错误已提示
  }
}

function goUser(u) {
  if (u.id === auth.userId) {
    router.push('/me')
  } else {
    router.push('/user/' + u.id)
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/user/' + targetId)
  }
}

function syncIsPc() {
  isPc.value = window.innerWidth >= 768
}

onMounted(async () => {
  await loadFirst()
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
.fl {
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

.fl.dark {
  --bg: #0b0b0e;
  --card: rgba(255, 255, 255, 0.055);
  --text: #f5f2ee;
  --text-2: rgba(245, 242, 238, 0.5);
  --line: rgba(255, 255, 255, 0.09);
  --btn-bg: rgba(255, 255, 255, 0.08);
  padding-bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom) + 16px);
}

.fl-top {
  max-width: 720px;
  margin: 0 auto;
  padding: 26px 18px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.fl.dark .fl-top {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 14px 16px;
  background: rgba(11, 11, 14, 0.88);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--line);
}

.fl-back {
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

.fl-back:hover {
  border-color: rgba(255, 92, 92, 0.45);
  color: #ff5c5c;
}

.fl-title-wrap {
  flex: 1;
  text-align: center;
  min-width: 0;
}

.fl-title {
  font-size: 19px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fl-count {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-2);
}

.fl-top-space {
  width: 76px;
  flex: none;
}

.fl-list {
  max-width: 720px;
  margin: 0 auto;
  padding: 6px 18px 70px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fl-item {
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

.fl:not(.dark) .fl-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 26px rgba(38, 34, 31, 0.09);
  border-color: rgba(255, 92, 92, 0.22);
}

.fl.dark .fl-item:hover {
  background: rgba(255, 255, 255, 0.09);
}

.fl-avatar {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
  background: var(--card);
  border: 2px solid rgba(255, 92, 92, 0.25);
}

.fl-info {
  flex: 1;
  min-width: 0;
}

.fl-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fl-name {
  font-size: 15px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fl-me {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 92, 92, 0.12);
  color: #ff5c5c;
  font-size: 11px;
  font-weight: 600;
  flex: none;
}

.fl-bio {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fl-btn {
  height: 36px;
  padding: 0 20px;
  border-radius: 999px;
  border: none;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  flex: none;
  font-family: inherit;
  transition: all 0.18s;
  background: #ff5c5c;
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 92, 92, 0.3);
}

.fl-btn:hover {
  background: #e84b4b;
}

.fl-btn.on {
  background: var(--btn-bg);
  color: var(--text);
  border: 1px solid var(--line);
  box-shadow: none;
}

.fl-btn.on:hover {
  border-color: rgba(255, 92, 92, 0.5);
  color: #ff5c5c;
}

.fl-state {
  padding: 90px 0;
  text-align: center;
  color: var(--text-2);
  font-size: 14px;
}

.fl-more {
  padding: 18px 0 6px;
  text-align: center;
  color: var(--text-2);
  font-size: 13px;
}
</style>
