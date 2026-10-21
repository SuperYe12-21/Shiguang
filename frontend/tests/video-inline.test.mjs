import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { compile } from '@vue/compiler-dom'
import { parse } from '@vue/compiler-sfc'
import * as Vue from 'vue'

for (const component of ['mobile/FeedItem', 'pc/PcFeedCard']) {
  const filename = new URL(`../src/components/${component}.vue`, import.meta.url)
  const { descriptor, errors } = parse(readFileSync(filename, 'utf8'))
  assert.deepEqual(errors, [])
  const videoTemplate = descriptor.template.content.match(/<video\b[\s\S]*?\/>/)[0]
  const { code } = compile(videoTemplate, { mode: 'function', prefixIdentifiers: true })
  const render = new Function('Vue', code)(Vue)

  function videoNode(overrides = {}) {
    const context = {
      post: { type: 'VIDEO', videoUrl: '/video.mp4', coverUrl: '/cover.jpg' },
      videoFailed: false, posterOff: false, muted: false, active: true, warm: false,
      onVideoTap() {}, onPlay() {}, onPause() {}, onLoadedData() {}, onMeta() {}, onTime() {},
      ...overrides
    }
    return render(context, [])
  }

  test(`${component} requests in-page X5 compositing without the native inline override`, () => {
    const { props } = videoNode()
    assert.equal(props['x5-video-player-type'], 'h5-page')
    assert.equal(Object.hasOwn(props, 'x5-playsinline'), false)
    assert.notEqual(props['x5-video-player-fullscreen'], 'true')
  })

  test(`${component} keeps standard and legacy inline playback enabled`, () => {
    const { props } = videoNode()
    assert.ok(props.playsinline === '' || props.playsinline === true)
    assert.equal(props['webkit-playsinline'], 'true')
    assert.ok(props.controls === undefined || props.controls === false)
  })

  test(`${component} preserves source, sound preference and adjacent preloading`, () => {
    const active = videoNode().props
    assert.equal(active.src, '/video.mp4')
    assert.equal(active.poster, '/cover.jpg')
    assert.equal(active.muted, false)
    assert.equal(active.preload, 'auto')
    assert.equal(videoNode({ active: false, warm: false }).props.preload, 'metadata')
    assert.equal(videoNode({ active: false, warm: true, muted: true }).props.muted, true)
    assert.equal(videoNode({ active: false, warm: true }).props.preload, 'auto')
  })
}
