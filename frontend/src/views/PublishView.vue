<template>
  <div class="pub-page">
    <header class="pub-head">
      <button class="pub-back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" /></svg>
      </button>
      <h1 class="pub-title">发布作品</h1>
      <button class="pub-submit" :disabled="!canSubmit || publishing" @click="submit">
        {{ publishing ? '发布中…' : '发布' }}
      </button>
    </header>

    <main class="pub-body">
      <!-- 类型切换 -->
      <div class="pub-tabs">
        <button class="tab-btn" :class="{ on: type === 'VIDEO' }" @click="switchType('VIDEO')">视频</button>
        <button class="tab-btn" :class="{ on: type === 'IMAGE' }" @click="switchType('IMAGE')">图文</button>
      </div>

      <!-- 视频模式 -->
      <template v-if="type === 'VIDEO'">
        <div v-if="!videoFile" class="pick-box" @click="pickVideo">
          <svg viewBox="0 0 24 24" width="52" height="52" fill="currentColor"><path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z" /></svg>
          <p class="pick-text">点击选择视频</p>
          <span class="pick-hint">支持 MP4 / MOV / AVI / WEBM，最大 500MB</span>
        </div>
        <div v-else class="video-preview">
          <video :src="videoPreviewUrl" controls playsinline></video>
          <div class="video-actions">
            <button class="sg-btn-ghost" @click="pickVideo">重新选择</button>
            <span v-if="videoUploading" class="up-state">上传中 {{ videoProgress }}%</span>
            <span v-else-if="videoObject" class="up-state ok">视频已上传</span>
          </div>
        </div>

        <!-- 封面（选填） -->
        <div class="cover-row">
          <div v-if="!coverFile" class="pick-cover" @click="pickCover">
            <span class="cover-plus">+</span>
            <span class="cover-label">添加封面</span>
            <span class="cover-tip">选填</span>
          </div>
          <div v-else class="cover-preview">
            <img :src="coverPreviewUrl" alt="封面" />
            <button class="cover-remove" aria-label="移除封面" @click="removeCover">×</button>
            <span v-if="coverUploading" class="cover-progress">{{ coverProgress }}%</span>
          </div>
        </div>
      </template>

      <!-- 图文模式 -->
      <template v-else>
        <div class="img-grid">
          <div v-for="(img, i) in images" :key="i" class="img-cell">
            <img :src="img.preview" alt="图片" />
            <button class="img-remove" aria-label="移除" @click="removeImage(i)">×</button>
            <span v-if="img.uploading" class="img-progress">{{ img.progress }}%</span>
            <span v-else-if="img.object" class="img-done">✓</span>
          </div>
          <div v-if="images.length < 18" class="img-add" @click="pickImages">
            <span class="add-plus">+</span>
            <span class="add-count">{{ images.length }}/18</span>
          </div>
        </div>
      </template>

      <!-- 文案 -->
      <div class="field">
        <input v-model="title" class="pub-input" maxlength="100" placeholder="添加标题（选填）" />
      </div>
      <div class="field">
        <textarea v-model="description" class="pub-textarea" maxlength="500" rows="4" placeholder="分享这一刻的故事…（选填）"></textarea>
      </div>
    </main>

    <!-- 隐藏文件选择 -->
    <input ref="videoInput" type="file" accept="video/mp4,video/quicktime,video/x-msvideo,video/webm" class="hidden-input" @change="onVideoPicked" />
    <input ref="coverInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif" class="hidden-input" @change="onCoverPicked" />
    <input ref="imagesInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple class="hidden-input" @change="onImagesPicked" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { presignUpload, createPost } from '../api/posts'

const router = useRouter()

const type = ref('VIDEO')
const title = ref('')
const description = ref('')

const videoInput = ref(null)
const coverInput = ref(null)
const imagesInput = ref(null)

const videoFile = ref(null)
const videoPreviewUrl = ref('')
const videoObject = ref('')
const videoUploading = ref(false)
const videoProgress = ref(0)

const coverFile = ref(null)
const coverPreviewUrl = ref('')
const coverObject = ref('')
const coverUploading = ref(false)
const coverProgress = ref(0)

const images = ref([])
const publishing = ref(false)

const MAX_VIDEO_MB = 500
const MAX_IMAGE_MB = 20

const canSubmit = computed(() => {
  if (publishing.value) return false
  if (type.value === 'VIDEO') {
    return !!videoObject.value && !videoUploading.value
  }
  return images.value.length > 0 && images.value.every((i) => i.object && !i.uploading)
})

function switchType(next) {
  if (type.value === next) return
  type.value = next
}

function goBack() {
  if (router.options.history.state.back) {
    router.back()
  } else {
    router.push('/feed')
  }
}

function pickVideo() {
  videoInput.value && videoInput.value.click()
}

