<template>
  <div class="feed-page" :class="{ 'is-pc': isPc, 'has-comment': !!commentPost }">
    <!-- 移动端：全屏竖屏流 + 底部导航 -->
    <div v-if="feed.mode === 'user' || feed.mode === 'likes'" class="m-back-bar">
      <button class="m-back-btn" @click="backToProfile">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        返回
      </button>
      <span class="m-back-title">{{ scopeLabel }}</span>
    </div>
    <template v-if="!isPc">
      <main ref="scrollEl" class="m-scroll" @scroll.passive="onScroll">
        <template v-if="feed.loading">
          <div v-for="i in 3" :key="i" class="m-skeleton"></div>
        </template>

        <template v-else-if="feed.posts.length">
          <FeedItem
            v-for="(post, i) in feed.posts"
            :key="post.id"
            :ref="(el) => setCardRef(i, el)"
            :post="post"
            :active="feedReady && i === currentIndex"
            :init-seek="seekInitFor(post)"
            :resume-frame="resumeFrameFor(post)"
            :class="{ 'item-compact': commentPost && i === currentIndex }"
            @like="feed.toggleLike(post)"
            @comment="onComment(post)"
            @share="onShare(post)"
            @follow="onFollow(post)"
            @author="goAuthor(post)"
            @progress="onVideoProgress"
          />
          <div class="m-end">
            <span v-if="feed.loadingMore">加载中…</span>
            <span v-else-if="!feed.hasMore">— 没有更多了 —</span>
          </div>
        </template>

        <div v-else-if="feed.error" class="m-empty">
          <p>{{ feed.error }}</p>
          <button class="sg-btn-primary" @click="retryFeed()">点击重试</button>
        </div>
        <div v-else class="m-empty">
          <p>{{ feed.mode === 'user' ? '该用户还没有发布作品' : feed.mode === 'likes' ? '还没有点赞的作品' : '还没有作品，去发布第一条拾光吧' }}</p>
        </div>
      </main>
      <BottomNav @me="goMe" />
    </template>

    <!-- PC：抖音式一屏一卡，滚轮翻页 -->
    <template v-else>
      <div class="p-viewport" @wheel.prevent="onWheel">
        <div class="p-stack" :style="{ transform: `translateY(-${currentIndex * 100}vh)` }">
          <template v-if="feed.loading && !feed.posts.length">
            <div class="p-loading">
              <span class="p-loading-mark">拾</span>
              <p>拾光加载中…</p>
            </div>
          </template>

          <template v-else-if="feed.posts.length">
            <PcFeedCard
              v-for="(post, i) in feed.posts"
              :key="post.id"
              :ref="(el) => setCardRef(i, el)"
              :post="post"
              :active="feedReady && i === currentIndex"
              :init-seek="seekInitFor(post)"
              :resume-frame="resumeFrameFor(post)"
              :class="{ 'item-compact': !!commentPost }"
              @like="feed.toggleLike(post)"
              @comment="onComment(post)"
              @share="onShare(post)"
              @follow="onFollow(post)"
              @author="goAuthor(post)"
              @progress="onVideoProgress"
            />
            <div class="p-end">
              <span v-if="feed.loadingMore">加载中…</span>
              <span v-else-if="!feed.hasMore">— 没有更多了 —</span>
            </div>
          </template>

          <div v-else-if="feed.error" class="p-empty">
            <p>{{ feed.error }}</p>
            <button class="sg-btn-primary" @click="retryFeed()">点击重试</button>
          </div>
          <div v-else class="p-empty">
            <p>{{ feed.mode === 'user' ? '该用户还没有发布作品' : feed.mode === 'likes' ? '还没有点赞的作品' : '还没有作品，去发布第一条拾光吧' }}</p>
          </div>
        </div>
      </div>

      <!-- 顶部迷你导航 -->
      <nav class="p-topbar">
        <span class="p-logo">拾光</span>
        <button class="p-nav-btn on" @click="router.push('/feed')">首页</button>
        <button class="p-nav-btn" @click="router.push('/publish')">发布</button>
        <button class="p-nav-btn" @click="todo('消息')">消息</button>
        <button class="p-nav-btn" @click="goMe">我的</button>
      </nav>
    </template>

    <CommentPanel v-if="commentPost" :post="commentPost" @close="commentPost = null" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useFeedStore } from '../stores/feed'
import { useAuthStore } from '../stores/auth'
import { followUser, unfollowUser } from '../api/user'
import { fetchPostDetail } from '../api/posts'
import { fetchProfile } from '../api/user'
import FeedItem from '../components/mobile/FeedItem.vue'
import BottomNav from '../components/mobile/BottomNav.vue'
import PcFeedCard from '../components/pc/PcFeedCard.vue'
import CommentPanel from '../components/CommentPanel.vue'

