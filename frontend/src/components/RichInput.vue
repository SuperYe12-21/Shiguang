<template>
  <div ref="rootEl" class="ri" :class="{ 'ri-off': disabled, 'ri-dark': dark }">
    <div v-if="items.length" class="ri-strip">
      <div v-for="(img, i) in items" :key="img.uid" class="ri-thumb">
        <img :src="img.preview" alt="已选图片" />
        <span v-if="img.uploading" class="ri-thumb-mask">{{ img.progress || 0 }}%</span>
        <button type="button" class="ri-thumb-del" aria-label="移除图片" @click="removeImage(i)">×</button>
      </div>
      <span class="ri-strip-tip">{{ items.length }}/{{ maxImages }}</span>
    </div>

    <div class="ri-row">
      <button type="button" class="ri-btn" :class="{ on: emojiOpen }" :disabled="disabled" title="表情" @click="toggleEmoji">😊</button>
      <input
        ref="inputEl"
        class="ri-input"
        :value="modelValue"
        :placeholder="placeholder"
        :maxlength="maxlength"
        :disabled="disabled"
        @input="onInput"
        @focus="onInputFocus"
        @keydown.enter.prevent="submit"
      />
      <button v-if="hasContent" type="button" class="ri-send" :disabled="disabled || sending || uploading" @click="submit">
        {{ sending ? '发送中…' : '发送' }}
      </button>
      <button v-else type="button" class="ri-btn ri-plus" :disabled="disabled || uploading" title="发送图片" @click="pickImages">+</button>
    </div>

    <div v-if="emojiOpen" class="ri-emoji"><EmojiPanel @pick="insertEmoji" /></div>

    <input ref="fileEl" class="ri-file" type="file" accept="image/*" :multiple="maxImages > 1" @change="onFiles" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import EmojiPanel from './EmojiPanel.vue'
import { uploadImage } from '../utils/upload'

const props = defineProps({
  modelValue: { type: String, default: '' },
  images: { type: Array, default: () => [] },
  maxImages: { type: Number, default: 3 },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  sending: { type: Boolean, default: false },
  maxlength: { type: Number, default: 1000 },
  dark: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'update:images', 'submit'])

const inputEl = ref(null)
const fileEl = ref(null)
const rootEl = ref(null)
const emojiOpen = ref(false)

/** 已选图片的本地副本：对象内部字段（进度、对象名）在子组件里改，需走响应式数组 */
const items = ref([])
const hasContent = computed(() => props.modelValue.trim() !== '' || items.value.length > 0)
const uploading = computed(() => items.value.some((img) => img.uploading))

// 外部清空（发送成功后）时同步本地列表
watch(() => props.images, (v) => {
  if (v.length === 0) items.value = []
})

function syncImages() {
  emit('update:images', items.value.map((img) => ({ ...img })))
}

function onInput(e) {
  emit('update:modelValue', e.target.value)
}

function toggleEmoji() {
  emojiOpen.value = !emojiOpen.value
  // 打开表情面板时收起键盘，两者只留一个
  if (emojiOpen.value) inputEl.value?.blur()
}

/** 点输入框：收起表情面板，只弹键盘 */
function onInputFocus() {
  if (emojiOpen.value) emojiOpen.value = false
}

/** 点组件之外的空白处收起表情面板（移动端点击消息区、评论区空白都算） */
function onDocPointerDown(e) {
  if (rootEl.value && rootEl.value.contains(e.target)) return
  emojiOpen.value = false
}

watch(emojiOpen, (open) => {
  if (open) document.addEventListener('pointerdown', onDocPointerDown, true)
  else document.removeEventListener('pointerdown', onDocPointerDown, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown, true)
})

/** 在光标处插入表情 */
function insertEmoji(emoji) {
  const el = inputEl.value
  const text = props.modelValue || ''
  const focused = !!el && document.activeElement === el
  const start = focused ? (el.selectionStart ?? text.length) : text.length
  const end = focused ? (el.selectionEnd ?? text.length) : text.length
  const next = text.slice(0, start) + emoji + text.slice(end)
  emit('update:modelValue', next)
  // 不主动 focus：移动端插入表情后不该弹出键盘；光标只在输入框本来就聚焦时更新
  if (focused) {
    requestAnimationFrame(() => {
      const pos = start + emoji.length
      el.setSelectionRange(pos, pos)
    })
  }
}

