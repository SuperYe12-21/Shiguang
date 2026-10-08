<template>
  <div class="st" :class="{ dark: !isPc }">
    <header class="st-top">
      <button class="st-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        <span>返回</span>
      </button>
      <h1 class="st-title">账号设置</h1>
      <div class="st-space"></div>
    </header>

    <main class="st-body">
      <section class="st-card">
        <div class="st-me">
          <img class="st-avatar" :src="me.avatarUrl || fallbackAvatar" alt="头像" />
          <div class="st-me-info">
            <p class="st-me-name">{{ me.nickname || '拾光用户' }}</p>
            <p class="st-me-phone">{{ maskPhone(me.phone) }}</p>
          </div>
          <button class="st-btn st-btn-ghost" @click="router.push('/me')">编辑资料</button>
        </div>
      </section>

      <section class="st-card">
        <div class="st-section-head">
          <h2 class="st-section-title">隐私</h2>
          <span class="st-section-tip">好友 = 互相关注</span>
        </div>
        <div v-for="row in rows" :key="row.key" class="st-row">
          <div class="st-row-text">
            <p class="st-row-title">{{ row.title }}</p>
            <p class="st-row-desc">{{ row.desc }}</p>
          </div>
          <div class="st-seg">
            <button
              v-for="opt in levels"
              :key="opt.value"
              class="st-seg-btn"
              :class="{ on: privacy[row.key] === opt.value }"
              :disabled="saving"
              @click="setLevel(row.key, opt.value)"
            >{{ opt.label }}</button>
          </div>
        </div>
      </section>

      <section class="st-card">
        <h2 class="st-section-title">账号</h2>
        <button class="st-line" @click="logout">
          <span class="st-line-text st-danger">退出登录</span>
          <span class="st-line-arr">›</span>
        </button>
      </section>

      <section class="st-card st-about">
        <span class="st-brand">拾光</span>
        <span class="st-version">v1.0.0 · 记录每个瞬间</span>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchMe, fetchPrivacy, updatePrivacy } from '../api/user'
import { useAuthStore } from '../stores/auth'
import { useMessageStore } from '../stores/message'
import { useNotificationStore } from '../stores/notification'

const router = useRouter()
const auth = useAuthStore()
const message = useMessageStore()
const notification = useNotificationStore()

const isPc = ref(window.innerWidth >= 768)
const me = ref({})
const saving = ref(false)
const privacy = reactive({
  like: 'PUBLIC',
  favorite: 'PUBLIC',
  follower: 'PUBLIC',
  following: 'PUBLIC'
})

const levels = [
  { value: 'PUBLIC', label: '公开' },
  { value: 'FRIENDS', label: '好友' },
  { value: 'PRIVATE', label: '私密' }
]

const rows = [
  { key: 'like', title: '点赞列表', desc: '别人能看我在主页点了哪些作品' },
  { key: 'favorite', title: '收藏列表', desc: '别人能看我收藏了哪些作品' },
  { key: 'follower', title: '粉丝列表', desc: '别人能看谁关注了我' },
  { key: 'following', title: '关注列表', desc: '别人能看我关注了谁' }
]

const fallbackAvatar = computed(() => {
  const ch = (me.value.nickname || '拾').charAt(0)
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(210,60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(210,45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
})

function maskPhone(phone) {
  if (!phone || phone.length < 7) return phone || ''
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

async function setLevel(key, value) {
  if (privacy[key] === value || saving.value) return
  const previous = privacy[key]
  privacy[key] = value
  saving.value = true
  try {
    const data = await updatePrivacy({ ...privacy })
    Object.assign(privacy, data)
    ElMessage.success('已保存')
  } catch (e) {
    privacy[key] = previous
  } finally {
    saving.value = false
  }
}

function logout() {
  auth.logout()
  message.stop()
  notification.stop()
  router.replace('/login')
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/me')
}

function syncIsPc() {
  isPc.value = window.innerWidth >= 768
}

onMounted(async () => {
  window.addEventListener('resize', syncIsPc)
  try {
    me.value = await fetchMe()
  } catch (e) {
    // 未登录时路由守卫会拦
  }
  try {
    const data = await fetchPrivacy()
    Object.assign(privacy, data)
  } catch (e) {
    // 保持默认公开
  }
})
</script>

<style scoped>
.st {
  min-height: 100vh;
  background: #f7f7f5;
  color: #26221f;
  font-family: var(--sg-font);
  -webkit-font-smoothing: antialiased;
}

.st.dark {
  background: #0b0b0e;
  color: #f5f2ee;
  padding-bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom) + 16px);
}

.st-top {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 720px;
  margin: 0 auto;
  padding: 22px 18px 12px;
}

.st.dark .st-top {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 14px 16px;
  background: rgba(11, 11, 14, 0.9);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.st-back {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  padding: 0 16px 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(38, 34, 31, 0.1);
  background: #fff;
  color: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}

.st.dark .st-back {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.1);
}

.st-title {
  flex: 1;
  text-align: center;
  font-size: 18px;
  font-weight: 800;
}

.st-space {
  width: 84px;
  flex: none;
}

.st-body {
  max-width: 720px;
  margin: 0 auto;
  padding: 6px 18px 60px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.st-card {
  border-radius: 18px;
  padding: 16px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(38, 34, 31, 0.06);
}

.st.dark .st-card {
  background: rgba(255, 255, 255, 0.06);
  box-shadow: none;
}

.st-me {
  display: flex;
  align-items: center;
  gap: 14px;
}

.st-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
}

.st-me-info {
  flex: 1;
  min-width: 0;
}

.st-me-name {
  font-size: 16px;
  font-weight: 700;
}

.st-me-phone {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.55;
}

.st-btn {
  height: 36px;
  padding: 0 16px;
  border-radius: 999px;
  border: none;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
}

.st-btn-ghost {
  background: rgba(255, 92, 92, 0.12);
  color: #ff5c5c;
}

.st-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
}

.st-section-title {
  font-size: 15px;
  font-weight: 700;
}

.st-section-tip {
  font-size: 11px;
  opacity: 0.5;
}

.st-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid rgba(38, 34, 31, 0.07);
}

.st.dark .st-row {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.st-row:first-of-type {
  border-top: none;
}

.st-row-text {
  min-width: 0;
}

.st-row-title {
  font-size: 14px;
  font-weight: 600;
}

.st-row-desc {
  margin-top: 3px;
  font-size: 11px;
  opacity: 0.5;
}

.st-seg {
  display: flex;
  flex: none;
  border-radius: 999px;
  padding: 3px;
  background: rgba(38, 34, 31, 0.07);
}

.st.dark .st-seg {
  background: rgba(255, 255, 255, 0.1);
}

.st-seg-btn {
  height: 30px;
  padding: 0 13px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.18s, color 0.18s;
}

.st-seg-btn.on {
  background: #ff5c5c;
  color: #fff;
}

.st-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 8px;
  padding: 12px 0;
  border: none;
  background: transparent;
  color: inherit;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.st-danger {
  color: #ff5c5c;
}

.st-line-arr {
  opacity: 0.4;
  font-size: 18px;
}

.st-about {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 22px 16px;
}

.st-brand {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 4px;
}

.st-version {
  font-size: 12px;
  opacity: 0.5;
}
</style>
