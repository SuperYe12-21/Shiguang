<template>
  <div class="profile-page">
    <!-- 顶栏 -->
    <header class="pf-top">
      <button class="pf-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        <span v-if="isPc" class="pf-back-text">返回</span>
      </button>
      <span class="pf-top-title">{{ profile.nickname || '个人主页' }}</span>
      <span class="pf-top-spacer" />
    </header>

    <!-- 用户信息 -->
    <section class="pf-head">
      <img class="pf-avatar" :src="profile.avatarUrl || fallbackAvatar" alt="头像" />
      <h2 class="pf-nickname">{{ profile.nickname || '拾光用户' }}</h2>
      <p v-if="profile.bio" class="pf-bio">{{ profile.bio }}</p>
      <p class="pf-join">加入拾光 · {{ formatDate(profile.createdAt) }}</p>
      <div class="pf-stats">
        <div class="pf-stat"><b>{{ profile.postCount ?? 0 }}</b><span>作品</span></div>
        <div class="pf-stat"><b>{{ formatCount(profile.followingCount) }}</b><span>关注</span></div>
        <div class="pf-stat"><b>{{ formatCount(profile.followerCount) }}</b><span>粉丝</span></div>
      </div>
      <div class="pf-actions">
        <template v-if="isMe">
          <button class="pf-btn pf-btn-primary" @click="openEdit">编辑资料</button>
          <button class="pf-btn pf-btn-ghost" @click="logout">退出登录</button>
        </template>
        <template v-else>
          <button v-if="profile.followedByMe" class="pf-btn pf-btn-ghost" @click="toggleFollow">已关注</button>
          <button v-else class="pf-btn pf-btn-primary" @click="toggleFollow">+ 关注</button>
        </template>
      </div>
    </section>

    <!-- 作品网格 -->
    <section class="pf-grid">
      <div v-for="p in posts" :key="p.id" class="pf-cell" @click="openPost(p)">
        <img :src="coverOf(p)" :alt="p.title || '作品'" loading="lazy" />
        <span v-if="p.type === 'VIDEO'" class="pf-cell-mark pf-cell-video">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
        </span>
        <span v-else-if="(p.images || []).length > 1" class="pf-cell-mark pf-cell-multi">{{ p.images.length }}</span>
        <span class="pf-cell-like">
          <svg viewBox="0 0 24 24" width="12" height="12" fill="#ff5c5c"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
          {{ formatCount(p.likeCount) }}
        </span>
      </div>
    </section>
    <div v-if="loadingMore" class="pf-more">加载中…</div>
    <div v-else-if="posts.length && !hasMore" class="pf-more">— 没有更多了 —</div>
    <div v-if="!posts.length && !loading" class="pf-empty">还没有作品，去发布第一条拾光吧</div>

    <!-- 编辑资料弹窗 -->
    <div v-if="editOpen" class="pf-modal" @click.self="editOpen = false">
      <div class="pf-modal-panel">
        <h3 class="pf-modal-title">编辑资料</h3>
        <div class="pf-edit-avatar" @click="pickAvatar">
          <img :src="editAvatar || profile.avatarUrl || fallbackAvatar" alt="头像" />
          <span class="pf-edit-avatar-tip">点击更换头像</span>
        </div>
        <input v-model="editNickname" class="pf-input" maxlength="30" placeholder="昵称" />
        <textarea v-model="editBio" class="pf-input pf-textarea" maxlength="120" rows="3" placeholder="一句话介绍自己"></textarea>
        <input ref="avatarInput" type="file" accept="image/jpeg,image/png,image/webp" hidden @change="onAvatarChange" />
        <div class="pf-modal-actions">
          <button class="pf-btn pf-btn-ghost" @click="editOpen = false">取消</button>
          <button class="pf-btn pf-btn-primary" :disabled="saving" @click="saveProfile">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </div>
    </div>

    <BottomNav v-if="!isPc" :active="isMe ? 'me' : ''" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchMe, fetchProfile, fetchUserPosts, updateMe, followUser, unfollowUser } from '../api/user'
import { presignUpload } from '../api/posts'
import { useAuthStore } from '../stores/auth'
import BottomNav from '../components/mobile/BottomNav.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isPc = computed(() => window.innerWidth >= 768)
const profile = ref({})
const posts = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const nextCursor = ref(null)
const hasMore = ref(true)
const meId = ref(null)

