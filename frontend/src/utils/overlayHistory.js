// 面板（评论 / 更多 / 分享）的返回键支持：面板打开时压入一条同 URL 的历史条目，
// 浏览器返回键（移动端返回/侧滑、PC 返回按钮）第一次先关掉面板，
// 而不是命中首页"返回即退出"或直接跳走。
const layers = []
let idSeq = 0
// 模块记录的"当前所在历史条目"是否是某个面板压入的条目
let currentLayerId = null
// 面板从 UI 关闭时会先回滚自己压入的条目，这次 popstate 需要静默消费掉
let suppressOnce = false
let suppressTimer = null

function readLayerId(state) {
  return state && typeof state === 'object' && typeof state.sgOverlay === 'number'
    ? state.sgOverlay
    : null
}

/** 压入一层面板历史；onBack 会在用户按返回键关闭该层时调用，返回层 id */
export function pushOverlay(onBack) {
  const id = ++idSeq
  const base = window.history.state
  const state = base && typeof base === 'object' ? { ...base } : {}
  state.sgOverlay = id
  try {
    window.history.pushState(state, '')
  } catch (e) {
    return null
  }
  layers.push({ id, onBack, url: location.pathname + location.search })
  currentLayerId = id
  return id
}

function removeLayer(id) {
  const idx = layers.findIndex((l) => l.id === id)
  if (idx >= 0) layers.splice(idx, 1)
}

function armSuppress() {
  suppressOnce = true
  if (suppressTimer) clearTimeout(suppressTimer)
  // 兜底：back() 被合并/取消时避免标志位卡死，吞掉后续一次正常返回
  suppressTimer = setTimeout(() => {
    suppressOnce = false
    suppressTimer = null
  }, 800)
}

/** 面板从 UI 关闭（点 X / 点遮罩）：它压入的条目还在当前位置就一并回滚 */
export function popOverlay(id) {
  removeLayer(id)
  if (currentLayerId === id) {
    currentLayerId = null
    armSuppress()
    window.history.back()
  }
}

/** 面板因跳转/切换被程序化关闭：只解除绑定，历史条目留给返回时自然消费 */
export function dropOverlay(id) {
  removeLayer(id)
  if (currentLayerId === id) currentLayerId = null
}

/** vue-router 每次完成导航后，同步模块记录的当前位置 */
export function syncOverlayLocation() {
  currentLayerId = readLayerId(window.history.state)
}

/** popstate 入口：返回 true 表示这次返回由面板消费（不再走"首页返回即退出"等逻辑） */
export function handleOverlayPop() {
  const destId = readLayerId(window.history.state)
  const sourceId = currentLayerId
  currentLayerId = destId
  if (suppressOnce) {
    suppressOnce = false
    if (suppressTimer) {
      clearTimeout(suppressTimer)
      suppressTimer = null
    }
    return true
  }
  if (!layers.length) return false
  const top = layers[layers.length - 1]
  if (destId != null && destId === top.id) {
    // 前进/后退回到了面板自己的历史条目（例如面板里跳去主页又返回）：条目不再代表打开的面板
    removeLayer(top.id)
    return true
  }
  if (sourceId == null) return false
  const layer = layers.find((l) => l.id === sourceId)
  if (!layer) return false
  // 只有"从面板条目退回它下面那条同 URL 的条目"才算是按返回关面板，避免误吞其他返回
  if (layer.url !== location.pathname + location.search) return false
  removeLayer(sourceId)
  try {
    if (layer.onBack) layer.onBack()
  } catch (e) {
    // 面板关闭失败不影响返回流程
  }
  return true
}
