<template>
  <div class="sp-root">
    <div class="sp-mask" @click="$emit('close')"></div>
    <div class="sp-panel" :class="{ 'is-pc': isPc }">
      <p class="sp-title">分享</p>

      <div class="sp-card">
        <img v-if="thumb" class="sp-thumb" :src="thumb" alt="" @error="onThumbError" />
        <span v-else class="sp-thumb sp-thumb-empty">拾</span>
        <div class="sp-card-info">
          <p class="sp-card-title">{{ post.title || '分享美好瞬间' }}</p>
          <p class="sp-card-sub">{{ post.type === 'VIDEO' ? '短视频' : '图文' }} · {{ (post.author && post.author.nickname) || '拾光用户' }}</p>
        </div>
      </div>

      <template v-if="auth.isLoggedIn">
        <p class="sp-section">最近聊过</p>
        <div v-if="loading" class="sp-hint">加载中…</div>
        <div v-else-if="friends.length" class="sp-friends">
          <button
            v-for="f in friends"
            :key="f.id"
            class="sp-friend"
            :disabled="sendingId !== null"
            @click="shareTo(f)"
          >
            <span class="sp-avatar-wrap" :class="{ sending: sendingId === f.id }">
              <img class="sp-avatar" :src="f.avatarUrl || fallbackAvatar(f)" alt="" />
            </span>
            <span class="sp-name">{{ f.nickname || '拾光用户' }}</span>
          </button>
        </div>
        <div v-else class="sp-hint">还没有聊过的好友，去主页点私信</div>
      </template>
      <div v-else class="sp-hint">登录后可以把作品分享给站内好友</div>

      <div v-if="manualOpen" class="sp-manual">
        <p class="sp-manual-tip">当前浏览器无法自动复制，请长按下方链接手动复制：</p>
        <input ref="manualInput" class="sp-input" readonly :value="postUrl" @focus="onInputFocus" />
      </div>

      <div class="sp-actions">
        <button class="sp-btn" @click="copyLink">复制链接</button>
        <button v-if="canSystemShare" class="sp-btn" @click="systemShare">系统分享</button>
        <button class="sp-btn ghost" @click="$emit('close')">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchConversations, sendMessage } from '../api/messages'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  post: { type: Object, required: true }
})

const emit = defineEmits(['close', 'shared'])

const auth = useAuthStore()
const isPc = window.innerWidth >= 768
const loading = ref(false)
const friends = ref([])
const sendingId = ref(null)
const manualOpen = ref(false)
const manualInput = ref(null)

const postUrl = computed(() => location.origin + '/post/' + props.post.id)
const thumb = computed(() => props.post.coverUrl || (props.post.images && props.post.images[0]) || '')
const canSystemShare = computed(() => typeof navigator !== 'undefined' && typeof navigator.share === 'function')

function fallbackAvatar(u) {
  const name = u.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((u.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

function onThumbError(e) {
  e.target.style.visibility = 'hidden'
}

function onInputFocus(e) {
  e.target.select()
}

async function loadFriends() {
  if (!auth.isLoggedIn) return
  loading.value = true
  try {
    const data = await fetchConversations('', 10)
    friends.value = (data.items || [])
      .map((c) => c.peer)
      .filter((p) => p && p.id)
      .slice(0, 10)
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false
  }
}

async function shareTo(friend) {
  if (sendingId.value) return
  sendingId.value = friend.id
  try {
    await sendMessage(friend.id, { type: 'POST_CARD', postId: props.post.id })
    ElMessage.success('已发送给 ' + (friend.nickname || '好友'))
    emit('shared', friend)
    emit('close')
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    sendingId.value = null
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(postUrl.value)
    ElMessage.success('链接已复制，快去分享吧')
    emit('close')
  } catch (e) {
    manualOpen.value = true
    await nextTick()
    if (manualInput.value) manualInput.value.select()
  }
}

async function systemShare() {
  try {
    await navigator.share({
      title: props.post.title || '拾光',
      text: '快来看看这个作品',
      url: postUrl.value
    })
    emit('close')
  } catch (e) {
    // 用户取消分享，不做提示
  }
}

onMounted(loadFriends)
</script>

<style scoped>
.sp-root {
  position: fixed;
  inset: 0;
  z-index: 1500;
}

.sp-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  animation: sp-fade 0.18s ease;
}

.sp-panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18px 18px calc(18px + env(safe-area-inset-bottom));
  border-radius: 18px 18px 0 0;
  background: #16151b;
  color: #fff;
  animation: sp-up 0.22s cubic-bezier(0.22, 1, 0.36, 1);
}

.sp-panel.is-pc {
  left: 50%;
  right: auto;
  top: 50%;
  bottom: auto;
  width: 380px;
  padding: 22px;
  border-radius: 16px;
  transform: translate(-50%, -50%);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.5);
  animation: sp-in 0.18s ease;
}

@keyframes sp-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes sp-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

@keyframes sp-in {
  from { opacity: 0; transform: translate(-50%, -46%); }
  to { opacity: 1; transform: translate(-50%, -50%); }
}

.sp-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
}

.sp-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.07);
}

.sp-thumb {
  width: 52px;
  height: 68px;
  border-radius: 8px;
  object-fit: cover;
  flex: none;
  background: rgba(255, 255, 255, 0.1);
}

.sp-thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: rgba(255, 255, 255, 0.5);
}

.sp-card-info {
  min-width: 0;
}

.sp-card-title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sp-card-sub {
  margin-top: 5px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.sp-section {
  margin: 18px 0 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.sp-friends {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  padding-bottom: 6px;
}

.sp-friend {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 62px;
  flex: none;
  border: none;
  background: transparent;
  color: inherit;
  font-family: inherit;
  cursor: pointer;
}

.sp-friend:disabled {
  cursor: default;
}

.sp-avatar-wrap {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  padding: 2px;
  background: linear-gradient(135deg, #ff5c5c, #ffa06b);
}

.sp-avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  display: block;
  background: #26252c;
}

.sp-avatar-wrap.sending {
  opacity: 0.55;
}

.sp-name {
  font-size: 11px;
  max-width: 62px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: rgba(255, 255, 255, 0.75);
}

.sp-hint {
  padding: 14px 2px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
}

.sp-manual {
  margin-top: 14px;
}

.sp-manual-tip {
  margin-bottom: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.sp-input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  font-size: 13px;
  font-family: inherit;
}

.sp-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.sp-btn {
  flex: 1;
  height: 42px;
  border: none;
  border-radius: 12px;
  background: var(--sg-primary, #ff5c5c);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: opacity 0.15s;
}

.sp-btn:hover {
  opacity: 0.9;
}

.sp-btn.ghost {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}
</style>
