<template>
  <div class="feed-page" :class="{ 'is-pc': isPc, 'has-comment': !!commentPost }">
    <!-- 移动端：全屏竖屏流 + 底部导航 -->
    <template v-if="!isPc">
      <header class="m-header">
        <div class="m-header-tabs">
          <template v-if="scopeLabel">
            <button class="m-header-back" aria-label="返回" @click="onBack">‹</button><span class="m-header-scope">{{ scopeLabel }}</span>
          </template>
          <template v-else>
            <button class="m-header-tab on" @click="goHome">推荐</button><button class="m-header-tab" @click="goFriends">朋友</button>
          </template>
        </div>
        <button class="m-header-search" aria-label="搜索" @click="router.push('/search')">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z"/></svg>
        </button>
      </header>
      <main
        ref="scrollEl"
        class="m-scroll"
        :class="{ booting: !feedReady }"
        @scroll.passive="onScroll"
        @touchstart.passive="onTouchStart"
        @touchend="onTouchEnd"
        @touchcancel="onTouchEnd"
      >
        <template v-if="feed.loading">
          <div v-for="i in 3" :key="i" class="m-skeleton"></div>
        </template>

        <template v-else-if="feed.posts.length">
          <FeedItem
            v-for="(post, i) in feed.posts"
            :key="`${post.id}-${i}`"
            :ref="(el) => setCardRef(i, el)"
            :post="post"
            :active="feedReady && i === currentIndex"
            :warm="feedReady && warmIndexes.includes(i)"
            :init-seek="seekInitFor(post)"
            :resume-frame="resumeFrameFor(post)"
            :class="{ 'item-compact': commentPost && i === currentIndex }"
            @like="feed.toggleLike(post)"
            @favorite="feed.toggleFavorite(post)"
            @comment="onComment(post)"
            @share="onShare(post)"
            @follow="onFollow(post)"
            @author="goAuthor(post)"
            @progress="onVideoProgress"
            @more="onMore(post)"
          />
          <div v-if="!isSingle" class="m-end">
            <span v-if="feed.loadingMore">加载中…</span>
            <span v-else-if="!feed.hasMore">— 没有更多了 —</span>
          </div>
        </template>

        <div v-else-if="feed.error" class="m-empty">
          <p>{{ feed.error }}</p>
          <button class="sg-btn-primary" @click="retryFeed()">点击重试</button>
        </div>
        <div v-else class="m-empty">
          <p>{{ emptyText }}</p>
        </div>
      </main>
      <BottomNav :active="feed.mode === 'friends' ? 'friends' : 'home'" @home="goHome" />
    </template>

    <!-- PC：抖音式一屏一卡，滚轮翻页 -->
    <template v-else>
      <AppSideNav :active="pcActive" :user="auth.user" :unread-count="unreadTotal" :friends-unread="notification.friendsUnread" @home="goHome" />
      <header class="p-header">
        <div class="p-header-tabs">
          <button v-if="scopeLabel" class="p-header-back" @click="onBack" aria-label="返回">‹</button>
          <span v-if="scopeLabel" class="p-header-scope">{{ scopeLabel }}</span>
          <template v-else>
            <button class="p-header-tab" :class="{ on: pcActive === 'home' }" @click="goHome">推荐</button>
            <button class="p-header-tab" :class="{ on: pcActive === 'friends' }" @click="goFriends">朋友</button>
          </template>
        </div>
        <button class="p-search" @click="router.push('/search')"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="10.5" cy="10.5" r="7"/><path d="m16 16 5 5"/></svg><span>搜索你感兴趣的内容</span></button>
      </header>
      <div class="p-viewport" @wheel.prevent="onWheel">
        <div
          class="p-stack"
          :class="{ booting: !feedReady }"
          :style="{ transform: `translateY(-${currentIndex * 100}%)` }"
        >
          <template v-if="feed.loading && !feed.posts.length">
            <div class="p-loading">
              <span class="p-loading-mark">拾</span>
              <p>拾光加载中…</p>
            </div>
          </template>

          <template v-else-if="feed.posts.length">
            <PcFeedCard
              v-for="(post, i) in feed.posts"
              :key="`${post.id}-${i}`"
              :ref="(el) => setCardRef(i, el)"
              :post="post"
              :active="feedReady && i === currentIndex"
              :warm="feedReady && warmIndexes.includes(i)"
              :init-seek="seekInitFor(post)"
              :resume-frame="resumeFrameFor(post)"
              :class="{ 'item-compact': !!commentPost }"
              @like="feed.toggleLike(post)"
              @favorite="feed.toggleFavorite(post)"
              @comment="onComment(post)"
              @share="onShare(post)"
              @follow="onFollow(post)"
              @author="goAuthor(post)"
              @progress="onVideoProgress"
              @more="onMore(post)"
            />
            <div v-if="!isSingle" class="p-end">
              <span v-if="feed.loadingMore">加载中…</span>
              <span v-else-if="!feed.hasMore">— 没有更多了 —</span>
            </div>
          </template>

          <div v-else-if="feed.error" class="p-empty">
            <p>{{ feed.error }}</p>
            <button class="sg-btn-primary" @click="retryFeed()">点击重试</button>
          </div>
          <div v-else class="p-empty">
            <p>{{ emptyText }}</p>
          </div>
        </div>
      </div>

    </template>

    <CommentPanel
      v-if="commentPost"
      :post="commentPost"
      :focus-root-id="focus.rootId"
      :focus-comment-id="focus.commentId"
      @close="closeComment"
    />
    <PostMorePanel
      v-if="morePost"
      :post="morePost"
      @close="closeMore"
      @updated="onPostUpdated"
      @deleted="onPostDeleted"
    />
    <SharePanel
      v-if="sharePost"
      :post="sharePost"
      @close="closeShare"
    />
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
import AppSideNav from '../components/AppSideNav.vue'
import CommentPanel from '../components/CommentPanel.vue'
import PostMorePanel from '../components/PostMorePanel.vue'
import SharePanel from '../components/SharePanel.vue'
import { pushOverlay, popOverlay, dropOverlay } from '../utils/overlayHistory'
import { useNotificationStore } from '../stores/notification'
import { useMessageStore } from '../stores/message'