async function onVideoPicked(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (file.size > MAX_VIDEO_MB * 1024 * 1024) {
    ElMessage.error('视频不能超过 ' + MAX_VIDEO_MB + 'MB')
    return
  }
  clearVideo()
  videoFile.value = file
  videoPreviewUrl.value = URL.createObjectURL(file)
  await uploadVideo(file)
}

async function uploadVideo(file) {
  videoUploading.value = true
  videoProgress.value = 0
  try {
    const object = await uploadFile('VIDEO', file, (p) => (videoProgress.value = p))
    videoObject.value = object
  } catch (err) {
    ElMessage.error('视频上传失败，请重试')
    clearVideo()
  } finally {
    videoUploading.value = false
  }
}

function pickCover() {
  coverInput.value && coverInput.value.click()
}

async function onCoverPicked(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
    ElMessage.error('封面不能超过 ' + MAX_IMAGE_MB + 'MB')
    return
  }
  removeCover()
  coverFile.value = file
  coverPreviewUrl.value = URL.createObjectURL(file)
  coverUploading.value = true
  coverProgress.value = 0
  try {
    coverObject.value = await uploadFile('COVER', file, (p) => (coverProgress.value = p))
  } catch (err) {
    ElMessage.error('封面上传失败，请重试')
    removeCover()
  } finally {
    coverUploading.value = false
  }
}

function removeCover() {
  if (coverPreviewUrl.value) URL.revokeObjectURL(coverPreviewUrl.value)
  coverFile.value = null
  coverPreviewUrl.value = ''
  coverObject.value = ''
  coverUploading.value = false
  coverProgress.value = 0
}

function pickImages() {
  imagesInput.value && imagesInput.value.click()
}

async function onImagesPicked(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  for (const file of files) {
    if (images.value.length >= 18) {
      ElMessage.warning('最多选择 18 张图片')
      break
    }
    if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
      ElMessage.error(file.name + ' 超过 ' + MAX_IMAGE_MB + 'MB，已跳过')
      continue
    }
    const item = reactive({ file, preview: URL.createObjectURL(file), object: '', uploading: true, progress: 0 })
    images.value.push(item)
    uploadImage(item)
  }
}

async function uploadImage(item) {
  try {
    item.object = await uploadFile('IMAGE', item.file, (p) => (item.progress = p))
  } catch (err) {
    ElMessage.error('图片上传失败：' + (item.file.name || ''))
    images.value = images.value.filter((x) => x !== item)
    URL.revokeObjectURL(item.preview)
  } finally {
    item.uploading = false
  }
}

function removeImage(i) {
  const item = images.value[i]
  if (!item) return
  URL.revokeObjectURL(item.preview)
  images.value.splice(i, 1)
}

async function uploadFile(uploadType, file, onProgress) {
  const dot = file.name.lastIndexOf('.')
  const extension = dot >= 0 ? file.name.slice(dot + 1) : ''
  const presign = await presignUpload(uploadType, file.type || 'application/octet-stream', extension)
  // 用原生 XHR 直传 MinIO：不能带 axios 的 Authorization 头，否则 MinIO 返回 400
  await putFile(presign.uploadUrl, file, onProgress)
  return presign.objectName
}

function putFile(url, file, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100))
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve()
      } else {
        reject(new Error('上传失败 HTTP ' + xhr.status))
      }
    }
    xhr.onerror = () => reject(new Error('网络异常'))
    xhr.send(file)
  })
}

async function submit() {
  if (!canSubmit.value) return
  publishing.value = true
  try {
    const payload = { type: type.value, title: title.value.trim(), description: description.value.trim() }
    if (type.value === 'VIDEO') {
      payload.videoObject = videoObject.value
      if (coverObject.value) payload.coverObject = coverObject.value
    } else {
      payload.images = images.value.map((i) => i.object)
    }
    await createPost(payload)
    ElMessage.success(type.value === 'VIDEO' ? '已发布，视频转码中…' : '发布成功')
    router.push('/feed')
  } catch (err) {
    // 错误提示已由拦截器处理
  } finally {
    publishing.value = false
  }
}

function clearVideo() {
  if (videoPreviewUrl.value) URL.revokeObjectURL(videoPreviewUrl.value)
  videoFile.value = null
  videoPreviewUrl.value = ''
  videoObject.value = ''
  videoUploading.value = false
  videoProgress.value = 0
}

onBeforeUnmount(() => {
  clearVideo()
  removeCover()
  images.value.forEach((i) => URL.revokeObjectURL(i.preview))
})
</script>

<style scoped>
.pub-page {
  min-height: 100vh;
  height: 100%;
  background: var(--sg-bg);
  display: flex;
  flex-direction: column;
}

.pub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: var(--sg-glass);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--sg-line);
  position: sticky;
  top: 0;
  z-index: 10;
}

