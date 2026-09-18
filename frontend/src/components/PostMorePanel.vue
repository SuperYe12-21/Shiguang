<template>
  <div class="pm-root">
    <div class="pm-mask" @click="$emit('close')"></div>
    <div class="pm-panel" :class="{ 'is-pc': isPc }">
      <!-- 删除确认 -->
      <template v-if="confirming">
        <p class="pm-title">删除这条作品？</p>
        <p class="pm-sub">删除后不可恢复，点赞和评论会一并清除</p>
        <div class="pm-actions">
          <button class="pm-btn ghost" @click="confirming = false">取消</button>
          <button class="pm-btn danger" :disabled="busy" @click="doDelete">{{ busy ? '删除中…' : '删除' }}</button>
        </div>
      </template>

      <!-- 编辑文案 -->
      <template v-else-if="editing">
        <p class="pm-title">编辑文案</p>
        <input v-model="editTitle" class="pm-input" maxlength="100" placeholder="标题（选填）" />
        <textarea v-model="editDesc" class="pm-textarea" maxlength="500" rows="4" placeholder="简介（选填）"></textarea>
        <div class="pm-actions">
          <button class="pm-btn ghost" @click="editing = false">取消</button>
          <button class="pm-btn primary" :disabled="busy" @click="saveEdit">{{ busy ? '保存中…' : '保存' }}</button>
        </div>
      </template>

      <!-- 主菜单 -->
      <template v-else>
        <p class="pm-title">作品设置</p>
        <div class="pm-item" @click="toggleVisibility">
          <span class="pm-ico">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM9 6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9V6zm3 12c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z" /></svg>
          </span>
          <span class="pm-label">仅自己可见</span>
          <span class="pm-switch" :class="{ on: isPrivate }"><i></i></span>
        </div>
        <div class="pm-item" @click="startEdit">
          <span class="pm-ico">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.996.996 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" /></svg>
          </span>
          <span class="pm-label">编辑文案</span>
        </div>
        <div class="pm-item danger" @click="confirming = true">
          <span class="pm-ico">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" /></svg>
          </span>
          <span class="pm-label">删除作品</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deletePost, setPostVisibility, updatePost } from '../api/posts'

const props = defineProps({
  post: { type: Object, required: true }
})

const emit = defineEmits(['close', 'updated', 'deleted'])

const isPc = window.innerWidth >= 768
const busy = ref(false)
const editing = ref(false)
const confirming = ref(false)
const editTitle = ref('')
const editDesc = ref('')
const isPrivate = computed(() => props.post.visibility === 'PRIVATE')

async function toggleVisibility() {
  if (busy.value) return
  busy.value = true
  const next = isPrivate.value ? 'PUBLIC' : 'PRIVATE'
  try {
    const updated = await setPostVisibility(props.post.id, next)
    emit('updated', { id: props.post.id, visibility: updated.visibility || next })
    ElMessage.success(next === 'PRIVATE' ? '已设为仅自己可见' : '已设为公开')
    emit('close')
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    busy.value = false
  }
}

function startEdit() {
  editTitle.value = props.post.title || ''
  editDesc.value = props.post.description || ''
  editing.value = true
}

async function saveEdit() {
  if (busy.value) return
  busy.value = true
  try {
    const payload = { title: editTitle.value.trim(), description: editDesc.value.trim() }
    const updated = await updatePost(props.post.id, payload)
    emit('updated', {
      id: props.post.id,
      title: updated.title ?? payload.title,
      description: updated.description ?? payload.description
    })
    ElMessage.success('已保存')
    emit('close')
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    busy.value = false
  }
}

async function doDelete() {
  if (busy.value) return
  busy.value = true
  try {
    await deletePost(props.post.id)
    emit('deleted', { id: props.post.id })
    ElMessage.success('已删除')
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.pm-root {
  position: fixed;
  inset: 0;
  z-index: 1500;
}

.pm-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  animation: pm-fade 0.18s ease;
}

.pm-panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18px 18px calc(18px + env(safe-area-inset-bottom));
  border-radius: 18px 18px 0 0;
  background: #16151b;
  color: #fff;
  animation: pm-up 0.22s cubic-bezier(0.22, 1, 0.36, 1);
}

/* PC：贴着右侧互动栏弹出 */
.pm-panel.is-pc {
  left: auto;
  right: 108px;
  top: 50%;
  bottom: auto;
  width: 260px;
  padding: 16px;
  border-radius: 14px;
  transform: translateY(-50%);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
  animation: pm-in-right 0.18s ease;
}

@keyframes pm-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes pm-up {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

@keyframes pm-in-right {
  from { opacity: 0; transform: translate(12px, -50%); }
  to { opacity: 1; transform: translate(0, -50%); }
}

.pm-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}

.pm-sub {
  margin: -6px 0 14px;
  font-size: 13px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.55);
}

.pm-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 4px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 15px;
  transition: background 0.15s;
}

.pm-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.pm-item.danger .pm-label,
.pm-item.danger .pm-ico {
  color: #ff5b5b;
}

.pm-ico {
  display: inline-flex;
  color: rgba(255, 255, 255, 0.8);
}

.pm-label {
  flex: 1;
}

.pm-switch {
  position: relative;
  width: 42px;
  height: 24px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.2);
  transition: background 0.2s;
}

.pm-switch i {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s;
}

.pm-switch.on {
  background: var(--sg-primary, #ff5c5c);
}

.pm-switch.on i {
  transform: translateX(18px);
}

.pm-input,
.pm-textarea {
  width: 100%;
  box-sizing: border-box;
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  font-size: 14px;
  font-family: inherit;
  resize: none;
}

.pm-input::placeholder,
.pm-textarea::placeholder {
  color: rgba(255, 255, 255, 0.35);
}

.pm-input:focus,
.pm-textarea:focus {
  outline: none;
  border-color: var(--sg-primary, #ff5c5c);
}

.pm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}

.pm-btn {
  padding: 9px 20px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: opacity 0.15s;
}

.pm-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.pm-btn.ghost {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.pm-btn.primary {
  background: var(--sg-primary, #ff5c5c);
  color: #fff;
}

.pm-btn.danger {
  background: #ff4d4f;
  color: #fff;
}
</style>
