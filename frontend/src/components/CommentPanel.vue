<template>
  <Teleport to="body">
    <div class="cp-mask" :class="{ 'cp-mask-pc': isPc }" @click.self="close">
      <section class="cp-panel" :class="isPc ? 'cp-panel-pc' : 'cp-panel-mobile'">
        <header class="cp-head">
          <h3 class="cp-title">评论 {{ formatCount(post.commentCount) }}</h3>
          <button class="cp-close" aria-label="关闭评论" @click="close">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M18.3 5.71L12 12l-6.3-6.29L4.3 7.12 10.59 13.4 4.3 19.69l1.4 1.41L12 14.82l6.3 6.29 1.4-1.41-6.29-6.29 6.29-6.28z"/></svg>
          </button>
        </header>

        <div ref="listEl" class="cp-list" @scroll="onScroll">
          <div v-if="loading" class="cp-state">评论加载中…</div>
          <div v-else-if="!comments.length" class="cp-state">还没有评论，来抢沙发吧～</div>

          <div v-for="c in comments" :key="c.id" class="cp-item">
            <img class="cp-avatar" :src="c.author && c.author.avatarUrl ? c.author.avatarUrl : fallbackAvatar(c)" alt="头像" />
            <div class="cp-main">
              <div class="cp-meta">
                <span class="cp-name">{{ c.author ? c.author.nickname : '拾光用户' }}</span>
                <span class="cp-time">{{ formatTime(c.createdAt) }}</span>
              </div>
              <p class="cp-content">{{ c.content }}</p>
              <button v-if="c.mine" class="cp-del" @click="remove(c)">删除</button>
            </div>
            <button class="cp-like" :class="{ 'is-liked': c.liked }" @click="toggleLike(c)">
              <svg viewBox="0 0 24 24" width="18" height="18" :fill="c.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
              <span>{{ formatCount(c.likeCount) }}</span>
            </button>
          </div>

          <div v-if="loadingMore" class="cp-state">加载更多…</div>
        </div>

        <footer class="cp-foot">
          <input
            v-model="draft"
            class="cp-input"
            :placeholder="auth.isLoggedIn ? '说点什么吧…' : '登录后参与评论'"
            maxlength="1000"
            :disabled="submitting"
            @keyup.enter="submit"
          />
          <button class="cp-send" :disabled="submitting || !draft.trim()" @click="submit">发送</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchComments, createComment, deleteComment, likeComment, unlikeComment } from '../api/comments'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  post: { type: Object, required: true }
})
const emit = defineEmits(['close'])

const auth = useAuthStore()
const isPc = computed(() => window.innerWidth >= 768)

const listEl = ref(null)
const comments = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const draft = ref('')
const submitting = ref(false)

async function loadFirst() {
  if (loading.value) return
  loading.value = true
  try {
    const data = await fetchComments(props.post.id, null, 20)
    comments.value = data.items || []
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
  } catch (e) {
    // 错误提示已由拦截器处理
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value || loading.value) return
  loadingMore.value = true
  try {
    const data = await fetchComments(props.post.id, cursor.value, 20)
    const items = data.items || []
    const seen = new Set(comments.value.map((c) => c.id))
    for (const item of items) {
      if (!seen.has(item.id)) {
        comments.value.push(item)
        seen.add(item.id)
      }
    }
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
  } catch (e) {
    // 静默，滚动可重试
  } finally {
    loadingMore.value = false
  }
}

function onScroll() {
  const el = listEl.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 80) {
    loadMore()
  }
}

function requireLogin() {
  if (!auth.isLoggedIn) {
    location.href = '/login'
    return false
  }
  return true
}

async function submit() {
  const text = draft.value.trim()
  if (!text || submitting.value) return
  if (!requireLogin()) return
  submitting.value = true
  try {
    const created = await createComment(props.post.id, text)
    comments.value.unshift(created)
    props.post.commentCount = (props.post.commentCount || 0) + 1
    draft.value = ''
    requestAnimationFrame(() => {
      const el = listEl.value
      if (el) el.scrollTop = 0
    })
  } catch (e) {
    // 错误提示已由拦截器处理
  } finally {
    submitting.value = false
  }
}

async function toggleLike(c) {
  if (!requireLogin()) return
  const prevLiked = c.liked
  const prevCount = c.likeCount
  c.liked = !prevLiked
  c.likeCount = Math.max(0, (prevCount || 0) + (prevLiked ? -1 : 1))
  try {
    const data = prevLiked ? await unlikeComment(c.id) : await likeComment(c.id)
    c.liked = data.liked
    c.likeCount = data.likeCount
  } catch (e) {
    c.liked = prevLiked
    c.likeCount = prevCount
  }
}

