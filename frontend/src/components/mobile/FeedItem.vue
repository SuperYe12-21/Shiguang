<template>
  <section class="feed-item">
    <video
      v-if="post.type === 'VIDEO'"
      ref="videoEl"
      class="feed-video"
      :src="post.videoUrl"
      :poster="post.coverUrl || undefined"
      loop
      muted
      playsinline
      preload="metadata"
      @click="toggleMute"
    />
    <div v-else class="feed-image">
      <img :src="cover" :alt="post.title || '作品'" loading="lazy" />
    </div>

    <!-- 左下：作者与文案 -->
    <div class="feed-meta">
      <div class="meta-author">
        <img class="avatar" :src="author.avatarUrl || fallbackAvatar" alt="avatar" />
        <span class="nickname">{{ author.nickname || '拾光用户' }}</span>
        <button v-if="!author.following" class="follow-btn" @click="$emit('follow')">关注</button>
      </div>
      <p class="title">{{ post.title || '分享美好瞬间' }}</p>
      <p v-if="post.description" class="desc">{{ post.description }}</p>
    </div>

    <!-- 右侧互动栏 -->
    <div class="action-rail">
      <button class="rail-btn" @click="$emit('like')">
        <span class="rail-icon" :class="{ liked: post.liked }">
          <svg v-if="post.liked" viewBox="0 0 24 24" width="34" height="34" fill="currentColor">
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
          </svg>
          <svg v-else viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
          </svg>
        </span>
        <span class="rail-count">{{ formatCount(post.likeCount) }}</span>
      </button>

      <button class="rail-btn" @click="$emit('comment')">
        <span class="rail-icon">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="currentColor">
            <path d="M12 3C6.48 3 2 6.8 2 11.5c0 2.3 1.1 4.4 2.9 5.9-.1 1.5-.7 3.1-1.9 4.3 1.9-.2 3.6-1 5-2.1.9.2 1.9.4 2.9.4 5.52 0 10-3.8 10-8.5S17.52 3 12 3z" />
          </svg>
        </span>
        <span class="rail-count">{{ formatCount(post.commentCount) }}</span>
      </button>

      <button class="rail-btn" @click="$emit('share')">
        <span class="rail-icon">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="currentColor">
            <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z" />
          </svg>
        </span>
        <span class="rail-count">分享</span>
      </button>
    </div>

    <div v-if="post.type === 'VIDEO' && muted" class="mute-badge">
      <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
        <path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z" />
      </svg>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  post: { type: Object, required: true },
  active: { type: Boolean, default: false }
})

const emit = defineEmits(['like', 'comment', 'share', 'follow'])

const videoEl = ref(null)
const muted = ref(true)

const author = computed(() => props.post.author || {})
const cover = computed(() => props.post.coverUrl || (props.post.images && props.post.images[0]) || '')

const fallbackAvatar = computed(() => {
  const name = author.value.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((author.value.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
})

watch(
  () => props.active,
  (active) => {
    const v = videoEl.value
    if (!v) return
    if (active) {
      v.play().catch(() => {})
    } else {
      v.pause()
    }
  },
  { immediate: true }
)

function toggleMute() {
  const v = videoEl.value
  if (!v) return
  muted.value = !muted.value
  v.muted = muted.value
  if (!v.muted && v.paused) {
    v.play().catch(() => {})
  }
}

function formatCount(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}
</script>

<style scoped>
.feed-item {
  position: relative;
  width: 100%;
  height: 100%;
  scroll-snap-align: start;
  overflow: hidden;
  background: #141210;
}

.feed-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.feed-image {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #141210;
}

.feed-image img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

/* 底部文案渐隐区 */
.feed-meta {
  position: absolute;
  left: 0;
  right: 88px;
  bottom: 0;
  padding: 64px 16px 18px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.62));
  color: #fff;
}

.meta-author {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.85);
  object-fit: cover;
  background: rgba(255, 255, 255, 0.2);
}

.nickname {
  font-size: 15px;
  font-weight: 600;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.follow-btn {
  margin-left: 2px;
  padding: 3px 12px;
  border-radius: var(--sg-radius-full);
  background: var(--sg-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.follow-btn:active {
  background: var(--sg-primary-deep);
}

.title {
  font-size: 15px;
  font-weight: 500;
  margin-bottom: 4px;
}

.desc {
  font-size: 13px;
  opacity: 0.85;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 右侧互动栏 */
.action-rail {
  position: absolute;
  right: 10px;
  bottom: 92px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  z-index: 2;
}

.rail-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  color: #fff;
}

.rail-icon {
  display: inline-flex;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.35));
}

.rail-icon.liked {
  color: var(--sg-primary);
  animation: sg-pop 0.3s ease;
}

.rail-count {
  font-size: 12px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}

.mute-badge {
  position: absolute;
  right: 16px;
  top: 16px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>