const editOpen = ref(false)
const saving = ref(false)
const editNickname = ref('')
const editBio = ref('')
const editAvatar = ref('')
const avatarInput = ref(null)
let newAvatarObject = ''

const isMe = computed(() => meId.value !== null && meId.value === profile.value.id)

const fallbackAvatar = computed(() => {
  const name = profile.value.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((profile.value.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
})

function coverOf(p) {
  if (p.type === 'VIDEO') return p.coverUrl || (p.images && p.images[0]) || ''
  return (p.images && p.images[0]) || p.coverUrl || ''
}

function formatCount(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

function formatDate(s) {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${da}`
}

async function resolveUserId() {
  if (route.params.id !== undefined && route.params.id !== 'me') return Number(route.params.id)
  if (!auth.isLoggedIn) {
    router.replace('/login')
    return null
  }
  const me = await fetchMe()
  meId.value = me.id
  return me.id
}

async function loadProfile() {
  loading.value = true
  try {
    const data = await fetchProfile(profile.value.id)
    profile.value = data
  } catch (e) {
    ElMessage.error('加载主页失败')
  } finally {
    loading.value = false
  }
}

async function loadPosts() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const data = await fetchUserPosts(profile.value.id, nextCursor.value, 12)
    const items = data.items || []
    const seen = new Set(posts.value.map((p) => p.id))
    for (const item of items) {
      if (!seen.has(item.id)) {
        posts.value.push(item)
        seen.add(item.id)
      }
    }
    nextCursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
  } catch (e) {
    // 静默，滚动可重试
  } finally {
    loadingMore.value = false
  }
}

async function toggleFollow() {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  try {
    const data = profile.value.followedByMe
      ? await unfollowUser(profile.value.id)
      : await followUser(profile.value.id)
    profile.value.followedByMe = data.following
    profile.value.followerCount = (profile.value.followerCount || 0) + (data.following ? 1 : -1)
  } catch (e) {
    // 错误已提示
  }
}

function goBack() {
  const state = window.history.state
  const backPath = state && state.back
  // 上一页是作品流（/feed?userId=...）时，穿透它直达首页
  if (typeof backPath === 'string' && backPath.startsWith('/feed') && backPath.includes('userId=')) {
    router.replace('/feed')
  } else {
    router.back()
  }
}

function openPost(p) {
  router.push({ path: '/feed', query: { postId: p.id, userId: profile.value.id } })
}

function openEdit() {
  editNickname.value = profile.value.nickname || ''
  editBio.value = profile.value.bio || ''
  editAvatar.value = profile.value.avatarUrl || ''
  newAvatarObject = ''
  editOpen.value = true
}

function pickAvatar() {
  avatarInput.value && avatarInput.value.click()
}

async function onAvatarChange(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  try {
    const dot = file.name.lastIndexOf('.')
    const extension = dot >= 0 ? file.name.slice(dot + 1) : ''
    const presign = await presignUpload('IMAGE', file.type, extension)
    await putFile(presign.uploadUrl, file)
    newAvatarObject = presign.objectName
    // 本地预览（对象名提交后端，返回时实时签名）
    editAvatar.value = URL.createObjectURL(file)
  } catch (err) {
    ElMessage.error('头像上传失败')
  }
}

function putFile(url, file) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve()
      else reject(new Error('HTTP ' + xhr.status))
    }
    xhr.onerror = () => reject(new Error('网络异常'))
    xhr.send(file)
  })
}

async function saveProfile() {
  const nickname = editNickname.value.trim()
  if (!nickname) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    await updateMe({ nickname, bio: editBio.value.trim(), avatarUrl: newAvatarObject || profile.value.avatarUrl || '' })
    ElMessage.success('资料已更新')
    editOpen.value = false
    await loadProfile()
  } catch (e) {
    // 错误已提示
  } finally {
    saving.value = false
  }
}

function logout() {
  auth.logout()
  router.replace('/login')
}

let scrollHandler = null

onMounted(async () => {
  const userId = await resolveUserId()
  if (userId == null) return
  profile.value = { id: userId }
  if (auth.isLoggedIn && meId.value === null) {
    try {
      const me = await fetchMe()
      meId.value = me.id
    } catch (e) {
      // 未登录时忽略
    }
  }
  await loadProfile()
  await loadPosts()
  scrollHandler = () => {
    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 400) {
      loadPosts()
    }
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })
})

onBeforeUnmount(() => {
  if (scrollHandler) window.removeEventListener('scroll', scrollHandler)
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: #0b0b0e;
  color: #fff;
  padding-bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom) + 20px);
}

.pf-top {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: rgba(11, 11, 14, 0.85);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.pf-back {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  transition: background 0.2s;
}

.pf-back:hover {
  background: rgba(255, 255, 255, 0.16);
}

/* PC：返回按钮改为胶囊样式，带文字并留出边距，不再孤零零贴在角落 */
@media (min-width: 768px) {
  .pf-top {
    padding: 12px 28px;
  }

  .pf-back {
    width: auto;
    height: 38px;
    padding: 0 18px 0 10px;
    border-radius: 999px;
    gap: 6px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    background: rgba(255, 255, 255, 0.06);
    font-size: 13px;
    font-weight: 600;
    transition: background 0.2s, border-color 0.2s;
  }

  .pf-back:hover {
    background: rgba(255, 255, 255, 0.14);
    border-color: rgba(255, 255, 255, 0.22);
  }

  .pf-back-text {
    font-size: 13px;
    font-weight: 600;
    line-height: 1;
  }

  .pf-top-title {
    position: absolute;
    left: 50%;
    transform: translateX(-50%);
    max-width: 40vw;
  }

  .pf-top-spacer {
    display: none;
  }
}

.pf-top-title {
  font-size: 15px;
  font-weight: 600;
  max-width: 60vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pf-top-spacer {
  width: 36px;
}

.pf-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 26px 20px 8px;
}

.pf-avatar {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid rgba(255, 255, 255, 0.9);
  background: var(--sg-bg-warm);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

.pf-nickname {
  margin-top: 14px;
  font-size: 20px;
  font-weight: 700;
}

.pf-bio {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.75);
  text-align: center;
  max-width: 420px;
  word-break: break-word;
}

.pf-join {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.pf-stats {
  display: flex;
  gap: 44px;
  margin-top: 18px;
}

.pf-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.pf-stat b {
  font-size: 17px;
  font-weight: 700;
}

.pf-stat span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.pf-actions {
  display: flex;
  gap: 12px;
  margin-top: 18px;
}

.pf-btn {
  padding: 8px 22px;
  border-radius: var(--sg-radius-full);
  font-size: 14px;
  font-weight: 600;
  transition: transform 0.15s, background 0.2s;
}

.pf-btn:active {
  transform: scale(0.97);
}

.pf-btn-primary {
  background: var(--sg-primary);
  color: #fff;
}

.pf-btn-primary:hover {
  background: var(--sg-primary-deep);
}

.pf-btn-ghost {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.pf-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.18);
}

.pf-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
  margin-top: 22px;
  padding: 0 3px;
  max-width: 600px;
  margin-left: auto;
  margin-right: auto;
}

.pf-cell {
  position: relative;
  aspect-ratio: 1 / 1;
  background: #141210;
  overflow: hidden;
  cursor: pointer;
}

.pf-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.25s ease;
}

.pf-cell:hover img {
  transform: scale(1.04);
}

.pf-cell-mark {
  position: absolute;
  right: 6px;
  bottom: 6px;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 3px;
}

.pf-cell-video {
  color: #fff;
}

.pf-cell-multi {
  color: #fff;
}

.pf-cell-like {
  position: absolute;
  left: 6px;
  bottom: 6px;
  display: flex;
  align-items: center;
  gap: 3px;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
  pointer-events: none;
}

.pf-more {
  padding: 18px 0 30px;
  text-align: center;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
}

.pf-empty {
  padding: 60px 0;
  text-align: center;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.4);
}

.pf-modal {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.pf-modal-panel {
  width: 100%;
  max-width: 380px;
  background: #1c1b20;
  border-radius: var(--sg-radius-lg);
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.pf-modal-title {
  font-size: 17px;
  font-weight: 700;
  text-align: center;
}

.pf-edit-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.pf-edit-avatar img {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.3);
}

.pf-edit-avatar-tip {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.pf-input {
  width: 100%;
  height: 42px;
  border-radius: var(--sg-radius);
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  padding: 0 14px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.pf-input:focus {
  border-color: var(--sg-primary);
}

.pf-textarea {
  height: auto;
  padding: 10px 14px;
  resize: none;
  line-height: 1.5;
}

.pf-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}
</style>