const route = useRoute()
const router = useRouter()
const feed = useFeedStore()
const auth = useAuthStore()
const notification = useNotificationStore()
const message = useMessageStore()
// 消息入口同时承载通知与私信，红点合并计算
const unreadTotal = computed(() => notification.unread.total + message.unread)
const commentPost = ref(null)
const morePost = ref(null)
const sharePost = ref(null)
/** 通知跳转带来的评论定位参数，交给 CommentPanel 消费 */
const focus = ref({ rootId: null, commentId: null })
const likesOwnerName = ref('')

const scrollEl = ref(null)
const currentIndex = ref(0)
const feedReady = ref(false)
const cardEls = []

function setCardRef(i, el) {
  cardEls[i] = el || undefined
}

const isPc = ref(window.innerWidth >= 768)
// PC 顶栏高亮：朋友动态流归"朋友"，其余（首页/他人主页/点赞/单作品）都归"首页"
const pcActive = computed(() => (route.name === 'friends-feed' ? 'friends' : 'home'))

/** 单作品页（/post/:id）：只放一条，不支持上下滑 */
const isSingle = computed(() => feed.mode === 'single')

const currentPost = computed(() => feed.posts[currentIndex.value] || null)

// 预加载策略：预热当前位置之后最近的两张视频卡（跳过图文卡片）
const warmIndexes = computed(() => {
  const result = []
  let count = 0
  for (let i = currentIndex.value + 1; i < feed.posts.length && count < 2; i++) {
    if (feed.posts[i].type === 'VIDEO') {
      result.push(i)
      count++
    }
  }
  return result
})

