<template>
  <aside class="pc-panel">
    <div class="panel-inner sg-glass">
      <template v-if="post">
        <div class="panel-author">
          <img class="avatar" :src="author.avatarUrl || fallbackAvatar" alt="avatar" />
          <div class="panel-author-info">
            <span class="nickname">{{ author.nickname || '拾光用户' }}</span>
            <span class="sub">{{ post.type === 'VIDEO' ? '短视频' : '图文' }} · {{ formatDate(post.createdAt) }}</span>
          </div>
        </div>

        <p class="panel-title">{{ post.title || '分享美好瞬间' }}</p>
        <p v-if="post.description" class="panel-desc">{{ post.description }}</p>

        <div class="panel-actions">
          <button class="panel-btn" :class="{ liked: post.liked }" @click="$emit('like')">
            <svg viewBox="0 0 24 24" width="22" height="22" :fill="post.liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
              <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
            </svg>
            <span>{{ formatCount(post.likeCount) }}</span>
          </button>
          <button class="panel-btn" @click="$emit('comment')">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
              <path d="M12 3C6.48 3 2 6.8 2 11.5c0 2.3 1.1 4.4 2.9 5.9-.1 1.5-.7 3.1-1.9 4.3 1.9-.2 3.6-1 5-2.1.9.2 1.9.4 2.9.4 5.52 0 10-3.8 10-8.5S17.52 3 12 3z" />
            </svg>
            <span>{{ formatCount(post.commentCount) }}</span>
          </button>
          <button class="panel-btn" @click="$emit('share')">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
              <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z" />
            </svg>
            <span>分享</span>
          </button>
        </div>

        <div class="panel-divider"></div>
        <p class="panel-section-title">更多作品</p>
        <div v-if="morePosts.length" class="more-list">
          <button v-for="p in morePosts" :key="p.id" class="more-item" @click="$emit('select', p)">
            <img class="more-thumb" :src="thumbOf(p)" alt="" loading="lazy" @error="onThumbError(p, $event)" />
            <div class="more-info">
              <span class="more-title">{{ p.title || '分享美好瞬间' }}</span>
              <span class="more-sub">{{ formatCount(p.likeCount) }} 赞 · {{ formatCount(p.commentCount) }} 评论</span>
            </div>
          </button>
        </div>
        <p v-else class="more-empty">暂时没有更多作品</p>
      </template>

      <div v-else class="panel-empty">
        <span class="empty-mark">拾</span>
        <p>滑动选择作品</p>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  post: { type: Object, default: null },
  posts: { type: Array, default: () => [] }
})

const emit = defineEmits(['like', 'comment', 'share', 'select'])

const author = computed(() => props.post.author || {})

const morePosts = computed(() => props.posts.filter((p) => p.id !== props.post.id).slice(0, 5))

const fallbackAvatar = computed(() => {
  const name = author.value.nickname || '拾'
  const ch = name.charAt(0)
  const hue = ((author.value.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='96' height='96'><rect width='96' height='96' rx='48' fill='hsl(${hue},60%,86%)'/><text x='48' y='64' font-size='42' text-anchor='middle' fill='hsl(${hue},45%,42%)' font-family='sans-serif'>${ch}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
})

function thumbOf(p) {
  return (p.coverUrl || (p.images && p.images[0])) || fallbackThumb(p)
}

function onThumbError(p, event) {
  event.target.src = fallbackThumb(p)
}

function fallbackThumb(p) {
  const hue = ((p.id || 0) * 47) % 360
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='120' height='160'><rect width='120' height='160' fill='hsl(${hue},50%,88%)'/><text x='60' y='86' font-size='30' text-anchor='middle' fill='hsl(${hue},45%,45%)' font-family='sans-serif'>拾</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

function formatCount(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

function formatDate(t) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.pc-panel {
  width: 300px;
  flex-shrink: 0;
  padding: 18px 8px 18px 0;
}

.panel-inner {
  position: sticky;
  top: 18px;
  padding: 22px 20px;
  border-radius: var(--sg-radius-lg);
  box-shadow: var(--sg-shadow);
}

.panel-author {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--sg-bg-warm);
}

.panel-author-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nickname {
  font-weight: 600;
  font-size: 15px;
}

.sub {
  font-size: 12px;
  color: var(--sg-text-3);
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.panel-desc {
  font-size: 13px;
  color: var(--sg-text-2);
  margin-bottom: 16px;
}

.panel-actions {
  display: flex;
  gap: 10px;
}

.panel-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--sg-radius-full);
  background: #fff;
  color: var(--sg-text-2);
  font-size: 14px;
  box-shadow: 0 2px 10px rgba(38, 34, 31, 0.06);
  transition: color 0.2s, background 0.2s;
}

.panel-btn:hover {
  color: var(--sg-primary-deep);
}

.panel-btn.liked {
  color: var(--sg-primary-deep);
  background: var(--sg-primary-soft);
}

.panel-divider {
  height: 1px;
  background: var(--sg-line);
  margin: 18px 0 14px;
}

.panel-section-title {
  font-size: 13px;
  color: var(--sg-text-3);
  margin-bottom: 10px;
}

.more-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.more-item {
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
}

.more-thumb {
  width: 46px;
  height: 58px;
  border-radius: var(--sg-radius-sm);
  object-fit: cover;
  background: var(--sg-bg-warm);
  flex-shrink: 0;
}

.more-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.more-title {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.more-sub {
  font-size: 12px;
  color: var(--sg-text-3);
}

.more-empty {
  font-size: 13px;
  color: var(--sg-text-3);
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 0;
  color: var(--sg-text-3);
}

.empty-mark {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: var(--sg-gradient-deep);
  color: #fff;
  font-size: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>