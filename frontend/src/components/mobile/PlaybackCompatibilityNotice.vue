<template>
  <div v-if="visible" class="playback-notice" role="status">
    <p>当前浏览器可能遮挡点赞、评论。建议通过微信打开，体验完整功能。</p>
    <button type="button" aria-label="关闭视频兼容提示" @click="dismiss">知道了</button>
  </div>
  <slot v-else />
</template>

<script setup>
import { ref } from 'vue'

const dismissalKey = 'sg_playback_compat_dismissed'
const userAgent = typeof navigator === 'undefined' ? '' : navigator.userAgent
const affectedBrowser = /Android/i.test(userAgent)
  && /HeyTapBrowser|OppoBrowser|MQQBrowser|QQBrowser/i.test(userAgent)
  && !/MicroMessenger/i.test(userAgent)

function wasDismissed() {
  try {
    return sessionStorage.getItem(dismissalKey) === '1'
  } catch {
    return false
  }
}

const visible = ref(affectedBrowser && !wasDismissed())

function dismiss() {
  visible.value = false
  try {
    sessionStorage.setItem(dismissalKey, '1')
  } catch {}
}
</script>

<style scoped>
.playback-notice {
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}
.playback-notice p {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: var(--sg-text-2);
  font-size: 12px;
  line-height: 1.45;
}
.playback-notice button {
  flex: none;
  min-height: 40px;
  padding: 0 4px;
  color: var(--sg-primary);
  font-size: 12px;
  font-weight: 600;
}
</style>