const emptyText = computed(() => {
  if (feed.mode === 'single') return '作品不存在或已删除'
  if (feed.mode === 'user') return '该用户还没有发布作品'
  if (feed.mode === 'likes') return '还没有点赞的作品'
  if (feed.mode === 'friends') return '好友还没有发布作品'
  return '还没有作品，去发布第一条拾光吧'
})
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
const FRIENDS_RESUME_KEY = 'sg_friends_resume'

function saveHomeResume(postId, time, frame, index, key = HOME_RESUME_KEY) {
  try {
    sessionStorage.setItem(
      key,
      JSON.stringify({
        postId,
        time: Math.max(0, Math.round((time || 0) * 10) / 10),
        frame: frame || '',
        index: Number.isInteger(index) && index >= 0 ? index : -1
      })
    )
  } catch (e) {
    // 隐私模式等场景下无法写入，忽略即可
  }
}

function takeHomeResume(key = HOME_RESUME_KEY) {
  try {
    const raw = sessionStorage.getItem(key)
    sessionStorage.removeItem(key)
    if (!raw) return null
    try {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed.postId === 'number') {
        return {
          postId: parsed.postId,
          time: Number(parsed.time) || 0,
          frame: typeof parsed.frame === 'string' ? parsed.frame : '',
          index: Number.isInteger(parsed.index) ? parsed.index : -1
        }
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
  // 离开首页/朋友动态去任何页面（个人主页、发布页、设置等）都记住当前位置，返回时回到原处
  const resumeKey = feed.mode === 'home'
    ? (to.path !== '/feed' ? HOME_RESUME_KEY : '')
    : feed.mode === 'friends'
      ? (to.path !== '/friends/feed' ? FRIENDS_RESUME_KEY : '')
      : ''
  if (resumeKey) {
    const post = currentPost.value
    if (post) {
      let frame = ''
      if (post.type === 'VIDEO') {
        const card = cardEls[currentIndex.value]
        frame = (card && typeof card.captureFrame === 'function' ? card.captureFrame() : '') || ''
      }
      saveHomeResume(post.id, post.type === 'VIDEO' ? activeVideoTime.value : 0, frame, currentIndex.value, resumeKey)
    }
  }
  resumeSeek.value = null
})

const scopeLabel = computed(() => {
  if (feed.mode === 'single') {
    const nick = feed.posts[0]?.author?.nickname || ''
    return nick ? nick + ' 的作品' : '作品'
  }
  if (feed.mode === 'likes') {
    const mine = !!auth.userId && feed.scopeUserId === auth.userId
    return (mine ? '我的' : (likesOwnerName.value ? likesOwnerName.value + ' 的' : 'Ta 的')) + '点赞'
  }
  if (feed.mode === 'friends') return '朋友动态'
  if (feed.mode !== 'user') return ''
  const nick = feed.posts[0]?.author?.nickname || ''
  const isMe = !!auth.userId && feed.scopeUserId === auth.userId
  return (isMe ? '我的' : (nick ? nick + ' 的' : '该用户')) + '主页'
})

let resizeHandler = null
let keydownHandler = null
let wheelLocked = false
let wheelTimer = null

function findPostIndex(postId, hintIndex = -1) {
  if (hintIndex < 0) {
    return feed.posts.findIndex((p) => p.id === postId)
  }
  // 轮换阶段同一作品可能出现多次，取离上次停留位置最近的一次
  let found = -1
  let best = Infinity
  feed.posts.forEach((p, i) => {
    const distance = Math.abs(i - hintIndex)
    if (p.id === postId && distance < best) {
      best = distance
      found = i
    }
  })
  return found
}

async function locatePost(postId, hintIndex = -1) {
  const found = findPostIndex(postId, hintIndex)
  if (found >= 0) {
    currentIndex.value = found
    await scrollToIndex(found)
    return
  }
  // 首页里目标可能落在后面的分页中（例如刚发布完列表已重新加载），按需继续向后加载
  if (feed.mode === 'home') {
    let guard = 0
    while (feed.hasMore && guard++ < 6) {
      const before = feed.posts.length
      await feed.loadMore()
      if (feed.posts.length === before) {
        break
      }
      const loaded = findPostIndex(postId, hintIndex)
      if (loaded >= 0) {
        currentIndex.value = loaded
        await scrollToIndex(loaded)
        return
      }
    }
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
  const uid = feed.scopeUserId
  // 发布完成后进入的作品流：返回直接回到个人主页（发布页已用 replace 移出历史）
  if (route.query.from === 'publish') {
    router.replace(uid === auth.userId ? '/me' : '/user/' + uid)
    return
  }
  const state = window.history.state
  if (state && state.back) {
    router.back()
    return
  }
  router.replace(uid === auth.userId ? '/me' : '/user/' + uid)
}

/** 返回条：作品流回主页，单作品页回上一页（通知点进来的就是消息页） */
function onBack() {
  if (isSingle.value) {
    const state = window.history.state
    if (state && state.back) {
      router.back()
      return
    }
    router.replace('/notifications')
    return
  }
  if (feed.mode === 'friends') {
    const state = window.history.state
    if (state && state.back) {
      router.back()
      return
    }
    router.replace('/friends')
    return
  }
  backToProfile()
}

function retryFeed() {
  if (feed.mode === 'single') {
    feed.loadSingle(Number(route.params.id || ''))
  } else if (feed.mode === 'likes') {
    feed.loadLikesFirstPage(feed.scopeUserId)
  } else if (feed.mode === 'user') {
    feed.loadUserFirstPage(feed.scopeUserId)
  } else if (feed.mode === 'friends') {
    feed.loadFriendsFirstPage()
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
  await loadFeedForQuery()
  openCommentFromQuery()
}

async function loadFeedForQuery() {
  // 单作品页：只加载被点开的那一条
  const singleId = Number(route.params.id || '')
  if (singleId) {
    await feed.loadSingle(singleId)
    return
  }
  const postId = Number(route.query.postId || '')
  const userId = Number(route.query.userId || '')
  const likesOf = Number(route.query.likesOf || '')

  // 朋友动态：只含互关好友的作品，按好友发布时间倒序，和首页一样竖屏滑动
  if (route.name === 'friends-feed') {
    if (feed.mode !== 'friends') {
      feed.reset()
      await feed.loadFriendsFirstPage()
    } else if (!feed.posts.length && !feed.loading) {
      await feed.loadFriendsFirstPage()
    }
    // 从个人主页返回时回到刚才那条，不是从第一条重新开始
    const resume = takeHomeResume(FRIENDS_RESUME_KEY)
    if (postId) {
      await locatePost(postId)
    } else if (resume && resume.postId) {
      if (resume.time > 0) {
        resumeSeek.value = { postId: resume.postId, time: resume.time, frame: resume.frame || '' }
      }
      await locatePost(resume.postId, resume.index)
    }
    // 直接打开朋友动态（刷新/外链）也要把红点清掉；先刷新一次避免与开机轮询抢时序
    notification.refreshFriendsUnread().then(() => notification.clearFriendsDot())
    return
  }

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
      await locatePost(resume.postId, resume.index)
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
    const nextIsPc = window.innerWidth >= 768
    if (nextIsPc !== isPc.value) {
      const index = currentIndex.value
      isPc.value = nextIsPc
      nextTick(() => scrollToIndex(index))
      return
    }
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
    if (e.key === ' ' || e.code === 'Space') {
      const el = e.target
      const tag = el && el.tagName ? el.tagName.toLowerCase() : ''
      if (tag === 'input' || tag === 'textarea' || tag === 'select' || (el && el.isContentEditable)) return
      e.preventDefault()
      const card = cardEls[currentIndex.value]
      if (card && typeof card.togglePlay === 'function') card.togglePlay()
      return
    }
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

// 路由变化（主页点作品/底部首页/通知点进另一条作品）时重新初始化
watch(
  () => route.fullPath,
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
  if (snapTimer) clearTimeout(snapTimer)
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
  if (snapPending) scheduleTouchSnap()
}

// —— 触摸吸附兜底：部分手机浏览器（尤其内置浏览器）对 CSS scroll-snap 支持不佳，
// 一滑就划过好几条。这里在滚动停止后补一次吸附，保证每次滑动都停在一条上；
// 原生吸附正常的浏览器里位置本就对齐，直接跳过，不会产生副作用 ——
let touchActive = false
let snapPending = false
let snapTimer = null
let touchStartIndex = 0
let snapTargetIndex = null
let snapTargetAt = 0

function onTouchStart() {
  const el = scrollEl.value
  if (!el || isPc.value) return
  touchActive = true
  if (snapTargetIndex != null && Date.now() - snapTargetAt < 900) {
    // 上一次补吸附还在动画中：以它的目标位置为基准，连续快滑不会被吞掉一步
    touchStartIndex = snapTargetIndex
  } else {
    touchStartIndex = Math.round(el.scrollTop / Math.max(el.clientHeight, 1))
  }
  snapTargetIndex = null
}

function onTouchEnd() {
  if (isPc.value) return
  touchActive = false
  snapPending = true
  scheduleTouchSnap()
}

function scheduleTouchSnap() {
  if (snapTimer) clearTimeout(snapTimer)
  snapTimer = setTimeout(runTouchSnap, 180)
}

function runTouchSnap() {
  snapTimer = null
  const el = scrollEl.value
  if (!el || isPc.value || touchActive || !snapPending) return
  if (commentPost.value || !feedReady.value) return
  snapPending = false
  const h = Math.max(el.clientHeight, 1)
  const current = el.scrollTop / h
  const delta = current - touchStartIndex
  const abs = Math.abs(delta)
  let target = Math.round(current)
  if (abs < 0.15) {
    // 轻微位移：回弹到原卡片
    target = touchStartIndex
  } else if (abs <= 1.6) {
    // 常规滑动：正好前进/后退一条
    target = touchStartIndex + Math.sign(delta)
  }
  const max = Math.max(feed.posts.length - 1, 0)
  target = Math.min(Math.max(target, 0), max)
  const top = target * h
  if (Math.abs(el.scrollTop - top) < 4) return
  snapTargetIndex = target
  snapTargetAt = Date.now()
  if ('scrollBehavior' in document.documentElement.style) {
    el.scrollTo({ top, behavior: 'smooth' })
  } else {
    el.scrollTop = top
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

function goFriends() {
  if (!auth.isLoggedIn) {
    router.push('/login')
    return
  }
  router.push('/friends')
}

// 首页按钮：已在首页流时点击 = 刷新回第一屏；在他人作品流/点赞流时先回到首页
function goHome() {
  const scoped = feed.mode === 'user' || feed.mode === 'likes' || feed.mode === 'friends'
  const deepLinked = !!route.query.postId
  const stayOnHome = feed.mode === 'home' && !scoped && !deepLinked
  // 留在首页（仅刷新）时回滚评论占用的历史条目；要跳去 /feed 则留给返回时自然消费
  closeComment({ keepHistory: !stayOnHome })
  if (stayOnHome) {
    if (!feed.loading) refreshHomeFeed()
    return
  }
  if (scoped) {
    currentIndex.value = 0
    const el = scrollEl.value
    if (el && !isPc.value) el.scrollTo({ top: 0 })
  }
  router.push('/feed')
}

async function refreshHomeFeed() {
  feedReady.value = false
  resumeSeek.value = null
  currentIndex.value = 0
  const el = scrollEl.value
  if (el && !isPc.value) el.scrollTo({ top: 0 })
  try {
    sessionStorage.removeItem(HOME_RESUME_KEY)
  } catch (e) {
    // 隐私模式等场景下无法写入，忽略即可
  }
  feed.reset()
  try {
    await feed.loadFirstPage()
  } finally {
    feedReady.value = true
  }
  if (!feed.error) {
    ElMessage.success('首页已刷新')
  }
}

function goAuthor(post) {
  const author = post.author
  if (!author || !author.id) return
  router.push('/user/' + author.id)
}

/** 评论面板：打开时占一层历史，返回键先关面板（而不是退出首页） */
let commentOverlayId = null

function clearComment() {
  commentPost.value = null
  focus.value = { rootId: null, commentId: null }
}

function onComment(post) {
  if (!auth.isLoggedIn) {
    location.href = '/login'
    return
  }
  focus.value = { rootId: null, commentId: null }
  commentPost.value = post
  if (commentOverlayId == null) {
    commentOverlayId = pushOverlay(() => {
      commentOverlayId = null
      clearComment()
    })
  }
}

/** keepHistory：关闭后马上要跳转/切换面板时，历史条目留给返回时自然消费 */
function closeComment(opts) {
  if (commentOverlayId != null) {
    const id = commentOverlayId
    commentOverlayId = null
    if (opts && opts.keepHistory) dropOverlay(id)
    else popOverlay(id)
  }
  clearComment()
}

/** 来自通知的跳转：?comment=1[&rootId=&commentId=]，定位完成后自动打开评论面板 */
function openCommentFromQuery() {
  if (route.query.comment !== '1') {
    // 从"通知直达评论"的流退回到普通流时，把这个自动打开（非用户点击）的面板收掉
    if (commentOverlayId == null && commentPost.value) clearComment()
    return
  }
  const post = currentPost.value
  if (!post) return
  focus.value = {
    rootId: Number(route.query.rootId || '') || null,
    commentId: Number(route.query.commentId || '') || null
  }
  commentPost.value = post
}

let moreOverlayId = null

function clearMore() {
  morePost.value = null
}

function closeMore(opts) {
  if (moreOverlayId != null) {
    const id = moreOverlayId
    moreOverlayId = null
    if (opts && opts.keepHistory) dropOverlay(id)
    else popOverlay(id)
  }
  morePost.value = null
}

function onMore(post) {
  closeComment({ keepHistory: true })
  morePost.value = post
  // 更多面板同样占一层历史：返回键先关面板
  if (moreOverlayId == null) {
    moreOverlayId = pushOverlay(() => {
      moreOverlayId = null
      clearMore()
    })
  }
}

/** 可见性 / 文案变更：就地更新当前列表里的这条作品 */
function onPostUpdated(patch) {
  const target = feed.posts.find((p) => p.id === patch.id)
  if (target) {
    Object.assign(target, patch)
  }
}

/** 删除作品：从列表移除，停留在同一个位置继续播放下一条 */
function onPostDeleted(patch) {
  closeMore()
  const index = feed.posts.findIndex((p) => p.id === patch.id)
  if (index < 0) {
    return
  }
  feed.posts.splice(index, 1)
  if (currentIndex.value > feed.posts.length - 1) {
    currentIndex.value = Math.max(0, feed.posts.length - 1)
  }
  nextTick(() => {
    scrollToIndex(currentIndex.value)
  })
}

let shareOverlayId = null

function clearShare() {
  sharePost.value = null
}

function closeShare(opts) {
  if (shareOverlayId != null) {
    const id = shareOverlayId
    shareOverlayId = null
    if (opts && opts.keepHistory) dropOverlay(id)
    else popOverlay(id)
  }
  sharePost.value = null
}

function onShare(post) {
  sharePost.value = post
  // 分享面板占一层历史：返回键先关面板
  if (shareOverlayId == null) {
    shareOverlayId = pushOverlay(() => {
      shareOverlayId = null
      clearShare()
    })
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

</script>

<style scoped>
.feed-page {
  height: var(--sg-vh, 100%);
  display: flex;
  flex-direction: column;
}

/* 移动端 */
.m-scroll {
  flex: 1;
  min-height: 0;
  height: calc(100% - 58px - var(--sg-nav-h) - env(safe-area-inset-bottom));
  margin-bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom));
  overflow-y: auto;
  scroll-snap-type: y mandatory;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
}

.m-header { position: relative; z-index: 2200; height: 58px; flex: none; display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 0 20px; background: var(--sg-card); border-bottom: 1px solid var(--sg-line); }
.m-header-tabs { display: flex; height: 100%; align-items: center; gap: 20px; min-width: 0; }
.m-header-tab { height: 100%; position: relative; color: var(--sg-text-3); font-size: 13px; }
.m-header-tab.on { color: var(--sg-text); font-weight: 600; }
.m-header-tab.on::after { content: ''; position: absolute; height: 3px; width: 14px; left: calc(50% - 7px); bottom: 7px; background: var(--sg-primary); border-radius: 3px; }
.m-header-back { font-size: 30px; line-height: 1; color: var(--sg-text-2); }
.m-header-scope { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-header-search { display: grid; place-items: center; width: 30px; height: 30px; color: var(--sg-text-2); flex: none; }

/* 开机定位阶段：关掉滚动吸附，避免恢复观看位置时被吸附动画带着滑过去 */
.m-scroll.booting {
  scroll-snap-type: none;
}


.m-scroll > .feed-item,
.m-scroll > .m-skeleton {
  /* 跟随滚动容器可视高度，而不是含浏览器地址栏的整屏，避免真机上内容偏下 */
  height: 100%;
}

/* 甩动时最多只吸附一条，避免一滑划过好几条（支持该属性的浏览器生效） */
.m-scroll > .feed-item {
  scroll-snap-stop: always;
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
  background: var(--sg-bg);
  display: flex;
  flex-direction: column;
  padding-left: var(--sg-sidebar-w);
}

.p-header { position: relative; z-index: 2200; height: 77px; flex: none; padding: 0 35px; display: flex; align-items: center; justify-content: space-between; gap: 16px; border-bottom: 1px solid var(--sg-line); }
.p-header-tabs { display: flex; align-items: center; gap: 25px; height: 100%; min-width: 0; }
.p-header-tab { position: relative; height: 100%; padding: 0 2px; color: var(--sg-text-3); font-size: 14px; }
.p-header-tab.on { color: var(--sg-text); font-weight: 600; }
.p-header-tab.on::after { content: ''; position: absolute; bottom: 15px; left: 25%; width: 50%; height: 3px; border-radius: 3px; background: var(--sg-primary); }
.p-header-back { font-size: 30px; color: var(--sg-text-2); line-height: 1; }
.p-header-scope { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; font-weight: 600; }
.p-search { display: flex; align-items: center; gap: 10px; padding: 9px 16px; border-radius: 20px; border: 1px solid var(--sg-line); background: var(--sg-card); color: var(--sg-text-3); font-size: 11px; flex: none; }

.p-viewport {
  flex: 1;
  min-width: 0;
  height: calc(100% - 77px);
  overflow: hidden;
  background: var(--sg-bg);
}

/* 评论打开时：视频区等比例压缩靠左，评论区占右侧 */
.has-comment .p-viewport {
  margin-right: var(--sg-comment-w);
  transition: margin-right 0.35s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.p-stack {
  height: 100%;
  display: flex;
  flex-direction: column;
  will-change: transform;
  transition: transform 0.45s cubic-bezier(0.22, 0.61, 0.36, 1);
}

/* 开机定位阶段：关掉过渡，返回首页时直接停在刚才那条，而不是从第一条滑过去 */
.p-stack.booting {
  transition: none;
}

.p-stack > .pc-slide,
.p-stack > .p-end,
.p-stack > .p-loading,
.p-stack > .p-empty {
  height: 100%;
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

</style>
