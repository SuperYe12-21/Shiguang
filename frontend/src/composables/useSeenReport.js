import { markPostSeen } from '../api/posts'

const REPORT_INTERVAL_MS = 60 * 1000
const lastReportAt = new Map()

function isRecentlyReported(postId) {
  const at = lastReportAt.get(postId)
  return !!at && Date.now() - at < REPORT_INTERVAL_MS
}

export function reportSeenOnce(postId) {
  if (!postId || isRecentlyReported(postId)) return
  lastReportAt.set(postId, Date.now())
  markPostSeen(postId).catch(() => {
    lastReportAt.delete(postId)
  })
}

/**
 * 观看计时：start()/stop() 控制累计计时，累计满 threshold 毫秒后上报一次。
 * postId 支持传入函数（返回空值时不计时），方便组件按登录状态与作品类型判断。
 */
export function createWatchTimer(postId, thresholdMs = 3000) {
  let timer = null
  let watched = 0
  let last = 0
  const resolveId = typeof postId === 'function' ? postId : () => postId

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    last = 0
  }

  function start() {
    if (timer) return
    const id = resolveId()
    if (!id || isRecentlyReported(id)) return
    last = Date.now()
    timer = setInterval(() => {
      const now = Date.now()
      watched += now - last
      last = now
      const currentId = resolveId()
      if (!currentId || isRecentlyReported(currentId)) {
        stop()
        return
      }
      if (watched >= thresholdMs) {
        stop()
        watched = 0
        reportSeenOnce(currentId)
      }
    }, 500)
  }

  return { start, stop }
}
