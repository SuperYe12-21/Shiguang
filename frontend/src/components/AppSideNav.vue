<template>
  <aside class="app-side-nav" aria-label="主导航">
    <button class="sn-brand" aria-label="拾光首页" @click="goHome">
      <span class="sn-mark">拾</span>
      <span class="sn-brand-text">拾光<small>SHIGUANG</small></span>
    </button>
    <nav class="sn-links">
      <button v-for="item in links" :key="item.key" class="sn-link" :class="{ active: active === item.key }" :aria-current="active === item.key ? 'page' : undefined" @click="item.key === 'home' ? goHome() : router.push(item.path)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path :d="item.icon" /></svg>
        <span>{{ item.label }}</span>
        <UnreadBadge v-if="item.key === 'message'" :count="unreadCount" />
        <i v-if="item.key === 'friends' && friendsUnread" class="sn-dot"></i>
      </button>
    </nav>
    <div class="sn-bottom">
      <button class="sn-publish" @click="router.push('/publish')"><span>＋</span><b>发布作品</b></button>
      <button class="sn-user" @click="router.push('/me')">
        <img v-if="user?.avatarUrl" :src="user.avatarUrl" alt="我的头像" />
        <span v-else class="sn-avatar">{{ (user?.nickname || '拾').slice(0, 1) }}</span>
        <span class="sn-user-name">{{ user?.nickname || '拾光用户' }}<small>记录，值得的瞬间</small></span>
      </button>
      <button class="sn-link sn-settings" @click="router.push('/settings')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><path d="M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2" /></svg>
        <span>设置</span>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { useRouter } from 'vue-router'
import UnreadBadge from './UnreadBadge.vue'

defineProps({
  active: { type: String, default: '' },
  user: { type: Object, default: null },
  unreadCount: { type: Number, default: 0 },
  friendsUnread: { type: Boolean, default: false }
})
const emit = defineEmits(['home'])
const router = useRouter()
const links = [
  { key: 'home', label: '首页', path: '/feed', icon: 'M3 10.5 12 3l9 7.5V21h-6v-7H9v7H3Z' },
  { key: 'friends', label: '朋友', path: '/friends', icon: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8' },
  { key: 'message', label: '消息', path: '/messages', icon: 'M21 11.5a8.5 8.5 0 0 1-8.5 8.5H7l-5 2 2-5v-5.5a8.5 8.5 0 0 1 17 0Z' },
  { key: 'me', label: '我的', path: '/me', icon: 'M20 21v-2a6 6 0 0 0-6-6h-4a6 6 0 0 0-6 6v2M12 10a4 4 0 1 0 0-8 4 4 0 0 0 0 8' }
]
function goHome() {
  emit('home')
  router.push('/feed')
}
</script>

<style scoped>
.app-side-nav { position: fixed; inset: 0 auto 0 0; z-index: 70; width: var(--sg-sidebar-w); padding: 30px 17px 19px; display: flex; flex-direction: column; background: var(--sg-card); border-right: 1px solid var(--sg-line); }
.sn-brand { display: flex; align-items: center; gap: 10px; margin: 0 0 34px 10px; text-align: left; }
.sn-mark { color: var(--sg-primary); font-family: Georgia, 'Songti SC', 'SimSun', serif; font-size: 34px; line-height: 40px; }
.sn-brand-text { font-size: 23px; font-weight: 700; letter-spacing: 3px; }
.sn-brand-text small { display: block; color: var(--sg-text-3); font: 8px/1.5 var(--sg-font); letter-spacing: 3px; }
.sn-links { display: grid; gap: 9px; }
.sn-link { position: relative; display: flex; align-items: center; gap: 14px; padding: 14px 15px; border-radius: 6px; width: 100%; color: var(--sg-text-2); font-size: 14px; text-align: left; transition: background .18s, color .18s; }
.sn-link svg { width: 21px; height: 21px; flex: none; }
.sn-link:hover { background: var(--sg-bg); }
.sn-link.active { color: var(--sg-primary); background: var(--sg-primary-soft); font-weight: 600; }
.sn-link :deep(.sg-badge) { margin-left: auto; }
.sn-dot { width: 6px; height: 6px; background: var(--sg-primary); border-radius: 50%; margin-left: auto; }
.sn-bottom { margin-top: auto; display: grid; gap: 16px; }
.sn-publish { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 10px; border-radius: 5px; background: var(--sg-primary); color: white; font-size: 13px; }
.sn-publish>span { font-size: 22px; line-height: 24px; }
.sn-publish b { font-weight: 500; }
.sn-user { display: flex; align-items: center; gap: 10px; padding: 8px; text-align: left; min-width: 0; }
.sn-user img,.sn-avatar { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 50%; object-fit: cover; background: var(--sg-gradient-deep); color: white; font-size: 17px; flex: none; }
.sn-user-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.sn-user-name small { display: block; margin-top: 4px; font-size: 9px; color: var(--sg-text-3); }
@media(max-width:1100px) {
  .app-side-nav { padding-left: 12px; padding-right: 12px; }
  .sn-brand { justify-content: center; margin-left: 0; }
  .sn-brand-text,.sn-user-name,.sn-publish b { display: none; }
  .sn-link { flex-direction: column; justify-content: center; gap: 7px; padding: 13px 0; font-size: 10px; }
  .sn-user { justify-content: center; padding: 0; }
  .sn-link :deep(.sg-badge),.sn-dot { position: absolute; top: 7px; right: 2px; }
}
</style>
