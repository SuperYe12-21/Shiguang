<template>
  <Transition name="mt">
    <div v-if="item" class="mt" :class="{ 'mt-pc': isPc }" @click="openChat">
      <img class="mt-avatar" :src="avatar" alt="" />
      <div class="mt-body">
        <p class="mt-name">
          {{ name }}
          <span v-if="count > 1" class="mt-count">{{ count }} 条新消息</span>
        </p>
        <p class="mt-text">{{ preview }}</p>
      </div>
      <button class="mt-close" type="button" aria-label="关闭" @click.stop="hide">×</button>
    </div>
  </Transition>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useMessageStore } from '../stores/message'

/** 新私信提示：顶部浮层，4 秒自动收起；正在该会话里 / 自己发的 / 页面不可见时不打扰 */
const AUTO_HIDE_MS = 4000

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const message = useMessageStore()

const isPc = ref(window.innerWidth >= 768)
const item = ref(null)
const count = ref(0)
let hideTimer = null
let unsubscribe = null

const name = computed(() => item.value?.peer?.nickname || '新消息')

const avatar = computed(() => {
  const peer = item.value?.peer
  if (peer?.avatarUrl) return peer.avatarUrl
  const ch = (peer?.nickname || '拾').charAt(0)
  const hue = ((peer?.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
})

const preview = computed(() => {
  const m = item.value?.message
  if (!m) return ''
  if (m.type === 'IMAGE') return '[图片]'
  if (m.type === 'POST_CARD') return '[作品] ' + ((m.post && m.post.title) || '分享美好瞬间')
  return m.content || ''
})

function onIncoming(m, peer) {
  if (!auth.isLoggedIn || !m || m.senderId === auth.userId) return
  if (route.path === '/chat/' + m.senderId) return
  if (document.visibilityState !== 'visible') return
  if (item.value && item.value.peer?.id === m.senderId) {
    count.value += 1
    item.value = { peer: item.value.peer, message: m }
  } else {
    count.value = 1
    item.value = { peer, message: m }
  }
  restartTimer()
}

function restartTimer() {
  if (hideTimer) clearTimeout(hideTimer)
  hideTimer = setTimeout(hide, AUTO_HIDE_MS)
}

function hide() {
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
  item.value = null
  count.value = 0
}

function openChat() {
  const peerId = item.value?.peer?.id
  hide()
  if (peerId) router.push('/chat/' + peerId)
}

function syncIsPc() {
  isPc.value = window.innerWidth >= 768
}

onMounted(() => {
  unsubscribe = message.subscribe(onIncoming)
  window.addEventListener('resize', syncIsPc)
})

onBeforeUnmount(() => {
  if (unsubscribe) unsubscribe()
  window.removeEventListener('resize', syncIsPc)
  if (hideTimer) clearTimeout(hideTimer)
})
</script>

<style scoped>
.mt {
  position: fixed;
  top: calc(12px + env(safe-area-inset-top));
  left: 12px;
  right: 12px;
  z-index: 2800;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(28, 27, 34, 0.96);
  color: #fff;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.32);
  cursor: pointer;
  -webkit-backdrop-filter: blur(14px);
  backdrop-filter: blur(14px);
}

.mt-pc {
  left: auto;
  right: 22px;
  top: 22px;
  width: 320px;
}

.mt-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
  background: rgba(255, 255, 255, 0.12);
}

.mt-body {
  flex: 1;
  min-width: 0;
}

.mt-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mt-count {
  flex: none;
  padding: 1px 7px;
  border-radius: 999px;
  background: rgba(255, 92, 92, 0.22);
  color: #ff8f8f;
  font-size: 11px;
  font-weight: 500;
}

.mt-text {
  margin-top: 2px;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.66);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mt-close {
  flex: none;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.75);
  font-size: 15px;
  line-height: 1;
  font-family: inherit;
  cursor: pointer;
}

.mt-close:hover {
  background: rgba(255, 255, 255, 0.2);
}

.mt-enter-active,
.mt-leave-active {
  transition: opacity 0.22s ease, transform 0.22s cubic-bezier(0.22, 1, 0.36, 1);
}

.mt-enter-from,
.mt-leave-to {
  opacity: 0;
  transform: translateY(-18px);
}
</style>
