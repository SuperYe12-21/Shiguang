import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { compileScript, parse } from '@vue/compiler-sfc'
import { transformSync } from 'esbuild'
import * as vue from 'vue'

globalThis.document = {
  body: { style: {} },
  fullscreenElement: null,
  addEventListener() {},
  removeEventListener() {}
}

async function settle() {
  await new Promise(setImmediate)
  await vue.nextTick()
}

function nodes(root, predicate) {
  return [...(predicate(root) ? [root] : []), ...(root.children || []).flatMap((child) => nodes(child, predicate))]
}

async function mountCard({ autoplayAllowed = false, deferAutoplay = false } = {}) {
  const filename = new URL('../src/components/pc/PcFeedCard.vue', import.meta.url)
  const { descriptor, errors } = parse(readFileSync(filename, 'utf8'))
  assert.deepEqual(errors, [])
  const compiled = compileScript(descriptor, { id: 'pc-playback-test', inlineTemplate: true })
  const { code } = transformSync(compiled.content, { format: 'cjs', loader: 'js' })
  const module = { exports: {} }
  new Function('require', 'module', 'exports', code)((source) => {
    if (source === 'vue') return vue
    if (source.endsWith('/stores/auth')) return { useAuthStore: () => ({ userId: 1, isLoggedIn: false }) }
    if (source.endsWith('/composables/useSeenReport')) return { createWatchTimer: () => ({ start() {}, stop() {} }) }
    throw new Error(`Unexpected dependency: ${source}`)
  }, module, module.exports)
  let userGesture = false
  let audioAllowed = autoplayAllowed
  let rejectAutoplay
  const calls = []
  const emitted = []
  const teleportRoot = { children: [] }
  const renderer = vue.createRenderer({
    querySelector: () => teleportRoot,
    createElement(type) {
      const node = { type, props: {}, children: [] }
      if (type === 'video') {
        Object.assign(node, {
          muted: false, paused: true, readyState: 4, duration: 45, currentTime: 0,
          addEventListener() {},
          play() {
            calls.push({ muted: node.muted, userGesture })
            if (deferAutoplay && calls.length === 1) {
              return new Promise((resolve, reject) => { rejectAutoplay = reject })
            }
            if (!node.muted && !audioAllowed && !userGesture) {
              const error = new Error('Autoplay requires user activation')
              error.name = 'NotAllowedError'
              return Promise.reject(error)
            }
            if (!node.muted && userGesture) audioAllowed = true
            node.paused = false
            node.props.onPlay?.()
            return Promise.resolve()
          },
          pause() {
            if (!node.paused) {
              node.paused = true
              node.props.onPause?.()
            }
          }
        })
      }
      return node
    },
    createText: (text) => ({ type: '#text', text, children: [] }),
    createComment: (text) => ({ type: '#comment', text, children: [] }),
    setText: (node, text) => { node.text = text },
    setElementText: (node, text) => { node.text = text; node.children = [] },
    patchProp(node, key, previous, value) {
      node.props[key] = value
      if (key === 'muted') node.muted = value
    },
    insert(node, parent, anchor = null) {
      if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
      const index = anchor ? parent.children.indexOf(anchor) : -1
      parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
      node.parent = parent
    },
    remove(node) {
      if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] || null
  })
  const active = vue.ref(true)
  const card = vue.ref(null)
  const root = { children: [] }
  const app = renderer.createApp({
    setup: () => () => vue.h(module.exports.default, {
      ref: card,
      active: active.value,
      post: { id: 1001, type: 'VIDEO', videoUrl: '/video.mp4', title: '测试视频', author: { id: 2 } },
      onLike: () => emitted.push('like')
    })
  })
  app.mount(root)
  await settle()
  const video = nodes(root, (node) => node.type === 'video')[0]
  const media = nodes(root, (node) => node.props?.class === 'media')[0]
  return {
    app, video, calls, active, card, emitted,
    rejectAutoplay() {
      const error = new Error('Delayed autoplay rejection')
      error.name = 'NotAllowedError'
      rejectAutoplay(error)
    },
    click() {
      userGesture = true
      try { media.props.onClick({ clientX: 100, clientY: 100 }) } finally { userGesture = false }
    },
    toggle() {
      userGesture = true
      try { card.value.togglePlay() } finally { userGesture = false }
    }
  }
}

test('a click immediately unlocks audio after the first-video autoplay falls back to muted', async () => {
  const harness = await mountCard()
  try {
    assert.equal(harness.video.muted, true)
    assert.equal(harness.video.paused, false)
    harness.click()
    await settle()
    assert.equal(harness.video.muted, false)
    assert.equal(harness.video.paused, false)
    assert.deepEqual(harness.calls.at(-1), { muted: false, userGesture: true })
  } finally { harness.app.unmount() }
})

test('the exposed playback action restores audio and then keeps normal pause and resume behavior', async () => {
  const harness = await mountCard()
  try {
    harness.toggle()
    await settle()
    assert.equal(harness.video.muted, false)
    assert.equal(harness.video.paused, false)
    harness.toggle()
    assert.equal(harness.video.paused, true)
    harness.toggle()
    await settle()
    assert.equal(harness.video.paused, false)
    assert.equal(harness.video.muted, false)
  } finally { harness.app.unmount() }
})

test('an already audible autoplay still pauses on a normal single click', async () => {
  const harness = await mountCard({ autoplayAllowed: true })
  try {
    assert.equal(harness.video.muted, false)
    harness.click()
    await new Promise((resolve) => setTimeout(resolve, 290))
    assert.equal(harness.video.paused, true)
    assert.equal(harness.video.muted, false)
  } finally { harness.app.unmount() }
})

test('unlocking audio cannot start a card that has already become inactive', async () => {
  const harness = await mountCard()
  try {
    harness.active.value = false
    await settle()
    const count = harness.calls.length
    harness.toggle()
    await settle()
    assert.equal(harness.calls.length, count)
    assert.equal(harness.video.paused, true)
  } finally { harness.app.unmount() }
})

test('an older autoplay rejection cannot mute a newer successful user playback', async () => {
  const harness = await mountCard({ deferAutoplay: true })
  try {
    harness.toggle()
    await settle()
    assert.equal(harness.video.muted, false)
    assert.equal(harness.video.paused, false)
    harness.rejectAutoplay()
    await settle()
    assert.equal(harness.video.muted, false)
    assert.equal(harness.video.paused, false)
  } finally { harness.app.unmount() }
})

test('a late autoplay rejection cannot resume an inactive video through the muted fallback', async () => {
  const harness = await mountCard({ deferAutoplay: true })
  try {
    harness.active.value = false
    await settle()
    harness.rejectAutoplay()
    await settle()
    assert.equal(harness.calls.length, 1)
    assert.equal(harness.video.paused, true)
  } finally { harness.app.unmount() }
})
