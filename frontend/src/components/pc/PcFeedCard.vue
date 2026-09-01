<template>
  <article class="pc-slide">
    <div class="media" @click="onMediaClick">
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
        <img
          v-if="!imgFailed && cover"
          class="image"
          :src="cover"
          :alt="post.title || '作品'"
          :loading="active ? 'eager' : 'lazy'"
          @error="imgFailed = true"
        />
        <div v-else class="image image-fallback">
          <span class="fallback-mark">拾</span>
          <span class="fallback-text">图片暂时无法加载</span>
        </div>
      </template>

      <button
        v-if="post.type === 'VIDEO' && !videoFailed"
        class="fullscreen-btn"
        title="全屏播放"
        @click.stop="openFullscreen"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z" /></svg>
      </button>
      <span v-if="post.type === 'IMAGE' && postImages.length > 1" class="multi-badge">1/{{ postImages.length }}</span>
      <div v-if="post.type === 'IMAGE' && !imgFailed && cover" class="zoom-hint">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="currentColor"><path d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z" /></svg>
        查看大图
      </div>
      <div v-if="post.type === 'VIDEO' && !videoFailed && !playing" class="play-mask">
        <span class="play-icon">▶</span>
      </div>
    </div>

    <!-- 左下：作者与文案 -->
    <div class="meta">
      <div class="meta-author">
        <img class="avatar" :src="author.avatarUrl || fallbackAvatar" alt="avatar" />
        <span class="nickname">{{ author.nickname || '拾光用户' }}</span>
        <button v-if="!author.following" class="follow-btn" @click.stop="$emit('follow')">关注</button>
      </div>
      <p class="title">{{ post.title || '分享美好瞬间' }}</p>
      <p v-if="post.description" class="desc">{{ post.description }}</p>
      <span class="tag">{{ post.type === 'VIDEO' ? '短视频' : '图文' }} · {{ formatDate(post.createdAt) }}</span>
    </div>

    <!-- 右侧互动栏 -->
    <div class="rail">
      <div class="rail-avatar-wrap">
        <img class="rail-avatar" :src="author.avatarUrl || fallbackAvatar" alt="avatar" />
        <span v-if="!author.following" class="rail-follow" title="关注" @click="$emit('follow')">+</span>
      </div>
      <button class="rail-btn" :class="{ liked: post.liked }" @click="$emit('like')">
        <svg viewBox="0 0 24 24" width="34" height="34" :fill="post.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" /></svg>
        <span>{{ formatCount(post.likeCount) }}</span>
      </button>
      <button class="rail-btn" @click="$emit('comment')">
        <svg viewBox="0 0 24 24" width="32" height="32" fill="currentColor"><path d="M12 3C6.48 3 2 6.8 2 11.5c0 2.3 1.1 4.4 2.9 5.9-.1 1.5-.7 3.1-1.9 4.3 1.9-.2 3.6-1 5-2.1.9.2 1.9.4 2.9.4 5.52 0 10-3.8 10-8.5S17.52 3 12 3z" /></svg>
        <span>{{ formatCount(post.commentCount) }}</span>
      </button>
      <button class="rail-btn" @click="$emit('share')">
        <svg viewBox="0 0 24 24" width="32" height="32" fill="currentColor"><path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z" /></svg>
        <span>分享</span>
      </button>
    </div>

    <Teleport to="body">
      <div v-if="lightboxOpen" class="sg-lightbox" @click.self="closeLightbox">
        <div class="lb-stage" @click="closeLightbox" :style="{ transform: 'translateX(-' + lightboxIndex * 100 + '%)' }">
          <img v-for="(img, i) in postImages" :key="i" class="sg-lightbox-img" :src="img" :alt="post.title || '作品'" />
        </div>
        <button v-if="postImages.length > 1 && lightboxIndex > 0" class="lb-arrow lb-prev" aria-label="上一张" @click.stop="lbPrev">‹</button>
        <button v-if="postImages.length > 1 && lightboxIndex < postImages.length - 1" class="lb-arrow lb-next" aria-label="下一张" @click.stop="lbNext">›</button>
        <div v-if="postImages.length > 1" class="lb-count">{{ lightboxIndex + 1 }}/{{ postImages.length }}</div>
        <button class="sg-lightbox-close" aria-label="关闭" @click="closeLightbox">×</button>
      </div>
    </Teleport>
  </article>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  post: { type: Object, required: true },
  active: { type: Boolean, default: false }
})

const emit = defineEmits(['like', 'comment', 'share', 'follow'])

const videoEl = ref(null)
const playing = ref(false)
const imgFailed = ref(false)
const videoFailed = ref(false)
const lightboxOpen = ref(false)
const fullscreenActive = ref(false)
let escHandler = null

const author = computed(() => props.post.author || {})
const cover = computed(() => props.post.coverUrl || (props.post.images && props.post.images[0]) || '')
const postImages = computed(() => {
  if (props.post.images && props.post.images.length) return props.post.images
  return props.post.coverUrl ? [props.post.coverUrl] : []
})
const lightboxIndex = ref(0)

function lbPrev() {
  if (lightboxIndex.value > 0) lightboxIndex.value -= 1
}

function lbNext() {
  if (lightboxIndex.value < postImages.value.length - 1) lightboxIndex.value += 1
}

