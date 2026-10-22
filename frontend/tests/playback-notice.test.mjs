import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { test } from 'node:test'
import { compileScript, parse } from '@vue/compiler-sfc'
import { transformSync } from 'esbuild'
import * as vue from 'vue'

const filename = new URL('../src/components/mobile/PlaybackCompatibilityNotice.vue', import.meta.url)
const android = 'Mozilla/5.0 (Linux; Android 16; PJZ110) AppleWebKit/537.36 Chrome/115.0 Mobile Safari/537.36'
const dismissalKey = 'sg_playback_compat_dismissed'

function findAll(root, predicate) {
  return [...(predicate(root) ? [root] : []), ...(root.children || []).flatMap((child) => findAll(child, predicate))]
}

function mountNotice(userAgent, storage = new Map(), storageBlocked = false) {
  assert.ok(existsSync(filename), 'compatibility notice component must exist')
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { userAgent } })
  globalThis.sessionStorage = {
    getItem(key) {
      if (storageBlocked) throw new Error('Storage disabled')
      return storage.get(key) || null
    },
    setItem(key, value) {
      if (storageBlocked) throw new Error('Storage disabled')
      storage.set(key, value)
    }
  }
  const { descriptor, errors } = parse(readFileSync(filename, 'utf8'))
  assert.deepEqual(errors, [])
  const compiled = compileScript(descriptor, { id: 'playback-notice-test', inlineTemplate: true })
  const { code } = transformSync(compiled.content, { format: 'cjs', loader: 'js' })
  const module = { exports: {} }
  new Function('require', 'module', 'exports', code)((source) => {
    assert.equal(source, 'vue')
    return vue
  }, module, module.exports)
  const renderer = vue.createRenderer({
    createElement: (type) => ({ type, props: {}, children: [] }),
    createText: (text) => ({ type: '#text', text, children: [] }),
    createComment: (text) => ({ type: '#comment', text, children: [] }),
    setText: (node, text) => { node.text = text },
    setElementText: (node, text) => { node.text = text; node.children = [] },
    patchProp: (node, key, previous, value) => { node.props[key] = value },
    insert(node, parent, anchor = null) {
      const index = anchor ? parent.children.indexOf(anchor) : -1
      parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
      node.parent = parent
    },
    remove(node) {
      if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
      node.parent = null
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] || null
  })
  const root = { children: [] }
  const app = renderer.createApp({
    setup: () => () => vue.h(module.exports.default, null, {
      default: () => vue.h('nav', { 'aria-label': '原有导航' }, '推荐 朋友 搜索')
    })
  })
  app.mount(root)
  return { app, root, notices: () => findAll(root, (node) => node.props?.role === 'status'),
    navigation: () => findAll(root, (node) => node.type === 'nav') }
}

test('compatibility notice occupies only the existing mobile header, not the media stage', () => {
  const view = readFileSync(new URL('../src/views/FeedView.vue', import.meta.url), 'utf8')
  assert.match(view, /<header class="m-header">\s*<PlaybackCompatibilityNotice>/)
  assert.match(view, /<\/PlaybackCompatibilityNotice>\s*<\/header>/)
  assert.equal((view.match(/<PlaybackCompatibilityNotice>/g) || []).length, 1)
})

for (const browser of ['HeyTapBrowser/40.10.23.1', 'OppoBrowser/40.0', 'MQQBrowser/19.4.5']) {
  test(`warns about possible native controls in Android ${browser}`, () => {
    const mounted = mountNotice(`${android} ${browser}`)
    assert.equal(mounted.notices().length, 1)
    const text = findAll(mounted.root, (node) => typeof node.text === 'string').map((node) => node.text).join(' ')
    assert.match(text, /可能/)
    assert.match(text, /微信/)
    assert.equal(findAll(mounted.root, (node) => node.type === 'video' || node.props?.role === 'dialog').length, 0)
    mounted.app.unmount()
  })
}

for (const [browser, agent] of [
  ['WeChat with QQ kernel tokens', `${android} MQQBrowser/6.2 MicroMessenger/8.0.65`],
  ['WeChat with vendor tokens', `${android} HeyTapBrowser/40.0 MicroMessenger/8.0.65`],
  ['Android Chrome', android],
  ['iOS Safari', 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) AppleWebKit/605.1.15 Version/18.0 Mobile Safari/604.1'],
  ['desktop Edge', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/155.0 Safari/537.36 Edg/155.0']
]) {
  test(`preserves navigation without warning in ${browser}`, () => {
    const mounted = mountNotice(agent)
    assert.equal(mounted.notices().length, 0)
    assert.equal(mounted.navigation().length, 1)
    mounted.app.unmount()
  })
}

test('dismissal restores navigation and is remembered for this browser session only', async () => {
  const storage = new Map()
  const agent = `${android} HeyTapBrowser/40.10.23.1`
  const mounted = mountNotice(agent, storage)
  const dismiss = findAll(mounted.root, (node) => node.props?.['aria-label'] === '关闭视频兼容提示')[0]
  assert.ok(dismiss)
  dismiss.props.onClick()
  await vue.nextTick()
  assert.equal(mounted.notices().length, 0)
  assert.equal(mounted.navigation().length, 1)
  assert.equal(storage.get(dismissalKey), '1')
  mounted.app.unmount()
  const revisited = mountNotice(agent, storage)
  assert.equal(revisited.notices().length, 0)
  revisited.app.unmount()
  const freshSession = mountNotice(agent)
  assert.equal(freshSession.notices().length, 1)
  freshSession.app.unmount()
})

test('storage restrictions do not prevent playback navigation or dismissal', async () => {
  const mounted = mountNotice(`${android} MQQBrowser/19.4.5`, new Map(), true)
  assert.equal(mounted.notices().length, 1)
  findAll(mounted.root, (node) => node.props?.['aria-label'] === '关闭视频兼容提示')[0].props.onClick()
  await vue.nextTick()
  assert.equal(mounted.navigation().length, 1)
  mounted.app.unmount()
})
