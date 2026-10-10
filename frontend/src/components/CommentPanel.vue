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

          <div v-for="c in comments" :key="c.id" class="cp-thread">
            <div
              class="cp-item"
              :class="{ 'cp-flash': flashKey === 't' + c.id }"
              :ref="(el) => setRowRef('t' + c.id, el)"
            >
              <img class="cp-avatar" :src="avatarSrc(c)" @error="onAvatarError(c)" @click="goUser(c.author)" alt="头像" />
              <div class="cp-main">
                <div class="cp-meta">
                  <span class="cp-name">{{ c.author ? c.author.nickname : '拾光用户' }}</span>
                  <span v-if="isPostAuthor(c)" class="cp-badge">作者</span>
                  <span class="cp-time">{{ formatTime(c.createdAt) }}</span>
                </div>
                <p class="cp-content">{{ c.content }}</p>
                <div v-if="(c.images || []).length" class="cp-imgs" :class="{ 'cp-imgs-1': c.images.length === 1 }">
                  <img
                    v-for="(img, i) in c.images"
                    :key="i"
                    class="cp-img"
                    :src="img"
                    alt="评论图片"
                    loading="lazy"
                    @click="openViewer(c.images, i)"
                  />
                </div>
                <div class="cp-actions">
                  <button class="cp-act" @click="startReply(c, null)">回复</button>
                  <button v-if="c.canDelete" class="cp-act cp-act-del" @click="removeTop(c)">删除</button>
                </div>
              </div>
              <button class="cp-like" :class="{ 'is-liked': c.liked }" @click="toggleLike(c)">
                <svg viewBox="0 0 24 24" width="18" height="18" :fill="c.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
                <span>{{ formatCount(c.likeCount) }}</span>
              </button>
            </div>

            <button v-if="thread(c).expanded" class="cp-collapse" @click="collapse(c)">收起回复</button>
            <button v-else-if="c.replyCount > 0" class="cp-expand" @click="expand(c)">
              查看 {{ c.replyCount }} 条回复
            </button>

            <div v-if="thread(c).expanded" class="cp-replies">
              <div v-if="thread(c).loading" class="cp-state cp-state-sm">回复加载中…</div>
              <div
                v-for="r in thread(c).items"
                :key="r.id"
                class="cp-item cp-item-sub"
                :class="{ 'cp-flash': flashKey === 'r' + r.id }"
                :ref="(el) => setRowRef('r' + r.id, el)"
              >
                <img class="cp-avatar cp-avatar-sm" :src="avatarSrc(r)" @error="onAvatarError(r)" @click="goUser(r.author)" alt="头像" />
                <div class="cp-main">
                  <div class="cp-meta">
                    <span class="cp-name">{{ r.author ? r.author.nickname : '拾光用户' }}</span>
                    <span v-if="isPostAuthor(r)" class="cp-badge">作者</span>
                    <span class="cp-time">{{ formatTime(r.createdAt) }}</span>
                  </div>
                  <p class="cp-content">
                    <span v-if="r.replyToUser" class="cp-at">回复 @{{ r.replyToUser.nickname }}：</span>{{ r.content }}
                  </p>
                  <div v-if="(r.images || []).length" class="cp-imgs" :class="{ 'cp-imgs-1': r.images.length === 1 }">
                    <img
                      v-for="(img, i) in r.images"
                      :key="i"
                      class="cp-img"
                      :src="img"
                      alt="评论图片"
                      loading="lazy"
                      @click="openViewer(r.images, i)"
                    />
                  </div>
                  <div class="cp-actions">
                    <button class="cp-act" @click="startReply(r, c)">回复</button>
                    <button v-if="r.canDelete" class="cp-act cp-act-del" @click="removeReply(r, c)">删除</button>
                  </div>
                </div>
                <button class="cp-like" :class="{ 'is-liked': r.liked }" @click="toggleLike(r)">
                  <svg viewBox="0 0 24 24" width="16" height="16" :fill="r.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
                  <span>{{ formatCount(r.likeCount) }}</span>
                </button>
              </div>
              <button
                v-if="thread(c).hasMore"
                class="cp-expand cp-expand-sub"
                :disabled="thread(c).loadingMore"
                @click="loadMoreReplies(c)"
              >
                {{ thread(c).loadingMore ? '加载中…' : '查看更多回复' }}
              </button>
            </div>
          </div>

          <div v-if="loadingMore" class="cp-state">加载更多…</div>
        </div>

        <footer class="cp-foot">
          <div v-if="replyTarget" class="cp-reply-hint">
            <span class="cp-reply-hint-text">回复 @{{ replyTarget.nickname }}</span>
            <button class="cp-reply-cancel" @click="cancelReply">取消</button>
          </div>
          <RichInput
            ref="richInputEl"
            v-model="draft"
            v-model:images="draftImages"
            :max-images="3"
            :maxlength="1000"
            :placeholder="replyTarget ? '回复 ' + replyTarget.nickname : (auth.isLoggedIn ? '说点什么吧…' : '登录后参与评论')"
            :disabled="submitting || !auth.isLoggedIn"
            :sending="submitting"
            :dark="isPc"
            @submit="submit"
          />
        </footer>
      </section>
    </div>
  </Teleport>
  <ImageViewer
    v-if="viewerOpen"
    :images="viewerImages"
    :index="viewerIndex"
    @close="viewerOpen = false"
  />
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchComments,
  createComment,
  deleteComment,
  likeComment,
  unlikeComment,
  fetchReplies,
  createReply
} from '../api/comments'
import { useAuthStore } from '../stores/auth'
import RichInput from './RichInput.vue'
import ImageViewer from './ImageViewer.vue'

