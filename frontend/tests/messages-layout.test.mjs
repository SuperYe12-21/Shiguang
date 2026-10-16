import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'
import { compileScript, compileStyle, parse } from '@vue/compiler-sfc'
import { transformSync } from 'esbuild'
import * as vue from 'vue'
import * as vueRouter from 'vue-router'

const require = createRequire(import.meta.url)
const viewPath = (name) => fileURLToPath(new URL(`../src/views/${name}.vue`, import.meta.url))
const descriptors = new Map(['MessagesView', 'ChatView'].map((name) => {
  const filename = viewPath(name)
  const { descriptor, errors } = parse(readFileSync(filename, 'utf8'), { filename })
  assert.deepEqual(errors, [])
  return [name, descriptor]
}))

const renderer = vue.createRenderer({
  createElement: (type) => ({ type, props: {}, children: [], scrollHeight: 1000, clientHeight: 600, scrollTop: 0 }),
  createText: (text) => ({ type: '#text', text, children: [] }),
  createComment: (text) => ({ type: '#comment', text, children: [] }),
  setText: (node, text) => { node.text = text },
  setElementText: (node, text) => { node.text = text; node.children = [] },
  patchProp: (node, key, previous, value) => { node.props[key] = value },
  insert(node, parent, anchor = null) {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
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

function findAll(node, predicate) {
  return [...(predicate(node) ? [node] : []), ...(node.children || []).flatMap((child) => findAll(child, predicate))]
}

function byClass(root, name) {
  return findAll(root, (node) => String(node.props?.class || '').split(' ').includes(name))
}

async function settle() {
  await new Promise(setImmediate)
  await vue.nextTick()
}

async function harness({ width = 1440, route = '/messages', chatProps = {}, chatResponse, readResponse, conversationResponse } = {}) {
  const events = new Map()
  globalThis.window = {
    innerWidth: width,
    history: { length: 1 },
    addEventListener(name, handler) {
      if (!events.has(name)) events.set(name, new Set())
      events.get(name).add(handler)
    },
    removeEventListener: (name, handler) => events.get(name)?.delete(handler)
  }
  const router = vueRouter.createRouter({
    history: vueRouter.createMemoryHistory(),
    routes: [
      { path: '/chat/:userId', component: { render: () => null } },
      { path: '/:pathMatch(.*)*', component: { render: () => null } }
    ]
  })
  await router.push(route)
  const calls = { chat: [], read: [], conversations: [], sent: [] }
  const listeners = new Set()
  const auth = vue.reactive({ userId: 1, isLoggedIn: true, user: { id: 1, nickname: '本人' } })
  const message = vue.reactive({
    unread: 5,
    refresh() {},
    subscribe(handler) { listeners.add(handler); return () => listeners.delete(handler) }
  })
  const notification = vue.reactive({ unread: { like: 2, comment: 1, follow: 0, total: 3 }, friendsUnread: true })
  const conversations = [
    { id: 20, peer: { id: 2, nickname: '会话甲' }, unread: 3, lastContent: '甲的消息' },
    { id: 30, peer: { id: 3, nickname: '会话乙' }, unread: 2, lastContent: '乙的消息' }
  ]
  const api = {
    async fetchConversations(cursor, limit) {
      calls.conversations.push({ cursor, limit })
      return conversationResponse ? conversationResponse(cursor) : { items: structuredClone(conversations), hasMore: false }
    },
    async fetchChat(peerId, cursor, limit) {
      calls.chat.push({ peerId, cursor, limit })
      return chatResponse ? chatResponse(peerId, cursor, limit) : {
        peer: { id: peerId, nickname: `用户${peerId}` },
        items: [{ id: peerId * 100, senderId: peerId, receiverId: 1, content: `来自${peerId}`, type: 'TEXT' }],
        hasMore: false, canSend: true
      }
    },
    async markConversationRead(peerId) {
      calls.read.push(peerId)
      return readResponse ? readResponse(peerId) : { messages: 0 }
    },
    async sendMessage(peerId, payload) {
      calls.sent.push({ peerId, payload })
      return { id: 900, senderId: 1, receiverId: peerId, ...payload }
    }
  }
  const richInput = {
    props: ['modelValue', 'images', 'dark', 'disabled', 'sending'],
    emits: ['update:modelValue', 'update:images', 'submit'],
    setup(props, { emit, expose }) {
      expose({ focus() {}, closeEmoji() {} })
      return () => vue.h('rich-input', {
        ...props,
        onText: (value) => emit('update:modelValue', value),
        onImages: (value) => emit('update:images', value),
        onSubmit: () => emit('submit')
      })
    }
  }
  const leaf = (name, props = []) => ({ props, setup: (values) => () => vue.h(name, { ...values }) })
  const cache = new Map()
  function load(name) {
    if (cache.has(name)) return cache.get(name)
    const compiled = compileScript(descriptors.get(name), { id: name, inlineTemplate: true })
    const { code } = transformSync(compiled.content, { format: 'cjs', loader: 'js' })
    const module = { exports: {} }
    const boundary = (source) => {
      if (source === 'vue') return vue
      if (source === 'vue-router') return vueRouter
      if (source === 'element-plus') return { ElMessage: { info() {} } }
      if (source.endsWith('/api/messages')) return api
      if (source.endsWith('/api/user')) return { fetchMe: async () => auth.user }
      if (source.endsWith('/stores/message')) return { useMessageStore: () => message }
      if (source.endsWith('/stores/auth')) return { useAuthStore: () => auth }
      if (source.endsWith('/stores/notification')) return { useNotificationStore: () => notification }
      if (source.endsWith('/ChatView.vue')) return load('ChatView')
      if (source.endsWith('/AppSideNav.vue')) return leaf('side-nav', ['active', 'user', 'unreadCount', 'friendsUnread'])
      if (source.endsWith('/BottomNav.vue')) return leaf('bottom-nav', ['active'])
      if (source.endsWith('/RichInput.vue')) return richInput
      if (source.endsWith('/ImageViewer.vue')) return leaf('image-viewer', ['images', 'index'])
      if (source.endsWith('/UnreadBadge.vue')) return leaf('unread-badge', ['count', 'variant'])
      return require(source)
    }
    new Function('require', 'module', 'exports', code)(boundary, module, module.exports)
    cache.set(name, module.exports.default)
    return module.exports.default
  }
  const root = { children: [] }
  const component = load(route.startsWith('/chat/') ? 'ChatView' : 'MessagesView')
  const app = renderer.createApp(component, chatProps)
  app.use(router)
  app.mount(root)
  await settle()
  return {
    app, root, calls, listeners, events, router, message,
    incoming(payload) { for (const handler of listeners) handler(payload) },
    async resize(value) {
      window.innerWidth = value
      for (const handler of events.get('resize') || []) handler()
      await settle()
    }
  }
}

test('desktop inbox uses shared navigation and leaves conversations unread until explicitly selected', async () => {
  const context = await harness()
  try {
    assert.equal(findAll(context.root, (node) => node.type === 'side-nav')[0]?.props.active, 'message')
    assert.equal(findAll(context.root, (node) => node.type === 'side-nav')[0]?.props.unreadCount, 8)
    assert.equal(byClass(context.root, 'mv-chat-empty').length, 1)
    assert.equal(byClass(context.root, 'mv-item').length, 2)
    assert.deepEqual(context.calls.chat, [])
    assert.deepEqual(context.calls.read, [])
    const tiles = byClass(context.root, 'mv-tile')
    tiles[1].props.onClick()
    await settle()
    assert.equal(context.router.currentRoute.value.fullPath, '/notifications?category=comment')
  } finally { context.app.unmount() }
})

test('desktop selection remounts real chat, clears only acknowledged unread, and cleans subscriptions', async () => {
  const context = await harness()
  try {
    byClass(context.root, 'mv-item')[0].props.onClick()
    await settle()
    assert.equal(context.router.currentRoute.value.path, '/messages')
    assert.equal(context.calls.chat.length, 1)
    assert.equal(context.calls.chat[0].peerId, 2)
    assert.deepEqual(context.calls.read, [2])
    assert.equal(byClass(context.root, 'mv-badge').length, 1)
    assert.equal(context.listeners.size, 2)
    let input = findAll(context.root, (node) => node.type === 'rich-input')[0]
    input.props.onText('甲的草稿')
    await settle()
    byClass(context.root, 'mv-item')[1].props.onClick()
    await settle()
    input = findAll(context.root, (node) => node.type === 'rich-input')[0]
    assert.equal(input.props.modelValue, '')
    assert.equal(context.calls.chat.at(-1).peerId, 3)
    assert.equal(context.listeners.size, 2)
    context.incoming({ id: 301, senderId: 3, receiverId: 1, type: 'TEXT', content: '当前会话新消息' })
    await settle()
    assert.equal(byClass(context.root, 'mv-badge').length, 0)
    assert.equal(byClass(context.root, 'chat-bubble').at(-1).text, '当前会话新消息')
    context.incoming({ id: 201, senderId: 2, receiverId: 1, type: 'TEXT', content: '另一会话新消息' })
    await settle()
    assert.equal(byClass(context.root, 'mv-badge').length, 1)
    assert.equal(byClass(context.root, 'chat-bubble').length, 2)
    await context.resize(390)
    assert.equal(context.listeners.size, 1)
  } finally { context.app.unmount() }
  assert.equal(context.listeners.size, 0)
  assert.equal(context.events.get('resize')?.size || 0, 0)
})

test('failed read acknowledgements keep the conversation unread', async () => {
  const context = await harness({ readResponse: async () => { throw new Error('offline') } })
  try {
    byClass(context.root, 'mv-item')[0].props.onClick()
    await settle()
    assert.deepEqual(context.calls.read, [2])
    assert.equal(byClass(context.root, 'mv-badge').length, 2)
    assert.equal(context.message.unread, 5)
  } finally { context.app.unmount() }
})

test('mobile inbox uses the message bottom tab and opens the existing standalone chat route', async () => {
  const context = await harness({ width: 390 })
  try {
    assert.equal(findAll(context.root, (node) => node.type === 'bottom-nav')[0]?.props.active, 'message')
    assert.equal(findAll(context.root, (node) => node.type === 'side-nav').length, 0)
    byClass(context.root, 'mv-item')[0].props.onClick()
    await settle()
    assert.equal(context.router.currentRoute.value.path, '/chat/2')
    assert.deepEqual(context.calls.chat, [])
  } finally { context.app.unmount() }
})

test('conversation pagination is driven by the list scroll container', async () => {
  const context = await harness({ conversationResponse: (cursor) => cursor
    ? { items: [{ id: 30, peer: { id: 3 } }], hasMore: false }
    : { items: [{ id: 20, peer: { id: 2 } }], nextCursor: 'older', hasMore: true }
  })
  try {
    const list = byClass(context.root, 'mv-list')[0]
    assert.equal(typeof list.props.onScrollPassive, 'function')
    list.scrollTop = 700
    list.props.onScrollPassive()
    await settle()
    assert.deepEqual(context.calls.conversations.map((call) => call.cursor), ['', 'older'])
    assert.equal(byClass(context.root, 'mv-item').length, 2)
  } finally { context.app.unmount() }
})

test('standalone chat preserves route identity, rich media, permission hints, pagination, and sending', async () => {
  const context = await harness({ width: 390, route: '/chat/7', chatResponse: (peerId, cursor, limit) => ({
    peer: { id: peerId, nickname: '路由用户' }, canSend: true, hint: '真实权限提示',
    items: cursor ? [{ id: 700, senderId: peerId, type: 'TEXT', content: '更早消息' }] : limit === 1 ? [] : [
      { id: 703, senderId: peerId, type: 'POST_CARD', post: { id: 81, available: true, title: '分享作品', coverUrl: '/cover.jpg' } },
      { id: 702, senderId: peerId, type: 'IMAGE', images: ['/photo.jpg'] },
      { id: 701, senderId: peerId, type: 'TEXT', content: '文字消息' }
    ], nextCursor: cursor ? null : 'history', hasMore: !cursor
  }) })
  try {
    assert.equal(context.calls.chat[0].peerId, 7)
    assert.equal(byClass(context.root, 'chat-back').length, 1)
    assert.equal(byClass(context.root, 'chat-hint')[0].text, '真实权限提示')
    assert.equal(byClass(context.root, 'chat-img').length, 1)
    byClass(context.root, 'chat-img')[0].props.onClick()
    await settle()
    assert.deepEqual(findAll(context.root, (node) => node.type === 'image-viewer')[0].props.images, ['/photo.jpg'])
    const list = byClass(context.root, 'chat-list')[0]
    list.scrollTop = 0
    list.props.onScrollPassive()
    await settle()
    assert.equal(context.calls.chat.at(-1).cursor, 'history')
    const input = findAll(context.root, (node) => node.type === 'rich-input')[0]
    assert.equal(input.props.dark, false)
    input.props.onText('你好 ☀️')
    await settle()
    input.props.onSubmit()
    await settle()
    assert.deepEqual(context.calls.sent, [{ peerId: 7, payload: { type: 'TEXT', content: '你好 ☀️' } }])
    byClass(context.root, 'chat-card')[0].props.onClick()
    await settle()
    assert.equal(context.router.currentRoute.value.path, '/post/81')
  } finally { context.app.unmount() }
  assert.equal(context.listeners.size, 0)
})

test('leaving a loading embedded chat does not mark it read or leak a late subscription', async () => {
  let finishChat
  const pending = new Promise((resolve) => { finishChat = resolve })
  const context = await harness({ chatResponse: () => pending })
  byClass(context.root, 'mv-item')[0].props.onClick()
  await settle()
  assert.equal(context.calls.chat.length, 1)
  context.app.unmount()
  finishChat({ peer: { id: 2 }, items: [], hasMore: false })
  await settle()
  assert.deepEqual(context.calls.read, [])
  assert.equal(context.listeners.size, 0)
  assert.equal(context.events.get('resize')?.size || 0, 0)
})

test('both views compile their scoped styles and use warm journal tokens on both screen sizes', () => {
  for (const [name, descriptor] of descriptors) {
    const source = descriptor.styles.map((style) => style.content).join('\n')
    const result = compileStyle({ id: name, source, scoped: true, filename: viewPath(name) })
    assert.deepEqual(result.errors, [])
    assert.ok(/var\(--sg-bg/.test(source), `${name} must use the shared background token`)
    assert.ok(/var\(--sg-card/.test(source), `${name} must use the shared card token`)
    assert.doesNotMatch(descriptor.template.content, /dark:\s*!isPc|:dark="!isPc"/)
  }
  const styles = descriptors.get('MessagesView').styles[0].content
  assert.match(styles, /padding-left:\s*var\(--sg-sidebar-w/)
  assert.match(styles, /(?:grid-template-columns:\s*300px|width:\s*300px)/)
})
