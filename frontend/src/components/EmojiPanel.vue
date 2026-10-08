<template>
  <div class="emoji-panel">
    <div v-for="group in groups" :key="group.title" class="emoji-group">
      <p class="emoji-title">{{ group.title }}</p>
      <div class="emoji-grid">
        <button
          v-for="e in group.items"
          :key="e"
          type="button"
          class="emoji-btn"
          @click="$emit('pick', e)"
        >{{ e }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineEmits(['pick'])

const groups = [
  {
    title: '最常用',
    items: ['😀', '😄', '😁', '😆', '😅', '😂', '🙂', '😉', '😊', '😍', '🥰', '😘', '😜', '🤪', '🤗', '🤔']
  },
  {
    title: '心情',
    items: ['😐', '😑', '😶', '🙄', '😏', '😔', '😞', '😢', '😭', '😤', '😡', '🥺', '😱', '😨', '😴', '🤯']
  },
  {
    title: '手势',
    items: ['👍', '👎', '👌', '✌️', '🤞', '🤟', '👏', '🙌', '🤝', '🙏', '💪', '👋', '🤙', '✊', '👊', '🫶']
  },
  {
    title: '生活',
    items: ['❤️', '🔥', '✨', '🎉', '🎁', '🌹', '🌸', '🌈', '☀️', '🌙', '⭐', '☕', '🍜', '🍰', '🍺', '⚽']
  },
  {
    title: '符号',
    items: ['✅', '❌', '❗', '❓', '💯', '👀', '💤', '💡', '📷', '🎵', '🚀', '🐶', '🐱', '🌻', '🍀', '😺']
  }
]
</script>

<style scoped>
.emoji-panel {
  max-height: 232px;
  overflow-y: auto;
  padding: 10px 12px 12px;
}

.emoji-group + .emoji-group {
  margin-top: 8px;
}

.emoji-title {
  font-size: 11px;
  color: var(--text-2, #8a837d);
  margin-bottom: 6px;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
}

.emoji-btn {
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}

.emoji-btn:hover {
  background: rgba(255, 92, 92, 0.12);
  transform: scale(1.12);
}
</style>
