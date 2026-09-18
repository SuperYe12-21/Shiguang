<template>
  <router-view :key="route.path" />
</template>

<script setup>
import { onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useNotificationStore } from './stores/notification'

// 按 path 作为 router-view 的 key：/feed 与 /post/:id、/me 与 /user/:id 共用同一个组件，
// 不加 key 时 vue-router 会复用实例，页面里的临时状态（评论面板、当前作品下标）会残留到下一个页面
const route = useRoute()
const auth = useAuthStore()
const notification = useNotificationStore()

// 登录后开始轮询未读数，登出立刻停掉并清空红点
watch(
  () => auth.token,
  (token) => {
    if (token) {
      notification.start()
    } else {
      notification.stop()
    }
  },
  { immediate: true }
)

onMounted(() => {
  auth.ensureUser()
})

onBeforeUnmount(() => {
  notification.stop()
})
</script>
