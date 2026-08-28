<template>
  <div class="feed-page" :class="{ 'is-pc': isPc }">
    <!-- 移动端：全屏竖屏流 + 底部导航 -->
    <template v-if="!isPc">
      <main ref="scrollEl" class="m-scroll" @scroll.passive="onScroll">
        <template v-if="feed.loading">
          <div v-for="i in 3" :key="i" class="m-skeleton"></div>
        </template>

        <template v-else-if="feed.posts.length">
          <FeedItem
            v-for="(post, i) in feed.posts"
            :key="post.id"
            :post="post"
            :active="i === currentIndex"
            @like="feed.toggleLike(post)"
            @comment="onComment(post)"
            @share="onShare(post)"
            @follow="onFollow(post)"
          />
          <div class="m-end">
            <span v-if="feed.loadingMore">加载中…</span>
            <span v-else-if="!feed.hasMore">— 没有更多了 —</span>
          </div>
        </template>

        <div v-else-if="feed.error" class="m-empty">
          <p>{{ feed.error }}</p>
          <button class="sg-btn-primary" @click="feed.loadFirstPage()">点击重试</button>
        </div>
        <div v-else class="m-empty">
          <p>还没有作品，去发布第一条拾光吧</p>
        </div>
      </main>
      <BottomNav />
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
              :post="post"
              :active="i === currentIndex"
              @like="feed.toggleLike(post)"
              @comment="onComment(post)"
              @share="onShare(post)"
              @follow="onFollow(post)"
            />
            <div class="p-end">
              <span v-if="feed.loadingMore">加载中…</span>
              <span v-else-if="!feed.hasMore">— 没有更多了 —</span>
            </div>
          </template>

          <div v-else-if="feed.error" class="p-empty">
            <p>{{ feed.error }}</p>
            <button class="sg-btn-primary" @click="feed.loadFirstPage()">点击重试</button>
          </div>
          <div v-else class="p-empty">
            <p>还没有作品，去发布第一条拾光吧</p>
          </div>
        </div>
      </div>

      <!-- 顶部迷你导航 -->
      <nav class="p-topbar">
        <span class="p-logo">拾光</span>
        <button class="p-nav-btn on" @click="router.push('/feed')">首页</button>
        <button class="p-nav-btn" @click="todo('发布')">发布</button>
        <button class="p-nav-btn" @click="todo('消息')">消息</button>
        <button class="p-nav-btn" @click="todo('我的')">我的</button>
      </nav>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useFeedStore } from '../stores/feed'
import { useAuthStore } from '../stores/auth'
import { followUser, unfollowUser } from '../api/user'
import FeedItem from '../components/mobile/FeedItem.vue'
import BottomNav from '../components/mobile/BottomNav.vue'
import PcFeedCard from '../components/pc/PcFeedCard.vue'

const router = useRouter()
const feed = useFeedStore()
const auth = useAuthStore()

const scrollEl = ref(null)
const currentIndex = ref(0)

const isPc = computed(() => window.innerWidth >= 768)

const currentPost = computed(() => feed.posts[currentIndex.value] || null)

let resizeHandler = null
let keydownHandler = null
let wheelLocked = false
let wheelTimer = null

onMounted(async () => {
  if (!feed.posts.length && !feed.loading) {
    await feed.loadFirstPage()
  }
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

onBeforeUnmount(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  if (keydownHandler) window.removeEventListener('keydown', keydownHandler)
  if (wheelTimer) clearTimeout(wheelTimer)
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

function onComment(post) {
  ElMessage.info('评论面板开发中，下一步上线')
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
  height: 100%;
  display: flex;
}

/* 移动端 */
.m-scroll {
  flex: 1;
  height: 100%;
  overflow-y: auto;
  scroll-snap-type: y mandatory;
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
}

.m-scroll > .feed-item,
.m-scroll > .m-skeleton {
  height: 100vh;
  height: 100dvh;
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