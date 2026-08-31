<template>
  <Teleport to="body">
    <div class="am-mask" :class="{ 'am-mask-pc': isPc }" @click.self="close">
      <section class="am-panel" :class="isPc ? 'am-panel-pc' : 'am-panel-mobile'">
        <header class="am-head">
          <h3 class="am-title">我的账号</h3>
          <button class="am-close" aria-label="关闭" @click="close">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M18.3 5.71L12 12l-6.3-6.29L4.3 7.12 10.59 13.4 4.3 19.69l1.4 1.41L12 14.82l6.3 6.29 1.4-1.41-6.29-6.29 6.29-6.28z"/></svg>
          </button>
        </header>

        <div class="am-body">
          <div class="am-user">
            <img class="am-avatar" :src="me.avatarUrl || fallbackAvatar" alt="头像" />
            <div class="am-info">
              <span class="am-name">{{ me.nickname || '拾光用户' }}</span>
              <span class="am-phone">{{ maskPhone(me.phone) }}</span>
            </div>
          </div>
          <p class="am-hint">切换账号请先退出登录，再使用其他手机号登录</p>
          <button class="am-logout" @click="logout">退出登录</button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMe } from '../api/user'
import { useAuthStore } from '../stores/auth'

const emit = defineEmits(['close'])
const router = useRouter()
const auth = useAuthStore()

const isPc = computed(() => window.innerWidth >= 768)
const me = ref({})

const fallbackAvatar = computed(() => {
  const name = me.value.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((me.value.id || 0) * 47) % 360
  const svg = "<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(" + hue + ",60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(" + hue + ",45%,42%)' font-family='sans-serif'>" + ch + "</text></svg>"
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg)
})

function maskPhone(phone) {
  if (!phone || phone.length !== 11) return phone || ''
  return phone.slice(0, 3) + '****' + phone.slice(7)
}

function logout() {
  auth.logout()
  router.push('/login')
}

function close() {
  emit('close')
}

function onKeydown(e) {
  if (e.key === 'Escape') close()
}

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  document.body.style.overflow = 'hidden'
  try {
    me.value = await fetchMe()
  } catch (e) {
    // 401 时拦截器会自动跳登录页
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.am-mask {
  position: fixed;
  inset: 0;
  z-index: 2100;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.am-mask-pc {
  align-items: center;
}

.am-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.am-panel-mobile {
  width: 100%;
  border-radius: 18px 18px 0 0;
  background: #fff;
  animation: am-up 0.28s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.am-panel-pc {
  width: 360px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.3);
  animation: am-pop 0.22s ease-out;
}

@keyframes am-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

@keyframes am-pop {
  from { transform: scale(0.94); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.am-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.am-title {
  font-size: 16px;
  font-weight: 700;
}

.am-close {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: inherit;
  opacity: 0.65;
  transition: opacity 0.2s, background 0.2s;
}

.am-close:hover {
  opacity: 1;
  background: rgba(128, 128, 128, 0.15);
}

.am-body {
  padding: 20px 16px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.am-user {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
}

.am-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  background: #f0e9e0;
  border: 2px solid #fff;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
}

.am-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.am-name {
  font-size: 17px;
  font-weight: 700;
}

.am-phone {
  font-size: 13px;
  color: rgba(128, 128, 128, 0.9);
}

.am-hint {
  font-size: 12px;
  color: rgba(128, 128, 128, 0.9);
  text-align: center;
  line-height: 1.6;
}

.am-logout {
  width: 100%;
  height: 42px;
  border-radius: 12px;
  background: #fff0f1;
  color: #e04f5f;
  font-size: 15px;
  font-weight: 600;
  transition: background 0.2s;
}

.am-logout:hover {
  background: #ffe3e6;
}
</style>