const fallbackAvatar = computed(() => {
  const name = author.value.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((author.value.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
})

function syncPlayback() {
  const v = videoEl.value
  if (!v) return
  if (props.active) {
    v.play().catch(() => {})
    playing.value = true
  } else {
    v.pause()
    playing.value = false
  }
}

watch(() => props.active, syncPlayback)

watch(videoEl, (v) => {
  if (!v) return
  syncPlayback()
  v.addEventListener('fullscreenchange', () => {
    fullscreenActive.value = document.fullscreenElement === v
  })
})

function togglePlay() {
  if (!props.active) return
  const v = videoEl.value
  if (!v) return
  if (v.paused) {
    v.play().catch(() => {})
  } else {
    v.pause()
  }
  playing.value = !v.paused
}

function onMediaClick() {
  if (props.post.type === 'VIDEO') {
    togglePlay()
  } else {
    openLightbox()
  }
}

function openLightbox() {
  lightboxOpen.value = true
  lightboxIndex.value = 0
  document.body.style.overflow = 'hidden'
  escHandler = (e) => {
    if (e.key === 'Escape') closeLightbox()
    else if (e.key === 'ArrowLeft') lbPrev()
    else if (e.key === 'ArrowRight') lbNext()
  }
  document.addEventListener('keydown', escHandler)
}

function closeLightbox() {
  lightboxOpen.value = false
  document.body.style.overflow = ''
  if (escHandler) {
    document.removeEventListener('keydown', escHandler)
    escHandler = null
  }
}

onBeforeUnmount(() => {
  const v = videoEl.value
  if (v) {
    v.pause()
  }
  closeLightbox()
})

async function openFullscreen() {
  const v = videoEl.value
  if (!v) return
  try {
    await v.requestFullscreen()
  } catch (e) {
    // noop: fullscreen may be unsupported
  }
}

function formatCount(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

function formatDate(s) {
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
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  return `${y}-${mo}-${da}`
}

onBeforeUnmount(() => {
  if (escHandler) {
    document.removeEventListener('keydown', escHandler)
    escHandler = null
  }
  document.body.style.overflow = ''
})
</script>

<style scoped>
.pc-slide {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #0b0b0e;
}

.media {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0b0b0e;
  cursor: pointer;
}

.video,
.image {
  width: 100%;
  height: 100%;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
  background: #0b0b0e;
}

.image-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 14px;
  color: rgba(255, 255, 255, 0.7);
}

.fallback-mark {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: var(--sg-gradient-deep);
  color: #fff;
  font-size: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.fallback-text {
  font-size: 14px;
}

.fullscreen-btn {
  position: absolute;
  right: 20px;
  top: 20px;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s, background 0.2s;
  z-index: 5;
}

.fullscreen-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.media:hover .fullscreen-btn,
.media:hover .zoom-hint {
  opacity: 1;
}

.zoom-hint {
  position: absolute;
  right: 20px;
  bottom: 20px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: var(--sg-radius-full);
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 5;
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
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  color: #26221f;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 5px;
}

.meta {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 3;
  padding: 90px 44px 34px 36px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, 0.55) 100%);
  pointer-events: none;
}

.meta-author {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  pointer-events: auto;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.85);
  background: var(--sg-bg-warm);
}

.nickname {
  font-weight: 600;
  font-size: 16px;
  color: #fff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
}

.follow-btn {
  padding: 5px 16px;
  border-radius: var(--sg-radius-full);
  background: var(--sg-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}

.title {
  font-size: 17px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 6px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
}

.desc {
  font-size: 14px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.88);
  max-width: 620px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
}

.tag {
  display: inline-block;
  margin-top: 10px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.6);
}

.rail {
  position: absolute;
  right: 26px;
  bottom: 150px;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.rail-avatar-wrap {
  position: relative;
  margin-bottom: 2px;
}

.rail-avatar {
  width: 54px;
  height: 54px;
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
  border: 2px solid #0b0b0e;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
  cursor: pointer;
  transition: transform 0.15s;
}

.rail-follow:hover {
  transform: translateX(-50%) scale(1.1);
}

.rail-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
  transition: transform 0.15s;
}

.rail-btn:hover {
  transform: scale(1.08);
}

.rail-btn.liked {
  color: #ff4757;
}

.multi-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
  z-index: 5;
  pointer-events: none;
}

.lb-stage {
  display: flex;
  width: 100%;
  height: 100%;
  transition: transform 0.3s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.lb-stage .sg-lightbox-img {
  flex: 0 0 100%;
  max-width: 100vw;
  max-height: 100vh;
  object-fit: contain;
  border-radius: 0;
  box-shadow: none;
}

.lb-arrow {
  position: fixed;
  top: 50%;
  transform: translateY(-50%);
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 30px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  z-index: 3001;
}

.lb-arrow:hover {
  background: rgba(255, 255, 255, 0.24);
}

.lb-prev {
  left: 28px;
}

.lb-next {
  right: 28px;
}

.lb-count {
  position: fixed;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  padding: 4px 14px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 13px;
  z-index: 3001;
}

.sg-lightbox {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(10, 9, 8, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: zoom-out;
}

.sg-lightbox-img {
  max-width: 92vw;
  max-height: 92vh;
  object-fit: contain;
  border-radius: var(--sg-radius);
  box-shadow: var(--sg-shadow-lg);
}

.sg-lightbox-close {
  position: fixed;
  right: 24px;
  top: 24px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 20px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.sg-lightbox-close:hover {
  background: rgba(255, 255, 255, 0.24);
}
</style>