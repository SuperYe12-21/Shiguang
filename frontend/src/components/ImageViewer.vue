<template>
  <Teleport to="body">
    <div class="sg-iv" @click.self="$emit('close')">
      <button class="sg-iv-close" aria-label="关闭" @click="$emit('close')">×</button>
      <img class="sg-iv-img" :src="images[current]" alt="图片" @click.stop />
      <div v-if="images.length > 1" class="sg-iv-nav">
        <button class="sg-iv-arrow" :disabled="current === 0" aria-label="上一张" @click.stop="prev">‹</button>
        <span class="sg-iv-count">{{ current + 1 }} / {{ images.length }}</span>
        <button class="sg-iv-arrow" :disabled="current === images.length - 1" aria-label="下一张" @click.stop="next">›</button>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  images: { type: Array, default: () => [] },
  index: { type: Number, default: 0 }
})
const emit = defineEmits(['close'])

const current = ref(props.index || 0)

watch(() => props.index, (v) => { current.value = v || 0 })

function prev() {
  if (current.value > 0) current.value -= 1
}

function next() {
  if (current.value < props.images.length - 1) current.value += 1
}

function onKey(e) {
  if (e.key === 'Escape') emit('close')
  else if (e.key === 'ArrowLeft') prev()
  else if (e.key === 'ArrowRight') next()
}

let prevOverflow = ''

onMounted(() => {
  document.addEventListener('keydown', onKey)
  prevOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  document.body.style.overflow = prevOverflow
})
</script>

<style scoped>
.sg-iv {
  position: fixed;
  inset: 0;
  z-index: 3200;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
}

.sg-iv-img {
  max-width: 92vw;
  max-height: 88vh;
  object-fit: contain;
  border-radius: 8px;
}

.sg-iv-close {
  position: absolute;
  top: calc(14px + env(safe-area-inset-top));
  right: 16px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.sg-iv-nav {
  position: absolute;
  bottom: calc(20px + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  gap: 16px;
}

.sg-iv-arrow {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.sg-iv-arrow:disabled {
  opacity: 0.35;
  cursor: default;
}

.sg-iv-count {
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
}
</style>