const props = defineProps({
  post: { type: Object, required: true },
  // 通知跳转的定位目标：先定位到 rootId 楼层，再定位到楼层里的 focusCommentId
  focusRootId: { type: Number, default: null },
  focusCommentId: { type: Number, default: null }
})
const emit = defineEmits(['close'])

const REPLY_PAGE = 10
const FLASH_MS = 2000
const FOCUS_MAX_PAGES = 5

const auth = useAuthStore()
const router = useRouter()
const isPc = computed(() => window.innerWidth >= 768)

const listEl = ref(null)
const richInputEl = ref(null)
const comments = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)
const draft = ref('')
const draftImages = ref([])
const submitting = ref(false)
const replyTarget = ref(null)
const viewerImages = ref([])
const viewerIndex = ref(0)
const viewerOpen = ref(false)

const rowEls = {}
const flashKey = ref('')
let flashTimer = null

function setRowRef(key, el) {
  if (el) rowEls[key] = el
  else delete rowEls[key]
}

/** 滚动到目标行并短暂高亮，找不位置就静默放弃 */
function flashRow(key) {
  const el = rowEls[key]
  const list = listEl.value
  if (!el || !list) return
  const listRect = list.getBoundingClientRect()
  const elRect = el.getBoundingClientRect()
  const top = list.scrollTop + (elRect.top - listRect.top) - list.clientHeight / 2 + elRect.height / 2
  list.scrollTo({ top: Math.max(0, top) })
  if (flashTimer) clearTimeout(flashTimer)
  flashKey.value = key
  flashTimer = setTimeout(() => {
    flashKey.value = ''
  }, FLASH_MS)
}

async function focusTarget() {
  const rootId = props.focusRootId
  if (!rootId) return
  let pages = 0
  let root = comments.value.find((c) => c.id === rootId)
  while (!root && hasMore.value && pages < FOCUS_MAX_PAGES) {
    await loadMore()
    pages += 1
    root = comments.value.find((c) => c.id === rootId)
  }
  if (!root) return
  const targetId = props.focusCommentId
  if (!targetId || targetId === rootId) {
    await nextTick()
    flashRow('t' + rootId)
    return
  }
  if (!thread(root).items.some((r) => r.id === targetId)) {
    await loadThread(root, targetId, FOCUS_MAX_PAGES)
  }
  if (!thread(root).items.some((r) => r.id === targetId)) return
  await nextTick()
  flashRow('r' + targetId)
}

const threads = reactive({})
const EMPTY_THREAD = Object.freeze({
  expanded: false, items: [], cursor: null, hasMore: false, loading: false, loadingMore: false
})

function thread(c) {
  return threads[c.id] || EMPTY_THREAD
}

function ensureThread(id) {
  if (!threads[id]) {
    threads[id] = {
      expanded: false, items: [], cursor: null, hasMore: false, loading: false, loadingMore: false
    }
  }
  return threads[id]
}

