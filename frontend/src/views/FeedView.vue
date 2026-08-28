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

    <!-- PC：三栏布局 -->
    <template v-else>
      <PcSideNav />
      <main ref="scrollEl" class="p-scroll" @scroll.passive="onScroll">
        <template v-if="feed.loading">
          <div v-for="i in 2" :key="i" class="p-skeleton sg-card"></div>
        </template>

        <template v-else-if="feed.posts.length">
          <PcFeedCard
            v-for="post in feed.posts"
            :key="post.id"
            :post="post"
            @select="selectPost"
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
      </main>
      <PcRightPanel
        :post="currentPost"
        :posts="feed.posts"
        @like="feed.toggleLike(currentPost)"
        @comment="onComment(currentPost)"
        @share="onShare(currentPost)"
        @select="scrollToPost"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useFeedStore } from '../stores/feed'
import { useAuthStore } from '../stores/auth'
import { followUser, unfollowUser } from '../api/user'
import FeedItem from '../components/mobile/FeedItem.vue'
import BottomNav from '../components/mobile/BottomNav.vue'
import PcSideNav from '../components/pc/PcSideNav.vue'
import PcFeedCard from '../components/pc/PcFeedCard.vue'
import PcRightPanel from '../components/pc/PcRightPanel.vue'

const feed = useFeedStore()
const auth = useAuthStore()

const scrollEl = ref(null)
const currentIndex = ref(0)

const isPc = computed(() => window.innerWidth >= 768)

const currentPost = computed(() => feed.posts[currentIndex.value] || null)

let resizeHandler = null

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
})

onBeforeUnmount(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
})

watch(isPc, () => {
  nextTick(() => {
    if (scrollEl.value && currentPost.value) {
      scrollEl.value.scrollTop = 0
      currentIndex.value = 0
    }
  })
})

function onScroll() {
  const el = scrollEl.value
  if (!el) return
  if (!isPc.value) {
    const itemHeight = Math.max(el.clientHeight, 1)
    const index = Math.round(el.scrollTop / itemHeight)
    currentIndex.value = Math.min(Math.max(index, 0), feed.posts.length - 1)
  } else {
    // PC: card nearest to viewport center becomes current
    const center = el.scrollTop + el.clientHeight / 2
    let best = 0
    let bestDist = Infinity
    Array.from(el.children).forEach((child, i) => {
      if (i >= feed.posts.length) return
      const mid = child.offsetTop + child.offsetHeight / 2
      const dist = Math.abs(mid - center)
      if (dist < bestDist) {
        bestDist = dist
        best = i
      }
    })
    currentIndex.value = best
  }
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 600) {
    feed.loadMore()
  }
}

function scrollToPost(post) {
  if (!isPc.value) return
  const el = scrollEl.value
  if (!el) return
  const index = feed.posts.findIndex((p) => p.id === post.id)
  if (index < 0) return
  currentIndex.value = index
  const card = el.children[index]
  if (card) {
    el.scrollTo({ top: card.offsetTop - 12, behavior: 'smooth' })
  }
}

function selectPost(post) {
  const index = feed.posts.findIndex((p) => p.id === post.id)
  if (index >= 0) currentIndex.value = index
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

/* PC 端 */
.feed-page.is-pc {
  background: var(--sg-gradient);
  min-height: 100%;
}

.p-scroll {
  flex: 1;
  max-width: 640px;
  min-width: 0;
  overflow-y: auto;
  padding: 18px 22px 40px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.p-skeleton {
  height: 420px;
  animation: sg-pulse 1.4s ease-in-out infinite;
}

.p-end {
  text-align: center;
  color: var(--sg-text-3);
  font-size: 13px;
  padding: 8px 0;
}

.p-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--sg-text-2);
}
</style>