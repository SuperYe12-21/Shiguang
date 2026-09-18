<template>
  <span v-if="text" class="sg-badge" :class="`sg-badge-${variant}`">{{ text }}</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  count: { type: Number, default: 0 },
  // pill: 跟在文字右侧的小胶囊；float: 浮在图标右上角
  variant: { type: String, default: 'pill' }
})

const text = computed(() => {
  const n = props.count || 0
  if (n <= 0) return ''
  return n > 99 ? '99+' : String(n)
})
</script>

<style scoped>
.sg-badge {
  background: #ff5c5c;
  color: #fff;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.sg-badge-pill {
  padding: 3px 7px;
  border-radius: 999px;
  font-size: 11px;
}

.sg-badge-float {
  position: absolute;
  top: -5px;
  right: -9px;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(255, 255, 255, 0.92);
  box-sizing: content-box;
}
</style>
