<template>
  <section class="feed-item">
    <video
      v-if="post.type === 'VIDEO'"
      ref="videoEl"
      class="feed-video"
      :src="post.videoUrl"
      :poster="post.coverUrl || undefined"
      loop
      :muted="muted"
      playsinline
      preload="metadata"
      @click="togglePlay"
      @play="playing = true"
      @pause="playing = false"
      @error="videoFailed = true"
    />
    <div v-else class="feed-image">
      <div v-if="!imgFailed" class="img-swiper" @touchstart.passive="onTouchStart" @touchmove="onTouchMove" @touchend="onTouchEnd">
        <div class="img-track" :style="{ transform: 'translateX(-' + imgIndex * 100 + '%)' }">
          <img v-for="(img, i) in postImages" :key="i" :src="img" :alt="post.title || '作品'" draggable="false" @error="imgFailed = true" />
        </div>
        <span v-if="postImages.length > 1" class="img-count">{{ imgIndex + 1 }}/{{ postImages.length }}</span>
        <div v-if="postImages.length > 1" class="img-dots">
          <span v-for="(img, i) in postImages" :key="i" class="dot" :class="{ on: i === imgIndex }" />
        </div>
      </div>
      <div v-else class="img-fallback">
        <span class="img-fallback-mark">拾</span>
        <span class="img-fallback-text">图片暂时无法加载</span>
      </div>
    </div>

    <!-- 左下：作者与文案 -->
    <div class="feed-meta">
      <div class="meta-author">
        <img class="avatar" :src="author.avatarUrl || fallbackAvatar" alt="avatar" />
        <span class="nickname">{{ author.nickname || '拾光用户' }}</span>
      </div>
      <p class="title">{{ post.title || '分享美好瞬间' }}</p>
      <p v-if="post.description" class="desc">{{ post.description }}</p>
    </div>

    <!-- 右侧互动栏 -->
    <div class="action-rail">
      <div class="rail-avatar-wrap">
        <img class="rail-avatar" :src="author.avatarUrl || fallbackAvatar" alt="avatar" />
        <span v-if="!author.following" class="rail-follow" title="关注" @click="$emit('follow')">+</span>
      </div>
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

    <div v-if="post.type === 'VIDEO' && !videoFailed && !playing" class="play-mask-m" @click="togglePlay">
      <span class="play-icon-m">▶</span>
    </div>

    <div v-if="post.type === 'VIDEO' && showSoundTip" class="sound-tip" @click.stop="togglePlay">点按视频开启声音</div>

    <div v-if="post.type === 'VIDEO' && videoFailed" class="video-fail">
      <span>视频加载失败</span>
    </div>

    
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  post: { type: Object, required: true },
  active: { type: Boolean, default: false }
})

const emit = defineEmits(['like', 'comment', 'share', 'follow'])

const videoEl = ref(null)
const muted = ref(false)
const playing = ref(false)
const soundBlocked = ref(false)
const showSoundTip = ref(false)
let soundTipTimer = null
const imgFailed = ref(false)
const videoFailed = ref(false)

const author = computed(() => props.post.author || {})
const cover = computed(() => props.post.coverUrl || (props.post.images && props.post.images[0]) || '')

const postImages = computed(() => {
  if (props.post.images && props.post.images.length) return props.post.images
  return props.post.coverUrl ? [props.post.coverUrl] : []
})
const imgIndex = ref(0)
let touchStartX = null
let touchStartY = null
let touchMoved = false

watch(postImages, () => {
  if (imgIndex.value >= postImages.value.length) imgIndex.value = 0
})

function onTouchStart(e) {
  if (postImages.value.length < 2) return
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  touchMoved = false
}

function onTouchMove(e) {
  if (touchStartX == null) return
  const dx = e.touches[0].clientX - touchStartX
  const dy = e.touches[0].clientY - touchStartY
  if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 8) {
    e.preventDefault()
    touchMoved = true
  }
}

