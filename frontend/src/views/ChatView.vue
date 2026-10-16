<template>
  <div class="chat" :class="{ embedded: props.embedded }">
    <header class="chat-top">
      <button v-if="!props.embedded" class="chat-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        <span>返回</span>
      </button>
      <div class="chat-peer" @click="openPeer">
        <img class="chat-peer-avatar" :src="avatarOf(peer)" alt="" />
        <span class="chat-peer-name">{{ peer?.nickname || '拾光用户' }}</span>
      </div>
      <div v-if="!props.embedded" class="chat-top-space"></div>
    </header>

    <main ref="listEl" class="chat-list" @scroll.passive="onScroll">
      <div class="chat-more">
        <span v-if="loading">加载中…</span>
        <span v-else-if="loadingMore">加载更早的消息…</span>
        <span v-else-if="items.length && !hasMore">没有更多了</span>
      </div>

      <template v-for="(m, i) in items" :key="m.id">
        <div v-if="showTime(i)" class="chat-divider">{{ fmtTime(m.createdAt) }}</div>
        <div class="chat-row" :class="{ mine: isMine(m) }">
          <img v-if="!isMine(m)" class="chat-avatar" :src="avatarOf(peer)" alt="" />
          <div v-if="m.type === 'IMAGE'" class="chat-imgs" :class="{ 'chat-imgs-1': (m.images || []).length === 1 }">
            <img
              v-for="(img, idx) in m.images"
              :key="idx"
              class="chat-img"
              :src="img"
              alt="图片消息"
              loading="lazy"
              @click="openViewer(m.images, idx)"
            />
          </div>
          <button v-else-if="m.type === 'POST_CARD'" class="chat-card" @click="openCard(m)">
            <img v-if="m.post && m.post.coverUrl" class="chat-card-cover" :src="m.post.coverUrl" alt="作品封面" loading="lazy" />
            <div v-else class="chat-card-cover chat-card-blank">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M4 5h16v14H4V5zm2 2v10h12V7H6zm2 8l2.5-3 2 2.4L14 12l2 3H8z"/></svg>
            </div>
            <div class="chat-card-info">
              <span class="chat-card-title">{{ m.post && m.post.available ? (m.post.title || '分享美好瞬间') : '作品已删除' }}</span>
              <span class="chat-card-sub">{{ m.post && m.post.available ? ('@' + (m.post.authorNickname || '拾光用户') + ' · 作品') : '无法查看' }}</span>
            </div>
          </button>
          <div v-else class="chat-bubble">{{ m.content }}</div>
          <img v-if="isMine(m)" class="chat-avatar" :src="avatarOf(myProfile)" alt="" />
        </div>
      </template>

      <div v-if="!items.length && !loading" class="chat-empty">还没有消息，先打个招呼吧～</div>
    </main>

    <footer class="chat-input">
      <p v-if="hint" class="chat-hint">{{ hint }}</p>
      <RichInput
        ref="richInputEl"
        v-model="draft"
        v-model:images="draftImages"
        :max-images="9"
        :maxlength="1000"
        :placeholder="canSend ? '发消息…' : '对方回复后才能继续聊天'"
        :disabled="!canSend || sending"
        :sending="sending"
        :dark="false"
        @submit="send"
      />
    </footer>

    <ImageViewer v-if="viewerOpen" :images="viewerImages" :index="viewerIndex" @close="viewerOpen = false" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchChat, sendMessage, markConversationRead } from '../api/messages'
import { fetchMe } from '../api/user'
import { useMessageStore } from '../stores/message'
import { useAuthStore } from '../stores/auth'
import RichInput from '../components/RichInput.vue'
import ImageViewer from '../components/ImageViewer.vue'

const props = defineProps({
  peerUserId: { type: [Number, String], default: null },
  embedded: { type: Boolean, default: false }
})
const emit = defineEmits(['read'])

const route = useRoute()
const router = useRouter()
const message = useMessageStore()
const auth = useAuthStore()

const peerId = Number(props.peerUserId ?? route.params.userId)
const listEl = ref(null)
const inputEl = ref(null)
const richInputEl = ref(null)
const peer = ref(null)
const me = ref(auth.user || null)
const canSend = ref(true)
const hint = ref('')
const items = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const sending = ref(false)
const draft = ref('')
const draftImages = ref([])
const viewerImages = ref([])
const viewerIndex = ref(0)
const viewerOpen = ref(false)
let unsubscribe = null
let disposed = false