const route = useRoute()
const router = useRouter()
const feed = useFeedStore()
const auth = useAuthStore()
const commentPost = ref(null)
const likesOwnerName = ref('')

const scrollEl = ref(null)
const currentIndex = ref(0)
const feedReady = ref(false)
const cardEls = []

function setCardRef(i, el) {
  cardEls[i] = el || undefined
}

const isPc = computed(() => window.innerWidth >= 768)

const currentPost = computed(() => feed.posts[currentIndex.value] || null)
const activeVideoTime = ref(0)
const resumeSeek = ref(null)

function onVideoProgress(t) {
  if (typeof t === 'number' && isFinite(t) && t > 0) {
    activeVideoTime.value = t
  }
}

function seekInitFor(post) {
  const r = resumeSeek.value
  return r && r.postId === post.id ? r.time : 0
}

function resumeFrameFor(post) {
  const r = resumeSeek.value
  return r && r.postId === post.id && r.frame ? r.frame : ''
}

// 首页流位置记忆：进入个人主页前记住当前作品，返回首页后恢复（避免总从第一条开始）
const HOME_RESUME_KEY = 'sg_home_resume'

function saveHomeResume(postId, time, frame) {
  try {
    sessionStorage.setItem(
      HOME_RESUME_KEY,
      JSON.stringify({ postId, time: Math.max(0, Math.round((time || 0) * 10) / 10), frame: frame || '' })
    )
  } catch (e) {
    // 隐私模式等场景下无法写入，忽略即可
  }
}

function takeHomeResume() {
  try {
    const raw = sessionStorage.getItem(HOME_RESUME_KEY)
    sessionStorage.removeItem(HOME_RESUME_KEY)
    if (!raw) return null
    try {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed.postId === 'number') {
        return { postId: parsed.postId, time: Number(parsed.time) || 0, frame: typeof parsed.frame === 'string' ? parsed.frame : '' }
      }
      return null
    } catch (e) {
      const legacy = Number(raw)
      return legacy ? { postId: legacy, time: 0, frame: '' } : null
    }
  } catch (e) {
    return null
  }
}

onBeforeRouteLeave((to) => {
  if (feed.mode === 'home' && (to.path === '/me' || to.path.startsWith('/user/'))) {
    const post = currentPost.value
    if (post) {
      let frame = ''
      if (post.type === 'VIDEO') {
        const card = cardEls[currentIndex.value]
        frame = (card && typeof card.captureFrame === 'function' ? card.captureFrame() : '') || ''
      }
      saveHomeResume(post.id, post.type === 'VIDEO' ? activeVideoTime.value : 0, frame)
    }
  }
  resumeSeek.value = null
})

const scopeLabel = computed(() => {
  if (feed.mode === 'likes') {
    const mine = !!auth.userId && feed.scopeUserId === auth.userId
    return (mine ? '我的' : (likesOwnerName.value ? likesOwnerName.value + ' 的' : 'Ta 的')) + '点赞'
  }
  if (feed.mode !== 'user') return ''
  const nick = feed.posts[0]?.author?.nickname || ''
  const isMe = !!auth.userId && feed.scopeUserId === auth.userId
  return (isMe ? '我的' : (nick ? nick + ' 的' : '该用户')) + '主页'
})

let resizeHandler = null
let keydownHandler = null
let wheelLocked = false
let wheelTimer = null

async function locatePost(postId) {
  const found = feed.posts.findIndex((p) => p.id === postId)
  if (found >= 0) {
    currentIndex.value = found
    await scrollToIndex(found)
    return
  }
  if ((feed.mode === 'user' || feed.mode === 'likes') && feed.posts.length) {
    // 用户/点赞模式下未找到（作品可能不在第一页），先尝试精确加载该作品
    const detail = await fetchPostDetail(postId).catch(() => null)
    if (detail) {
      feed.posts.unshift(detail)
      currentIndex.value = 0
      return
    }
    currentIndex.value = 0
    await scrollToIndex(0)
    return
  }
  try {
    const detail = await fetchPostDetail(postId)
    if (detail) {
      feed.posts.unshift(detail)
      currentIndex.value = 0
    }
  } catch (e) {
    // 作品不存在或已删除，忽略
  }
}

function scrollToIndex(index) {
  return nextTick(() => {
    const el = scrollEl.value
    if (el && !isPc.value) {
      el.scrollTo({ top: index * Math.max(el.clientHeight, 1) })
    }
  })
}