function onTouchEnd(e) {
  if (!touchMoved || touchStartX == null) return
  const dx = e.changedTouches[0].clientX - touchStartX
  const total = postImages.value.length
  if (dx < -40 && imgIndex.value < total - 1) imgIndex.value += 1
  else if (dx > 40 && imgIndex.value > 0) imgIndex.value -= 1
  touchStartX = null
  touchStartY = null
  touchMoved = false
}

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
      tryPlay(v)
    } else {
      v.pause()
    }
  },
  { immediate: true }
)

watch(videoEl, (v) => {
  if (!v) return
  if (props.active) {
    tryPlay(v)
  } else {
    v.pause()
  }
})

onBeforeUnmount(() => {
  const v = videoEl.value
  if (v) {
    v.pause()
    v.removeAttribute('src')
    v.load()
  }
})

function tryPlay(v) {
  v.muted = muted.value
  const p = v.play()
  if (p && p.catch) {
    p.catch(() => {
      if (!v.muted) {
        soundBlocked.value = true
        muted.value = true
        v.muted = true
        showSoundTip.value = true
        if (soundTipTimer) clearTimeout(soundTipTimer)
        soundTipTimer = setTimeout(() => {
          showSoundTip.value = false
        }, 4000)
        v.play().catch(() => {})
      }
    })
  }
}

function togglePlay() {
  const v = videoEl.value
  if (!v) return
  if (v.paused && soundBlocked.value) {
    soundBlocked.value = false
    showSoundTip.value = false
    muted.value = false
    v.muted = false
    const p = v.play()
    if (p && p.catch) {
      p.catch(() => {
        soundBlocked.value = true
        muted.value = true
        v.muted = true
        v.play().catch(() => {})
      })
    }
    return
  }
  if (v.paused) {
    tryPlay(v)
  } else {
    v.pause()
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
  transition: height 0.3s cubic-bezier(0.22, 0.61, 0.36, 1);
}

/* 评论打开时：当前作品压缩到屏幕上方，只保留媒体主体与评论区（抖音式） */
section.feed-item.item-compact {
  height: 38vh;
  height: 38dvh;
}

section.feed-item.item-compact .feed-meta,
section.feed-item.item-compact .action-rail {
  display: none;
}

.feed-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.feed-image {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #141210;
  overflow: hidden;
}

.img-swiper {
  width: 100%;
  height: 100%;
  touch-action: pan-y;
}

.img-track {
  display: flex;
  height: 100%;
  transition: transform 0.28s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.img-track img {
  flex: 0 0 100%;
  width: 100%;
  height: 100%;
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
}

.img-count {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  z-index: 3;
}

.img-dots {
  position: absolute;
  top: 52px;
  right: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  z-index: 3;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  transition: all 0.2s;
}

.dot.on {
  height: 16px;
  border-radius: 3px;
  background: #fff;
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
  bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom) + 4px);
  padding: 18px 16px 8px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.5));
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

.title {
  font-size: 15px;
  font-weight: 500;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  bottom: calc(var(--sg-nav-h) + env(safe-area-inset-bottom) + 96px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  z-index: 2;
}

.rail-avatar-wrap {
  position: relative;
  margin-bottom: 2px;
}

.rail-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #fff;
  background: var(--sg-bg-warm);
}

.rail-follow {
  position: absolute;
  left: 50%;
  bottom: -10px;
  transform: translateX(-50%);
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--sg-primary);
  border: 2px solid #141210;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
  cursor: pointer;
  transition: transform 0.15s;
}

.rail-follow:active {
  transform: translateX(-50%) scale(1.1);
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

.img-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.75);
}

.img-fallback-mark {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: var(--sg-gradient-deep);
  color: #fff;
  font-size: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.img-fallback-text {
  font-size: 13px;
}

.play-mask-m {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.22);
  z-index: 4;
}

.play-icon-m {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 5px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.sound-tip {
  position: absolute;
  top: 16px;
  right: 16px;
  padding: 8px 14px;
  border-radius: var(--sg-radius-full);
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 13px;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  animation: sg-fade-in 0.25s ease;
}

@keyframes sg-fade-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.video-fail {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  z-index: 3;
}


</style>