function avatarOf(user) {
  if (user?.avatarUrl) return user.avatarUrl
  const id = user?.id || 0
  const ch = (user?.nickname || '拾').charAt(0)
  const hue = (id * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

function isMine(m) {
  const myId = me.value?.id ?? auth.userId
  return myId == null ? m.senderId !== peerId : m.senderId === myId
}

/** 自己那侧头像：优先用后端最新的资料，拿不到就退回本地缓存 */
const myProfile = computed(() => me.value || auth.user || { id: auth.userId, nickname: '我' })

function fmtTime(s) {
  const d = new Date(s)
  if (isNaN(d.getTime())) return ''
  const hm = `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return hm
  return `${d.getMonth() + 1}月${d.getDate()}日 ${hm}`
}

function showTime(i) {
  if (i === 0) return true
  const prev = new Date(items.value[i - 1].createdAt).getTime()
  const cur = new Date(items.value[i].createdAt).getTime()
  if (isNaN(prev) || isNaN(cur)) return false
  return cur - prev > 5 * 60 * 1000
}

function scrollToBottom() {
  const el = listEl.value
  if (el) el.scrollTop = el.scrollHeight
}

async function loadFirst() {
  loading.value = true
  try {
    const data = await fetchChat(peerId, '', 30)
    peer.value = data.peer
    canSend.value = data.canSend !== false
    hint.value = data.hint || ''
    items.value = (data.items || []).slice().reverse()
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
    await nextTick()
    scrollToBottom()
  } catch (e) {
    // 错误已提示
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value || loading.value || !cursor.value) return
  loadingMore.value = true
  const el = listEl.value
  const prevHeight = el ? el.scrollHeight : 0
  const prevTop = el ? el.scrollTop : 0
  try {
    const data = await fetchChat(peerId, cursor.value, 30)
    const older = (data.items || []).slice().reverse()
    const seen = new Set(items.value.map((x) => x.id))
    items.value = [...older.filter((m) => !seen.has(m.id)), ...items.value]
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
    await nextTick()
    if (el) el.scrollTop = prevTop + (el.scrollHeight - prevHeight)
  } catch (e) {
    // 静默
  } finally {
    loadingMore.value = false
  }
}

function appendMessage(m) {
  if (items.value.some((x) => x.id === m.id)) return
  items.value.push(m)
  nextTick(() => scrollToBottom())
}

async function refreshState() {
  try {
    const data = await fetchChat(peerId, '', 1)
    canSend.value = data.canSend !== false
    hint.value = data.hint || ''
  } catch (e) {
    // 忽略
  }
}

async function markRead() {
  try {
    const data = await markConversationRead(peerId)
    if (disposed) return
    if (data && typeof data.messages === 'number') {
      message.unread = data.messages
    }
    emit('read', peerId)
  } catch (e) {
    // 忽略
  }
}

async function send() {
  const text = draft.value.trim()
  const images = draftImages.value.filter((img) => !img.uploading && img.object).map((img) => img.object)
  if ((!text && !images.length) || !canSend.value || sending.value) return
  sending.value = true
  try {
    const payload = images.length
      ? { type: 'IMAGE', content: '', imageUrls: images }
      : { type: 'TEXT', content: text }
    const m = await sendMessage(peerId, payload)
    appendMessage(m)
    draft.value = ''
    draftImages.value = []
    if (richInputEl.value) {
      richInputEl.value.closeEmoji()
      richInputEl.value.focus()
    }
    refreshState()
  } catch (e) {
    refreshState()
  } finally {
    sending.value = false
  }
}

function openViewer(images, index) {
  viewerImages.value = images || []
  viewerIndex.value = index || 0
  viewerOpen.value = true
}

function openCard(m) {
  if (m.post && m.post.available) {
    router.push('/post/' + m.post.id)
  } else {
    ElMessage.info('作品已删除')
  }
}

function onScroll() {
  const el = listEl.value
  if (!el || loadingMore.value || loading.value) return
  if (el.scrollTop <= 40) loadMore()
}

/** 实时新消息：当前会话相关就追加；对方发来的顺手标记已读 */
function onIncoming(m) {
  const related = (m.senderId === peerId && m.receiverId === auth.userId)
    || (m.senderId === auth.userId && m.receiverId === peerId)
  if (!related) return
  appendMessage(m)
  if (m.receiverId === auth.userId) {
    markRead()
    refreshState()
  }
}

function openPeer() {
  if (peerId) router.push('/user/' + peerId)
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/messages')
  }
}

onMounted(async () => {
  if (!peerId) {
    router.replace('/messages')
    return
  }
  message.refresh()
  loadMe()
  await loadFirst()
  if (disposed) return
  markRead()
  unsubscribe = message.subscribe(onIncoming)
})

async function loadMe() {
  if (!auth.isLoggedIn) return
  try {
    me.value = await fetchMe()
  } catch (e) {
    // 拿不到就继续用本地缓存的资料
  }
}

onBeforeUnmount(() => {
  disposed = true
  if (unsubscribe) unsubscribe()
})
</script>

<style scoped>
.chat {
  --bg: var(--sg-bg, #f4f1e9);
  --card: var(--sg-card, #faf8f2);
  --text: var(--sg-text, #3b4239);
  --text-2: var(--sg-text-2, #99998d);
  --line: var(--sg-line, #e6e3d9);
  --peer-bubble: var(--card);
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
  height: 100dvh;
  background: var(--bg);
  color: var(--text);
  font-family: var(--sg-font);
  -webkit-font-smoothing: antialiased;
}

.chat.embedded {
  height: 100%;
  min-height: 0;
}

.chat-top {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 68px;
  padding: 12px 24px;
  box-sizing: border-box;
  border-bottom: 1px solid var(--line);
  background: var(--bg);
}

.chat-back {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 36px;
  padding: 0 12px 0 9px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--card);
  color: var(--text);
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}

.chat-back:hover {
  color: var(--sg-primary, #b66c4d);
}

.chat-back:focus-visible, .chat-card:focus-visible {
  outline: 2px solid var(--sg-primary, #b66c4d);
  outline-offset: 2px;
}

.chat-peer {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 11px;
  cursor: pointer;
  min-width: 0;
}

.embedded .chat-top {
  min-height: 75px;
}

.embedded .chat-peer {
  justify-content: flex-start;
}

.chat-peer-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
}

.chat-peer-name {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-top-space {
  width: 76px;
  flex: none;
}

.chat-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 20px 28px 24px;
  max-width: 900px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}

.embedded .chat-list {
  max-width: none;
}

.chat-more {
  min-height: 24px;
  display: grid;
  place-items: center;
  color: var(--text-2);
  font-size: 11px;
}

.chat-divider {
  text-align: center;
  color: var(--text-2);
  font-size: 10px;
  margin: 12px 0 23px;
}

.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 0 0 20px;
}

.chat-row.mine {
  justify-content: flex-end;
}

.chat-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
}

.chat-imgs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
  width: min(78%, 296px);
}

.chat-imgs-1 {
  grid-template-columns: minmax(0, 1fr);
  width: min(78%, 180px);
}

.chat-img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 5px;
  cursor: zoom-in;
  background: var(--line);
}

.chat-row.mine .chat-imgs {
  margin-left: auto;
}

.chat-card {
  display: flex;
  flex-direction: column;
  width: min(78%, 220px);
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
  background: var(--card);
  color: inherit;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.chat-card-cover {
  width: 100%;
  height: 120px;
  object-fit: cover;
  flex: none;
  background: var(--line);
}

.chat-card-blank {
  display: grid;
  place-items: center;
  color: var(--text-2);
}

.chat-card-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  width: 100%;
  padding: 12px;
  box-sizing: border-box;
}

.chat-card-title {
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-card-sub {
  font-size: 10px;
  color: var(--text-2);
}

.chat-bubble {
  max-width: min(78%, 520px);
  padding: 12px 15px;
  border-radius: 5px;
  background: var(--peer-bubble);
  font-size: 13px;
  line-height: 1.8;
  word-break: break-word;
  white-space: pre-wrap;
  box-sizing: border-box;
}

.chat-row.mine .chat-bubble {
  background: var(--sg-primary-soft, #eee4d8);
  color: var(--text);
}

.chat-empty {
  padding: 60px 0;
  text-align: center;
  color: var(--text-2);
  font-size: 13px;
}

.chat-input {
  flex: none;
  min-width: 0;
  padding: 8px 12px 0;
  border-top: 1px solid var(--line);
  background: var(--card);
}

.chat-input :deep(.ri) {
  max-width: 900px;
  margin: 0 auto;
  border-top: 0;
}

.chat-hint {
  max-width: 876px;
  margin: 0 auto 8px;
  padding: 7px 12px;
  border-radius: 5px;
  background: var(--sg-primary-soft, #eee4d8);
  color: var(--sg-primary, #b66c4d);
  font-size: 12px;
}

@media (max-width: 767px) {
  .chat-top {
    min-height: 65px;
    padding: 10px 12px;
  }

  .chat-list {
    padding: 20px 16px;
  }

  .chat-input {
    padding: 6px 0 0;
  }

  .chat-hint {
    margin: 0 12px 6px;
  }
}
</style>