function backToProfile() {
  const state = window.history.state
  if (state && state.back) {
    router.back()
    return
  }
  const uid = feed.scopeUserId
  router.replace(uid === auth.userId ? '/me' : '/user/' + uid)
}

function retryFeed() {
  if (feed.mode === 'likes') {
    feed.loadLikesFirstPage(feed.scopeUserId)
  } else if (feed.mode === 'user') {
    feed.loadUserFirstPage(feed.scopeUserId)
  } else {
    feed.loadFirstPage()
  }
}

async function loadLikesOwnerName(userId) {
  try {
    const data = await fetchProfile(userId)
    likesOwnerName.value = data.nickname || ''
  } catch (e) {
    likesOwnerName.value = ''
  }
}


async function initFeed() {
  const postId = Number(route.query.postId || '')
  const userId = Number(route.query.userId || '')
  const likesOf = Number(route.query.likesOf || '')

  // 点赞列表入口：进入该用户的点赞列表流并定位到对应作品
  if (likesOf) {
    if (feed.mode !== 'likes' || feed.scopeUserId !== likesOf) {
      feed.reset()
      await Promise.all([feed.loadLikesFirstPage(likesOf), loadLikesOwnerName(likesOf)])
    }
    if (postId) {
      await locatePost(postId)
    }
    return
  }

  // 来自个人主页：只加载该用户的视频
  if (userId) {
    if (feed.mode !== 'user' || feed.scopeUserId !== userId) {
      feed.reset()
      await feed.loadUserFirstPage(userId)
    }
    if (postId) {
      await locatePost(postId)
    }
  } else {
    if (feed.mode !== 'home') {
      feed.reset()
      await feed.loadFirstPage()
    } else if (!feed.posts.length && !feed.loading) {
      await feed.loadFirstPage()
    }
    // 无论是否带 postId 都先消费记忆的位置，避免残留
    const resume = takeHomeResume()
    if (postId) {
      await locatePost(postId)
    } else if (resume && resume.postId) {
      if (resume.time > 0) {
        resumeSeek.value = { postId: resume.postId, time: resume.time, frame: resume.frame || '' }
      }
      await locatePost(resume.postId)
    }
  }
}

onMounted(async () => {
  try {
    await initFeed()
  } finally {
    feedReady.value = true
  }
  // 真机上布局视口高度会包含浏览器地址栏，用 visualViewport 的真实可视高度驱动卡片尺寸
  const syncViewportHeight = () => {
    const h = window.visualViewport ? window.visualViewport.height : window.innerHeight
    document.documentElement.style.setProperty('--sg-vh', h + 'px')
  }
  syncViewportHeight()
  window.visualViewport?.addEventListener('resize', syncViewportHeight)
  window.visualViewport?.addEventListener('scroll', syncViewportHeight)
  window.__syncSgVh = syncViewportHeight
  resizeHandler = () => {
    if (!isPc.value) {
      const el = scrollEl.value
      if (el) {
        const index = Math.round(el.scrollTop / Math.max(el.clientHeight, 1))
        currentIndex.value = Math.min(Math.max(index, 0), feed.posts.length - 1)
      }
    }
  }
  window.addEventListener('resize', resizeHandler)

  keydownHandler = (e) => {
    if (!isPc.value) return
    if (e.key === 'ArrowDown' || e.key === 'PageDown') {
      e.preventDefault()
      goNext()
    } else if (e.key === 'ArrowUp' || e.key === 'PageUp') {
      e.preventDefault()
      goPrev()
    }
  }
  window.addEventListener('keydown', keydownHandler)
})

// 路由参数变化（主页点作品/底部首页）时重新初始化
watch(
  () => route.query,
  () => {
    feedReady.value = false
    initFeed().finally(() => {
      feedReady.value = true
    })
  }
)

onBeforeUnmount(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  if (keydownHandler) window.removeEventListener('keydown', keydownHandler)
  if (wheelTimer) clearTimeout(wheelTimer)
  window.visualViewport?.removeEventListener('resize', window.__syncSgVh)
  window.visualViewport?.removeEventListener('scroll', window.__syncSgVh)
  delete window.__syncSgVh
})

watch(isPc, () => {
  nextTick(() => {
    currentIndex.value = 0
  })
})

function onScroll() {
  const el = scrollEl.value
  if (!el || isPc.value) return
  const itemHeight = Math.max(el.clientHeight, 1)
  const index = Math.round(el.scrollTop / itemHeight)
  currentIndex.value = Math.min(Math.max(index, 0), feed.posts.length - 1)
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 600) {
    feed.loadMore()
  }
}