function pickImages() {
  emojiOpen.value = false
  fileEl.value?.click()
}

async function onFiles(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  const room = props.maxImages - items.value.length
  if (room <= 0) {
    ElMessage.warning(`最多 ${props.maxImages} 张图片`)
    return
  }
  const picked = files.slice(0, room)
  if (files.length > room) ElMessage.warning(`最多 ${props.maxImages} 张图片，已保留前 ${room} 张`)
  const added = picked.map((file, i) => ({
    uid: `${Date.now()}-${i}-${Math.round(Math.random() * 1e6)}`,
    file,
    preview: URL.createObjectURL(file),
    object: '',
    progress: 0,
    uploading: true
  }))
  for (const raw of added) {
    items.value.push(raw)
  }
  syncImages()
  // push 之后再取出来，拿到的是响应式代理，改字段才能触发视图更新
  const proxies = added.map((raw) => items.value.find((x) => x.uid === raw.uid))
  for (const item of proxies) {
    try {
      item.object = await uploadImage(item.file, (p) => { item.progress = p })
      item.uploading = false
    } catch (err) {
      ElMessage.error('图片上传失败')
      const index = items.value.findIndex((x) => x.uid === item.uid)
      if (index >= 0) items.value.splice(index, 1)
      URL.revokeObjectURL(item.preview)
    }
    syncImages()
  }
}

function removeImage(index) {
  const item = items.value[index]
  if (!item) return
  if (item.preview) URL.revokeObjectURL(item.preview)
  items.value.splice(index, 1)
  syncImages()
}

function submit() {
  if (props.disabled || props.sending || uploading.value) return
  if (!hasContent.value) return
  emit('submit')
}

defineExpose({ focus: () => inputEl.value?.focus(), closeEmoji: () => { emojiOpen.value = false } })
</script>

<style scoped>
.ri {
  position: relative;
  border-top: 1px solid var(--line, rgba(38, 34, 31, 0.08));
  background: var(--card, #fff);
}

.ri-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 0;
  overflow-x: auto;
}

.ri-thumb {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 10px;
  overflow: hidden;
  flex: none;
  background: rgba(0, 0, 0, 0.06);
}

.ri-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ri-thumb-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 11px;
}

.ri-thumb-del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.ri-strip-tip {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-2, #8a837d);
  flex: none;
}

.ri-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
}

.ri-btn {
  width: 38px;
  height: 38px;
  flex: none;
  border: none;
  border-radius: 50%;
  background: rgba(38, 34, 31, 0.06);
  font-size: 19px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.18s, transform 0.18s;
}

.ri-btn:hover {
  background: rgba(255, 92, 92, 0.14);
}

.ri-btn.on {
  background: rgba(255, 92, 92, 0.18);
}

.ri-plus {
  font-size: 24px;
  font-weight: 500;
  color: #ff5c5c;
}

.ri-input {
  flex: 1;
  min-width: 0;
  height: 38px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: rgba(38, 34, 31, 0.06);
  font-size: 14px;
  font-family: inherit;
  color: inherit;
}

.ri-input:focus {
  outline: 2px solid rgba(255, 92, 92, 0.35);
  outline-offset: -1px;
}

.ri-send {
  flex: none;
  height: 38px;
  padding: 0 20px;
  border: none;
  border-radius: 999px;
  background: #ff5c5c;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.18s;
}

.ri-send:hover:not(:disabled) {
  background: #ff4747;
}

.ri-send:disabled {
  opacity: 0.55;
  cursor: default;
}

.ri-emoji {
  border-top: 1px solid var(--line, rgba(38, 34, 31, 0.08));
  background: var(--card, #fff);
}

.ri-file {
  display: none;
}

.ri-dark {
  --card: rgba(255, 255, 255, 0.06);
  --line: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.ri-dark .ri-btn {
  background: rgba(255, 255, 255, 0.14);
}

.ri-dark .ri-input {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}

.ri-dark .ri-input::placeholder {
  color: rgba(255, 255, 255, 0.55);
}
</style>