async function remove(c) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteComment(c.id)
    comments.value = comments.value.filter((x) => x.id !== c.id)
    props.post.commentCount = Math.max(0, (props.post.commentCount || 0) - 1)
    ElMessage.success('评论已删除')
  } catch (e) {
    // 错误提示已由拦截器处理
  }
}

function close() {
  emit('close')
}

function onKeydown(e) {
  if (e.key === 'Escape') close()
}

onMounted(() => {
  loadFirst()
  window.addEventListener('keydown', onKeydown)
  document.body.style.overflow = 'hidden'
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})

function formatCount(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

function formatTime(s) {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  const m = 60 * 1000
  const h = 60 * m
  const day = 24 * h
  if (diff < m) return '刚刚'
  if (diff < h) return Math.floor(diff / m) + '分钟前'
  if (diff < day) return Math.floor(diff / h) + '小时前'
  if (diff < 7 * day) return Math.floor(diff / day) + '天前'
  const y = d.getFullYear()
  const now = new Date()
  if (y === now.getFullYear()) return (d.getMonth() + 1) + '月' + d.getDate() + '日'
  return y + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日'
}

function fallbackAvatar(c) {
  const name = c.author ? c.author.nickname : '拾'
  const ch = name.charAt(0)
  const hue = (((c.author && c.author.id) || 0) * 47) % 360
  const svg = "<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(" + hue + ",60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(" + hue + ",45%,42%)' font-family='sans-serif'>" + ch + "</text></svg>"
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg)
}
</script>

<style scoped>
.cp-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: transparent;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  animation: cp-fade 0.25s ease-out;
}

.cp-mask-pc {
  align-items: stretch;
  justify-content: flex-end;
  background: rgba(0, 0, 0, 0.12);
}

@keyframes cp-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

.cp-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cp-panel-mobile {
  width: 100%;
  height: 62vh;
  max-height: 640px;
  border-radius: 18px 18px 0 0;
  background: #fff;
  animation: cp-up 0.28s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.cp-panel-pc {
  width: 400px;
  height: 100%;
  background: rgba(20, 19, 24, 0.94);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  animation: cp-in 0.25s ease-out;
  color: #fff;
}

@keyframes cp-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

@keyframes cp-in {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}

.cp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.cp-panel-pc .cp-head {
  border-bottom-color: rgba(255, 255, 255, 0.08);
}

.cp-title {
  font-size: 16px;
  font-weight: 700;
}

.cp-close {
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

.cp-close:hover {
  opacity: 1;
  background: rgba(128, 128, 128, 0.15);
}

.cp-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 8px;
  -webkit-overflow-scrolling: touch;
}

.cp-state {
  padding: 36px 0;
  text-align: center;
  font-size: 13px;
  color: rgba(128, 128, 128, 0.9);
}

.cp-panel-pc .cp-state {
  color: rgba(255, 255, 255, 0.5);
}

.cp-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
}

.cp-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  object-fit: cover;
  background: #f0e9e0;
}

.cp-main {
  flex: 1;
  min-width: 0;
}

.cp-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 3px;
}

.cp-name {
  font-size: 13px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.55);
}

.cp-panel-pc .cp-name {
  color: rgba(255, 255, 255, 0.55);
}

.cp-time {
  font-size: 12px;
  color: rgba(128, 128, 128, 0.8);
}

.cp-content {
  font-size: 15px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.cp-del {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(128, 128, 128, 0.9);
}

.cp-del:hover {
  color: #e04f5f;
}

.cp-like {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: rgba(128, 128, 128, 0.9);
  flex-shrink: 0;
  padding-top: 2px;
  min-width: 44px;
}

.cp-like.is-liked {
  color: #ff4757;
}

.cp-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px calc(10px + env(safe-area-inset-bottom));
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.cp-panel-pc .cp-foot {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.cp-input {
  flex: 1;
  height: 38px;
  border-radius: 19px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: rgba(0, 0, 0, 0.05);
  padding: 0 16px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.cp-input:focus {
  border-color: var(--sg-primary);
}

.cp-panel-pc .cp-input {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.14);
  color: #fff;
}

.cp-panel-pc .cp-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.cp-send {
  height: 38px;
  padding: 0 20px;
  border-radius: 19px;
  background: var(--sg-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  transition: opacity 0.2s, transform 0.15s;
  flex-shrink: 0;
}

.cp-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cp-send:not(:disabled):hover {
  background: var(--sg-primary-deep);
  transform: scale(1.03);
}
</style>