.pub-back {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sg-text-2);
  transition: background 0.2s;
}

.pub-back:hover {
  background: var(--sg-primary-soft);
  color: var(--sg-primary-deep);
}

.pub-title {
  font-size: 17px;
  font-weight: 700;
}

.pub-submit {
  height: 34px;
  padding: 0 18px;
  border-radius: var(--sg-radius-full);
  background: var(--sg-gradient-deep);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  transition: opacity 0.2s, transform 0.15s;
}

.pub-submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pub-submit:not(:disabled):hover {
  transform: scale(1.04);
}

.pub-body {
  flex: 1;
  max-width: 560px;
  width: 100%;
  margin: 0 auto;
  padding: 16px 14px 40px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pub-tabs {
  display: flex;
  gap: 8px;
}

.tab-btn {
  padding: 8px 26px;
  border-radius: var(--sg-radius-full);
  background: var(--sg-bg-warm);
  color: var(--sg-text-2);
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s;
}

.tab-btn.on {
  background: var(--sg-gradient-deep);
  color: #fff;
  box-shadow: 0 4px 12px rgba(232, 75, 75, 0.25);
}

.pick-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 240px;
  border-radius: var(--sg-radius-lg);
  border: 2px dashed var(--sg-line);
  background: var(--sg-bg-warm);
  color: var(--sg-primary-deep);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.pick-box:hover {
  border-color: var(--sg-primary);
  background: var(--sg-primary-soft);
}

.pick-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--sg-text);
}

.pick-hint {
  font-size: 12px;
  color: var(--sg-text-3);
}

.video-preview {
  border-radius: var(--sg-radius-lg);
  overflow: hidden;
  background: #000;
}

.video-preview video {
  width: 100%;
  max-height: 46vh;
  display: block;
}

.video-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--sg-glass);
}

.up-state {
  font-size: 13px;
  color: var(--sg-text-3);
}

.up-state.ok {
  color: #34a853;
  font-weight: 600;
}

.sg-btn-ghost {
  padding: 6px 16px;
  border-radius: var(--sg-radius-full);
  border: 1px solid var(--sg-line);
  color: var(--sg-text-2);
  font-size: 13px;
  transition: all 0.2s;
}

.sg-btn-ghost:hover {
  border-color: var(--sg-primary);
  color: var(--sg-primary-deep);
}

.cover-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pick-cover {
  width: 96px;
  height: 128px;
  border-radius: var(--sg-radius);
  border: 2px dashed var(--sg-line);
  background: var(--sg-bg-warm);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  color: var(--sg-primary-deep);
}

.pick-cover:hover {
  border-color: var(--sg-primary);
  background: var(--sg-primary-soft);
}

.cover-plus {
  font-size: 26px;
  font-weight: 300;
  line-height: 1;
}

.cover-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--sg-text);
}

.cover-tip {
  font-size: 11px;
  color: var(--sg-text-3);
}

.cover-preview {
  position: relative;
  width: 96px;
  height: 128px;
  border-radius: var(--sg-radius);
  overflow: hidden;
  background: var(--sg-bg-warm);
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-remove {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-progress {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 3px 0;
  text-align: center;
  font-size: 11px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
}

.img-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.img-cell,
.img-add {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: var(--sg-radius);
  overflow: hidden;
  background: var(--sg-bg-warm);
}

.img-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.img-remove {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.img-progress {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 3px 0;
  text-align: center;
  font-size: 11px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
}

.img-done {
  position: absolute;
  right: 6px;
  bottom: 6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(52, 168, 83, 0.9);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.img-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 2px dashed var(--sg-line);
  cursor: pointer;
  color: var(--sg-primary-deep);
  transition: border-color 0.2s, background 0.2s;
}

.img-add:hover {
  border-color: var(--sg-primary);
  background: var(--sg-primary-soft);
}

.add-plus {
  font-size: 30px;
  font-weight: 300;
  line-height: 1;
}

.add-count {
  font-size: 12px;
  color: var(--sg-text-3);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pub-input {
  height: 44px;
  padding: 0 14px;
  border-radius: var(--sg-radius);
  border: 1px solid var(--sg-line);
  background: var(--sg-bg-warm);
  font-size: 15px;
  outline: none;
  transition: border-color 0.2s;
}

.pub-input:focus,
.pub-textarea:focus {
  border-color: var(--sg-primary);
}

.pub-textarea {
  padding: 12px 14px;
  border-radius: var(--sg-radius);
  border: 1px solid var(--sg-line);
  background: var(--sg-bg-warm);
  font-size: 15px;
  line-height: 1.6;
  outline: none;
  resize: none;
  font-family: inherit;
  transition: border-color 0.2s;
}

.hidden-input {
  display: none;
}
</style>
