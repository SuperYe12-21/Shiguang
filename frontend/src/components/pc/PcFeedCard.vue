<template>
  <article class="pc-card sg-card">
    <div class="media" @click="togglePlay">
      <video
        v-if="post.type === 'VIDEO' && !videoFailed"
        ref="videoEl"
        class="video"
        :src="post.videoUrl"
        :poster="post.coverUrl || undefined"
        loop
        muted
        playsinline
        preload="metadata"
        @error="videoFailed = true"
      />
      <div v-else-if="post.type === 'VIDEO'" class="image image-fallback">
        <span class="fallback-mark">拾</span>
        <span class="fallback-text">视频暂时无法加载</span>
      </div>
      <template v-else>
        <img v-if="!imgFailed && cover" class="image" :src="cover" :alt="post.title || '作品'" loading="lazy" @error="imgFailed = true" />
        <div v-else class="image image-fallback">
          <span class="fallback-mark">拾</span>
          <span class="fallback-text">图片暂时无法加载</span>
        </div>
      </template>

      <div v-if="post.type === 'VIDEO' && !videoFailed && !playing" class="play-mask">
        <span class="play-icon">▶</span>
      </div>
    </div>

    <div class="body">
      <div class="author-row">
        <img class="avatar" :src="author.avatarUrl || fallbackAvatar" alt="avatar" />
        <div class="author-info">
          <span class="nickname">{{ author.nickname || '拾光用户' }}</span>
          <button v-if="!author.following" class="follow-btn" @click="$emit('follow')">关注</button>
        </div>
      </div>

      <p class="title">{{ post.title || '分享美好瞬间' }}</p>
      <p v-if="post.description" class="desc">{{ post.description }}</p>

      <div class="actions">
        <button class="action-btn" :class="{ liked: post.liked }" @click="$emit('like')">
          <svg viewBox="0 0 24 24" width="20" height="20" :fill="post.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
          </svg>
          <span>{{ formatCount(post.likeCount) }} 赞</span>
        </button>

        <button class="action-btn" @click="$emit('comment')">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
            <path d="M12 3C6.48 3 2 6.8 2 11.5c0 2.3 1.1 4.4 2.9 5.9-.1 1.5-.7 3.1-1.9 4.3 1.9-.2 3.6-1 5-2.1.9.2 1.9.4 2.9.4 5.52 0 10-3.8 10-8.5S17.52 3 12 3z" />
          </svg>
          <span>{{ formatCount(post.commentCount) }} 评论</span>
        </button>

        <button class="action-btn" @click="$emit('share')">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
            <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z" />
          </svg>
          <span>分享</span>
        </button>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  post: { type: Object, required: true }
})

const emit = defineEmits(['like', 'comment', 'share', 'follow'])

const videoEl = ref(null)
const playing = ref(false)
const imgFailed = ref(false)
const videoFailed = ref(false)
let observer = null

const author = computed(() => props.post.author || {})
const cover = computed(() => props.post.coverUrl || (props.post.images && props.post.images[0]) || '')

const fallbackAvatar = computed(() => {
  const name = author.value.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((author.value.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
})

onMounted(() => {
  const v = videoEl.value
  if (!v) return
  observer = new IntersectionObserver(
    (entries) => {
      const entry = entries[0]
      if (entry.isIntersecting) {
        v.play().catch(() => {})
        playing.value = true
      } else {
        v.pause()
        playing.value = false
      }
    },
    { threshold: 0.55 }
  )
  observer.observe(v)
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})

function togglePlay() {
  const v = videoEl.value
  if (!v) return
  if (v.paused) {
    v.play().catch(() => {})
  } else {
    v.pause()
  }
  playing.value = !v.paused
}

function formatCount(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}
</script>

<style scoped>
.pc-card {
  overflow: hidden;
  border-radius: var(--sg-radius-lg);
}

.media {
  position: relative;
  background: #141210;
  cursor: pointer;
}

.video {
  width: 100%;
  min-height: 42vh;
  max-height: 62vh;
  object-fit: contain;
  display: block;
  background: #141210;
}

.image {
  width: 100%;
  min-height: 240px;
  max-height: 62vh;
  object-fit: contain;
  display: block;
  background: #141210;
}

.image-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  color: rgba(255, 255, 255, 0.75);
}

.fallback-mark {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: var(--sg-gradient-deep);
  color: #fff;
  font-size: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fallback-text {
  font-size: 13px;
}

.play-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.18);
  pointer-events: none;
}

.play-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: #26221f;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 4px;
}

.body {
  padding: 16px 18px 14px;
}

.author-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--sg-bg-warm);
}

.author-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nickname {
  font-weight: 600;
  font-size: 15px;
}

.follow-btn {
  padding: 4px 14px;
  border-radius: var(--sg-radius-full);
  background: var(--sg-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
}

.desc {
  font-size: 13px;
  color: var(--sg-text-2);
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: var(--sg-radius-full);
  background: var(--sg-bg);
  color: var(--sg-text-2);
  font-size: 13px;
  transition: background 0.2s, color 0.2s;
}

.action-btn:hover {
  background: var(--sg-primary-soft);
  color: var(--sg-primary-deep);
}

.action-btn.liked {
  color: var(--sg-primary-deep);
  background: var(--sg-primary-soft);
}
</style>