function onWheel(e) {
  if (wheelLocked) return
  wheelLocked = true
  if (wheelTimer) clearTimeout(wheelTimer)
  wheelTimer = setTimeout(() => {
    wheelLocked = false
  }, 650)
  if (e.deltaY > 0) {
    goNext()
  } else if (e.deltaY < 0) {
    goPrev()
  }
}

function goNext() {
  const total = feed.posts.length
  if (!total || currentIndex.value >= total - 1) return
  currentIndex.value += 1
  if (currentIndex.value >= total - 2) {
    feed.loadMore()
  }
}

function goPrev() {
  if (currentIndex.value <= 0) return
  currentIndex.value -= 1
}

function goMe() {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  router.push('/me')
}

function goAuthor(post) {
  const author = post.author
  if (!author || !author.id) return
  router.push('/user/' + author.id)
}

function onComment(post) {
  if (!auth.isLoggedIn) {
    location.href = '/login'
    return
  }
  commentPost.value = post
}

async function onShare(post) {
  try {
    const url = location.origin + '/feed'
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制，快去分享吧')
  } catch (e) {
    ElMessage.info('复制失败，请手动复制地址')
  }
}

async function onFollow(post) {
  if (!auth.isLoggedIn) {
    location.href = '/login'
    return
  }
  const author = post.author
  if (!author) return
  try {
    const data = author.following ? await unfollowUser(author.id) : await followUser(author.id)
    author.following = data.following
    ElMessage.success(data.following ? '已关注' : '已取消关注')
  } catch (e) {
    // 错误提示已由拦截器处理
  }
}

function todo(label) {
  ElMessage.info(`${label}功能开发中，敬请期待`)
}
</script>

<style scoped>
.feed-page {
  height: var(--sg-vh, 100%);
  display: flex;
}

/* 移动端 */
.m-scroll {
  flex: 1;
  height: 100%;
  overflow-y: auto;
  scroll-snap-type: y mandatory;
  -webkit-overflow-scrolling: touch;
}

/* 用户作品流：返回条 */
.m-back-bar {
  position: fixed;
  top: 12px;
  left: 12px;
  z-index: 120;
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  color: #fff;
  font-size: 13px;
  border: 1px solid rgba(255, 255, 255, 0.14);
}

.m-back-btn {
  display: flex;
  align-items: center;
  gap: 3px;
  color: #fff;
  font-weight: 600;
}

.m-back-title {
  color: rgba(255, 255, 255, 0.85);
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.m-scroll > .feed-item,
.m-scroll > .m-skeleton {
  /* 跟随滚动容器可视高度，而不是含浏览器地址栏的整屏，避免真机上内容偏下 */
  height: 100%;
}

/* 评论打开时：锁定滚动，避免压缩当前卡片时跳动 */
.has-comment .m-scroll {
  scroll-snap-type: none;
  overflow-y: hidden;
}

.m-skeleton {
  background: linear-gradient(160deg, #efe7de, #e4d8cc);
}

.m-end {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sg-text-3);
  font-size: 13px;
  background: var(--sg-bg);
}

.m-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--sg-text-2);
}

/* PC 端：抖音式一屏一卡 */
.feed-page.is-pc {
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: #0b0b0e;
}

.p-viewport {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  background: #0b0b0e;
}

/* 评论打开时：视频区等比例压缩靠左，评论区占右侧 */
.has-comment .p-viewport {
  margin-right: 440px;
  transition: margin-right 0.35s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.p-stack {
  height: 100%;
  display: flex;
  flex-direction: column;
  will-change: transform;
  transition: transform 0.45s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.p-stack > .pc-slide,
.p-stack > .p-end,
.p-stack > .p-loading,
.p-stack > .p-empty {
  height: 100vh;
  height: 100dvh;
  flex-shrink: 0;
}

.p-loading,
.p-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  color: rgba(255, 255, 255, 0.65);
  background: #0b0b0e;
}

.p-loading-mark {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: var(--sg-gradient-deep);
  color: #fff;
  font-size: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: sg-pulse 1.4s ease-in-out infinite;
}

.p-end {
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  background: #0b0b0e;
}

/* 顶部迷你导航 */
.p-topbar {
  position: fixed;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: var(--sg-radius-full);
  background: rgba(16, 15, 18, 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.35);
}

.p-back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  transition: background 0.2s;
}

.p-back-btn:hover {
  background: rgba(255, 255, 255, 0.24);
}

.p-logo {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 2px;
  margin: 0 10px 0 8px;
  background: var(--sg-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.p-nav-btn {
  padding: 6px 14px;
  border-radius: var(--sg-radius-full);
  color: rgba(255, 255, 255, 0.75);
  font-size: 13px;
  transition: background 0.2s, color 0.2s;
}

.p-nav-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.p-nav-btn.on {
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  font-weight: 600;
}
</style>