async function loadFirst() {
  if (loading.value) return
  loading.value = true
  try {
    const data = await fetchComments(props.post.id, null, 20)
    comments.value = data.items || []
    comments.value.forEach((c) => ensureThread(c.id))
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
        ensureThread(item.id)
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

/** 展开楼层；targetId 不为空时持续翻页直到覆盖到这条新回复 */
async function loadThread(c, targetId, maxPages) {
  const st = ensureThread(c.id)
  st.expanded = true
  st.items = []
  st.cursor = null
  st.hasMore = true
  st.loading = true
  let pages = 0
  try {
    while (st.hasMore && pages < maxPages) {
      const data = await fetchReplies(c.id, st.cursor, REPLY_PAGE)
      const items = data.items || []
      const seen = new Set(st.items.map((x) => x.id))
      for (const item of items) {
        if (!seen.has(item.id)) st.items.push(item)
      }
      st.cursor = data.nextCursor || null
      st.hasMore = !!data.hasMore
      pages += 1
      if (targetId && items.some((x) => x.id === targetId)) break
    }
  } catch (e) {
    if (!st.items.length) st.expanded = false
  } finally {
    st.loading = false
  }
}

function expand(c) {
  loadThread(c, null, 1)
}

function collapse(c) {
  const st = ensureThread(c.id)
  st.expanded = false
  st.items = []
  st.cursor = null
  st.hasMore = false
}

async function loadMoreReplies(c) {
  const st = ensureThread(c.id)
  if (st.loadingMore || !st.hasMore) return
  st.loadingMore = true
  try {
    const data = await fetchReplies(c.id, st.cursor, REPLY_PAGE)
    const seen = new Set(st.items.map((x) => x.id))
    for (const item of data.items || []) {
      if (!seen.has(item.id)) st.items.push(item)
    }
    st.cursor = data.nextCursor || null
    st.hasMore = !!data.hasMore
  } catch (e) {
    // 静默，可重试
  } finally {
    st.loadingMore = false
  }
}

function requireLogin() {
  if (!auth.isLoggedIn) {
    location.href = '/login'
    return false
  }
  return true
}

function startReply(target, rootComment) {
  if (!requireLogin()) return
  replyTarget.value = {
    id: target.id,
    nickname: target.author ? target.author.nickname : '拾光用户'
  }
  if (rootComment && !thread(rootComment).expanded) {
    loadThread(rootComment, null, 1)
  }
  nextTick(() => {
    if (richInputEl.value) richInputEl.value.focus()
  })
}

function cancelReply() {
  replyTarget.value = null
}

function openViewer(images, index) {
  viewerImages.value = images || []
  viewerIndex.value = index || 0
  viewerOpen.value = true
}

async function submit() {
  const text = draft.value.trim()
  const images = draftImages.value.filter((img) => !img.uploading && img.object).map((img) => img.object)
  if ((!text && !images.length) || submitting.value) return
  if (!requireLogin()) return
  submitting.value = true
  try {
    if (replyTarget.value) {
      const created = await createReply(replyTarget.value.id, text, images)
      const rootComment = comments.value.find((x) => x.id === created.rootId)
      if (rootComment) {
        await loadThread(rootComment, created.id, 5)
        rootComment.replyCount = (rootComment.replyCount || 0) + 1
      }
      props.post.commentCount = (props.post.commentCount || 0) + 1
      draft.value = ''
      draftImages.value = []
      replyTarget.value = null
    } else {
      const created = await createComment(props.post.id, text, images)
      comments.value.unshift(created)
      ensureThread(created.id)
      props.post.commentCount = (props.post.commentCount || 0) + 1
      draft.value = ''
      draftImages.value = []
      requestAnimationFrame(() => {
        const el = listEl.value
        if (el) el.scrollTop = 0
      })
    }
    if (richInputEl.value) richInputEl.value.closeEmoji()
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

async function confirmDelete(message) {
  try {
    await ElMessageBox.confirm(message, '删除评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    return true
  } catch (e) {
    return false
  }
}

async function removeTop(c) {
  const extra = c.replyCount > 0 ? '该评论下的 ' + c.replyCount + ' 条回复会一并删除。' : ''
  if (!(await confirmDelete('确定删除这条评论吗？' + extra))) return
  try {
    await deleteComment(c.id)
    comments.value = comments.value.filter((x) => x.id !== c.id)
    delete threads[c.id]
    props.post.commentCount = Math.max(0, (props.post.commentCount || 0) - 1 - (c.replyCount || 0))
    ElMessage.success('评论已删除')
  } catch (e) {
    // 错误提示已由拦截器处理
  }
}

async function removeReply(r, rootComment) {
  if (!(await confirmDelete('确定删除这条回复吗？'))) return
  try {
    await deleteComment(r.id)
    const st = ensureThread(rootComment.id)
    st.items = st.items.filter((x) => x.id !== r.id)
    rootComment.replyCount = Math.max(0, (rootComment.replyCount || 0) - 1)
    props.post.commentCount = Math.max(0, (props.post.commentCount || 0) - 1)
    ElMessage.success('回复已删除')
  } catch (e) {
    // 错误提示已由拦截器处理
  }
}

function close() {
  emit('close')
}

/** 点击评论者头像：收起评论面板（历史条目留给返回自然消费），再进入其主页 */
function goUser(author) {
  const id = author && author.id
  if (!id) return
  emit('close', { keepHistory: true })
  router.push('/user/' + id)
}

function onKeydown(e) {
  if (e.key === 'Escape') close()
}

onMounted(() => {
  loadFirst().then(focusTarget)
  window.addEventListener('keydown', onKeydown)
  document.body.style.overflow = 'hidden'
})

onBeforeUnmount(() => {
  if (flashTimer) clearTimeout(flashTimer)
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})

function isPostAuthor(c) {
  const postAuthorId = props.post.author && props.post.author.id
  return postAuthorId != null && c.userId === postAuthorId
}

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

function avatarSrc(c) {
  if (c._avatarBroken) return fallbackAvatar(c)
  return c.author && c.author.avatarUrl ? c.author.avatarUrl : fallbackAvatar(c)
}

function onAvatarError(c) {
  c._avatarBroken = true
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

.cp-state-sm {
  padding: 14px 0;
}

.cp-panel-pc .cp-state {
  color: rgba(255, 255, 255, 0.5);
}

.cp-thread + .cp-thread {
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.cp-panel-pc .cp-thread + .cp-thread {
  border-top-color: rgba(255, 255, 255, 0.06);
}

.cp-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
}

.cp-item-sub {
  padding: 8px 0;
}

.cp-flash {
  padding-left: 8px;
  padding-right: 8px;
  margin-left: -8px;
  margin-right: -8px;
  border-radius: 10px;
  animation: cpFlash 2s ease-out;
}

@keyframes cpFlash {
  0% {
    background: rgba(255, 92, 92, 0.26);
  }
  60% {
    background: rgba(255, 92, 92, 0.16);
  }
  100% {
    background: transparent;
  }
}

.cp-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  object-fit: cover;
  background: #f0e9e0;
  cursor: pointer;
}

.cp-avatar-sm {
  width: 28px;
  height: 28px;
}

.cp-main {
  flex: 1;
  min-width: 0;
}

.cp-meta {
  display: flex;
  align-items: baseline;
  gap: 6px;
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

.cp-badge {
  font-size: 10px;
  line-height: 1;
  padding: 3px 5px;
  border-radius: 4px;
  background: var(--sg-primary-soft, rgba(255, 92, 92, 0.14));
  color: var(--sg-primary-deep, #e04f5f);
  flex-shrink: 0;
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

.cp-item-sub .cp-content {
  font-size: 14px;
}

.cp-at {
  color: var(--sg-primary-deep, #e04f5f);
}

.cp-panel-pc .cp-at {
  color: #ff8a8a;
}

.cp-actions {
  display: flex;
  gap: 14px;
  margin-top: 4px;
}

.cp-act {
  font-size: 12px;
  color: rgba(128, 128, 128, 0.9);
  transition: color 0.2s;
}

.cp-act:hover {
  color: rgba(0, 0, 0, 0.6);
}

.cp-panel-pc .cp-act:hover {
  color: #fff;
}

.cp-act-del:hover {
  color: #e04f5f;
}

.cp-expand,
.cp-collapse {
  font-size: 12px;
  font-weight: 600;
  color: var(--sg-primary-deep, #e04f5f);
  padding: 4px 0 8px 46px;
}

.cp-panel-pc .cp-expand,
.cp-panel-pc .cp-collapse {
  color: #ff8a8a;
}

.cp-expand:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.cp-collapse {
  color: rgba(128, 128, 128, 0.9);
}

.cp-panel-pc .cp-collapse {
  color: rgba(255, 255, 255, 0.45);
}

.cp-replies {
  padding-left: 46px;
  border-left: 2px solid rgba(0, 0, 0, 0.05);
  margin-left: 18px;
  margin-bottom: 6px;
}

.cp-panel-pc .cp-replies {
  border-left-color: rgba(255, 255, 255, 0.08);
}

.cp-expand-sub {
  padding-left: 0;
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

.cp-item-sub .cp-like {
  min-width: 38px;
}

.cp-like.is-liked {
  color: #ff4757;
}

.cp-foot {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0;
  flex-shrink: 0;
}

.cp-panel-pc .cp-foot {
  border-top-color: rgba(255, 255, 255, 0.08);
}

.cp-reply-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 10px 12px 0;
  padding: 6px 12px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.05);
  font-size: 12px;
}

.cp-panel-pc .cp-reply-hint {
  background: rgba(255, 255, 255, 0.08);
}

.cp-reply-hint-text {
  color: rgba(0, 0, 0, 0.55);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cp-panel-pc .cp-reply-hint-text {
  color: rgba(255, 255, 255, 0.6);
}

.cp-reply-cancel {
  font-size: 12px;
  font-weight: 600;
  color: var(--sg-primary-deep, #e04f5f);
  flex-shrink: 0;
}

.cp-imgs {
  display: grid;
  grid-template-columns: repeat(3, 92px);
  gap: 6px;
  margin-top: 8px;
}

.cp-imgs-1 {
  grid-template-columns: 140px;
}

.cp-img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 10px;
  cursor: zoom-in;
  background: rgba(0, 0, 0, 0.06);
}

.cp-item-sub .cp-imgs {
  grid-template-columns: repeat(3, 78px);
}
</style>
