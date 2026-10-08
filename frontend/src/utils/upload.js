import { presignUpload } from '../api/posts'

/** 直传 MinIO：必须用原生 XHR，带 axios 的 Authorization 头会被 MinIO 判 400 */
export function putFile(url, file, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100))
    }
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve(true) : reject(new Error('上传失败 ' + xhr.status)))
    xhr.onerror = () => reject(new Error('上传失败'))
    xhr.send(file)
  })
}

/** 上传一张图片，返回 MinIO 对象名 */
export async function uploadImage(file, onProgress) {
  const dot = file.name.lastIndexOf('.')
  const extension = dot >= 0 ? file.name.slice(dot + 1) : ''
  const presign = await presignUpload('IMAGE', file.type || 'application/octet-stream', extension)
  await putFile(presign.uploadUrl, file, onProgress)
  return presign.objectName
}
