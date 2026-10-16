import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { runInNewContext } from 'node:vm'
import { after, test } from 'node:test'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createMemoryHistory, createRouter } from 'vue-router'

let server
after(async () => { await server?.close() })

test('warm journal uses the approved paper and terracotta palette', async () => {
  const tokens = await readFile(new URL('../src/styles/tokens.css', import.meta.url), 'utf8')
  for (const [name, color] of Object.entries({
    'sg-bg': '#f4f1e9', 'sg-card': '#faf8f2', 'sg-text': '#3b4239', 'sg-primary': '#b66c4d'
  })) {
    assert.match(tokens, new RegExp(`--${name}:\\s*${color}`, 'i'))
  }
})

test('shared navigation preserves destinations, active page and real unread count', async () => {
  server = await createServer({ configFile: false, root: new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'), plugins: [vue()], server: { middlewareMode: true } })
  const { default: AppSideNav } = await server.ssrLoadModule('/src/components/AppSideNav.vue')
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }] })
  await router.push('/messages')
  await router.isReady()
  const app = createSSRApp(AppSideNav, { active: 'message', user: { nickname: '测试昵称' }, unreadCount: 7 })
  app.use(router)
  const html = await renderToString(app)
  for (const text of ['首页', '朋友', '消息', '我的', '发布作品', '测试昵称']) assert.ok(html.includes(text), text)
  assert.match(html, /aria-current="page"/)
  assert.match(html, />7</)
})

test('desktop feed uses a container-relative slide offset after reserving the header', async () => {
  const view = await readFile(new URL('../src/views/FeedView.vue', import.meta.url), 'utf8')
  assert.match(view, /currentIndex \* 100}\%/)
  assert.match(view, /<AppSideNav/)
})

test('mobile feed reserves a paper header and bottom navigation outside the media stage', async () => {
  const view = await readFile(new URL('../src/views/FeedView.vue', import.meta.url), 'utf8')
  const item = await readFile(new URL('../src/components/mobile/FeedItem.vue', import.meta.url), 'utf8')
  assert.ok(view.includes('class="m-header"'), 'mobile header is rendered')
  assert.match(item, /section\.feed-item\.item-compact \.feed-image\s*\{\s*height: 48%/)
})

test('crossing the responsive breakpoint preserves the current work index', async () => {
  const view = await readFile(new URL('../src/views/FeedView.vue', import.meta.url), 'utf8')
  const start = view.indexOf('resizeHandler = () => {')
  const end = view.indexOf("window.addEventListener('resize', resizeHandler)", start)
  const isPc = { value: true }
  const window = { innerWidth: 390 }
  const restored = []
  const handler = runInNewContext(`let resizeHandler; ${view.slice(start, end)}; resizeHandler`, {
    window, isPc, currentIndex: { value: 4 }, nextTick: (callback) => callback(),
    scrollToIndex: (index) => restored.push(index), scrollEl: { value: null }, feed: { posts: Array(8) }
  })
  handler()
  assert.equal(isPc.value, false)
  window.innerWidth = 1280
  handler()
  assert.equal(isPc.value, true)
  assert.deepEqual(restored, [4, 4])
})
