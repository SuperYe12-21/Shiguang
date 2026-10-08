<template>
  <div class="hi" :class="{ 'is-pc': isPc }">
    <!-- PC 顶栏 -->
    <nav v-if="isPc" class="hi-pc-top">
      <span class="hi-pc-logo">观看历史</span>
      <button class="hi-pc-back" @click="goBack">返回</button>
      <span class="hi-pc-space"></span>
      <button v-if="items.length" class="hi-pc-clear" @click="clearAll">清空历史</button>
    </nav>

    <header v-else class="hi-top">
      <button class="hi-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
        返回
      </button>
      <h1 class="hi-title">观看历史</h1>
      <button v-if="items.length" class="hi-clear" @click="clearAll">清空</button>
      <span v-else class="hi-top-space"></span>
    </header>

    <main ref="bodyEl" class="hi-body" @scroll.passive="onScroll">
      <div v-if="loading && !items.length" class="hi-state">加载中…</div>

      <div v-else-if="!items.length" class="hi-empty">
        <span class="hi-empty-ico">
          <svg viewBox="0 0 24 24" width="30" height="30" fill="currentColor"><path d="M12 3a9 9 0 1 0 9 9h-2a7 7 0 1 1-7-7v3l4-4-4-4v3zm1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H13z"/></svg>
        </span>
        <p class="hi-empty-title">还没有观看记录</p>
        <p class="hi-empty-desc">看过的视频和图文会出现在这里，方便随时找回</p>
        <button class="hi-empty-btn" @click="router.push('/feed')">去逛逛</button>
      </div>

      <template v-else>
        <div class="hi-grid">
          <div v-for="p in items" :key="p.id" class="hi-cell" @click="goPost(p)">
            <img class="hi-cover" :src="p.coverUrl || (p.images && p.images[0]) || ''" :alt="p.title || '作品'" loading="lazy" />
            <span v-if="p.type === 'VIDEO'" class="hi-play">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
            </span>
            <span class="hi-likes">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
              {{ formatCount(p.likeCount) }}
            </span>
            <span class="hi-author">{{ p.author?.nickname || '拾光用户' }}</span>
          </div>
        </div>

        <div class="hi-more">
          <span v-if="loadingMore">加载中…</span>
          <span v-else-if="!hasMore">— 没有更多了 —</span>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearHistory, fetchHistory } from '../api/posts'

const router = useRouter()

const isPc = ref(typeof window !== 'undefined' ? window.innerWidth >= 768 : false)
const bodyEl = ref(null)
const items = ref([])
const cursor = ref(null)
const hasMore = ref(true)
const loading = ref(false)
const loadingMore = ref(false)

function formatCount(n) {
  const v = Number(n) || 0
  if (v >= 10000) return (v / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (v >= 1000) return (v / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(v)
}

async function load(first = false) {
  if (loading.value || (!first && (!hasMore.value || loadingMore.value))) return
  if (first) {
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const data = await fetchHistory(first ? '' : cursor.value, 20)
    const list = data.items || []
    const seen = new Set(items.value.map((x) => x.id))
    for (const p of list) {
      if (!seen.has(p.id)) {
        items.value.push(p)
        seen.add(p.id)
      }
    }
    cursor.value = data.nextCursor || null
    hasMore.value = !!data.hasMore
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function onScroll() {
  const el = bodyEl.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 320) load(false)
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('清空后不可恢复，确定要清空全部观看历史吗？', '清空观看历史', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await clearHistory()
    items.value = []
    cursor.value = null
    hasMore.value = false
    ElMessage.success('已清空观看历史')
  } catch (e) {
    // 错误提示由拦截器处理
  }
}

function goPost(p) {
  router.push('/post/' + p.id)
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/me')
  }
}

function syncIsPc() {
  isPc.value = window.innerWidth >= 768
}

onMounted(() => {
  load(true)
  window.addEventListener('resize', syncIsPc)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncIsPc)
})
</script>

<style scoped>
.hi {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: var(--sg-bg);
  color: var(--sg-text);
}

.hi-pc-top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 22px;
  background: #14110f;
  color: #fff;
}

.hi-pc-logo {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 1px;
  background: var(--sg-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hi-pc-back {
  padding: 6px 14px;
  border-radius: var(--sg-radius-full);
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.hi-pc-back:hover {
  background: rgba(255, 255, 255, 0.24);
}

.hi-pc-space {
  flex: 1;
}

.hi-pc-clear {
  padding: 6px 14px;
  border-radius: var(--sg-radius-full);
  background: rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
}

.hi-pc-clear:hover {
  background: rgba(255, 90, 90, 0.28);
  color: #fff;
}

.hi-top {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  padding-top: calc(12px + env(safe-area-inset-top));
  border-bottom: 1px solid var(--sg-line);
  background: var(--sg-bg);
}

.hi-back {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 13px;
  color: var(--sg-text);
  font-weight: 600;
  flex-shrink: 0;
}

.hi-title {
  flex: 1;
  margin: 0;
  font-size: 16.5px;
  font-weight: 800;
  text-align: center;
}

.hi-top-space {
  width: 34px;
  flex-shrink: 0;
}

.hi-clear {
  font-size: 13px;
  color: var(--sg-primary-deep);
  flex-shrink: 0;
  padding: 4px 2px;
}

.hi-body {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 40px;
}

.hi-state {
  text-align: center;
  color: var(--sg-text-3);
  font-size: 13px;
  padding: 56px 24px;
}

.hi-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 62px 32px;
}

.hi-empty-ico {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 66px;
  height: 66px;
  border-radius: 50%;
  background: var(--sg-primary-soft);
  color: var(--sg-primary);
  margin-bottom: 14px;
}

.hi-empty-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.hi-empty-desc {
  margin: 6px 0 18px;
  font-size: 12.5px;
  color: var(--sg-text-2);
  line-height: 1.6;
  max-width: 260px;
}

.hi-empty-btn {
  height: 36px;
  padding: 0 22px;
  border-radius: 999px;
  background: var(--sg-primary);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(232, 75, 75, 0.28);
}

.hi-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
  padding: 4px 3px 0;
}

.hi.is-pc .hi-grid {
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  padding: 16px 22px 0;
  max-width: 1180px;
  margin: 0 auto;
}

.hi-cell {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: 6px;
  overflow: hidden;
  background: var(--sg-bg-deep);
  cursor: pointer;
}

.hi.is-pc .hi-cell {
  border-radius: var(--sg-radius);
}

.hi-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.hi-play {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  color: #fff;
  filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.5));
}

.hi-likes {
  position: absolute;
  left: 6px;
  bottom: 6px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #fff;
  font-size: 11px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.55);
}

.hi-likes svg {
  color: #ff5c5c;
}

.hi-author {
  position: absolute;
  right: 6px;
  bottom: 6px;
  max-width: 66%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.55);
}

.hi-more {
  text-align: center;
  color: var(--sg-text-3);
  font-size: 12px;
  padding: 16px 0 6px;
}
</style>
