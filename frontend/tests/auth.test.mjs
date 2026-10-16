import assert from 'node:assert/strict'
import { after, before, beforeEach, test } from 'node:test'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { createRenderer, nextTick, ssrContextKey } from 'vue'
import { ElMessage } from 'element-plus'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

const require = createRequire(import.meta.url)
const root = fileURLToPath(new URL('..', import.meta.url))
const routerModule = require.resolve('vue-router/dist/vue-router.mjs').replaceAll('\\', '/')
const storage = new Map()
const notifications = []
const hardNavigations = []
let transport
let server
let http
let auth
let router
let useAuthStore
let pinia

globalThis.localStorage = {
  getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: (key) => storage.delete(key)
}
globalThis.window = {
  history: { state: null },
  addEventListener() {}
}
globalThis.location = {
  pathname: '/feed',
  protocol: 'http:',
  host: 'auth-test.invalid',
  set href(value) { hardNavigations.push(value) }
}
ElMessage.error = (message) => notifications.push(message)
ElMessage.success = () => {}
axios.defaults.adapter = (config) => transport(config)

function reply(config, data, status = 200) {
  const response = { config, data, status, statusText: String(status), headers: {} }
  if (status >= 400) {
    throw new axios.AxiosError(data.message, 'ERR_BAD_REQUEST', config, null, response)
  }
  return response
}

function ok(config, data) {
  return reply(config, { code: 0, message: 'ok', data })
}

function unauthorized(config) {
  return reply(config, { code: 401, message: '未登录或登录已过期', data: null }, 401)
}

function deferred() {
  let resolve
  const promise = new Promise((complete) => { resolve = complete })
  return { promise, resolve }
}

const initialSession = {
  accessToken: 'expired-access',
  refreshToken: 'initial-refresh',
  user: { id: 17, nickname: '拾光' }
}
const rotatedSession = {
  accessToken: 'new-access',
  refreshToken: 'rotated-refresh',
  user: { id: 17, nickname: '更新后的昵称' }
}

before(async () => {
  server = await createServer({
    configFile: false,
    root,
    server: { middlewareMode: true },
    ssr: { noExternal: ['vue-router'] },
    plugins: [
      {
        name: 'auth-test-boundaries',
        enforce: 'pre',
        resolveId(source) {
          if (source === 'vue-router') return '\0auth-test-router'
        },
        load(id) {
          if (id === '\0auth-test-router') {
            return `export * from '${routerModule}'; export { createMemoryHistory as createWebHistory } from '${routerModule}'`
          }
          if (id.endsWith('.vue') && !id.endsWith('/LoginView.vue') && !id.endsWith('/App.vue')) {
            return '<template><div /></template>'
          }
        }
      },
      vue()
    ]
  })
  ;({ default: http } = await server.ssrLoadModule('/src/api/http.js'))
  ;({ useAuthStore } = await server.ssrLoadModule('/src/stores/auth.js'))
  ;({ default: router } = await server.ssrLoadModule('/src/router/index.js'))
})

after(async () => { await server?.close() })

beforeEach(async () => {
  storage.clear()
  storage.set('sg_token', initialSession.accessToken)
  storage.set('sg_refresh_token', initialSession.refreshToken)
  storage.set('sg_user', JSON.stringify(initialSession.user))
  pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore()
  transport = async (config) => { throw new Error(`Unexpected request: ${config.method} ${config.url}`) }
  await router.replace('/feed')
  notifications.length = 0
  hardNavigations.length = 0
})

test('login persists both tokens and logout clears persisted and reactive auth', async () => {
  transport = async (config) => ok(config, rotatedSession)
  await auth.login('13800000000', '123456')
  assert.equal(storage.get('sg_refresh_token'), rotatedSession.refreshToken)
  assert.equal(auth.refreshToken, rotatedSession.refreshToken)
  auth.logout()
  assert.equal(auth.token, '')
  assert.equal(auth.refreshToken, '')
  assert.equal(auth.user, null)
  assert.equal(storage.has('sg_token'), false)
  assert.equal(storage.has('sg_refresh_token'), false)
  assert.equal(storage.has('sg_user'), false)
})

test('concurrent 401s share refresh, rotate state, and retry intact without navigation', async () => {
  await router.replace('/feed?postId=81#comments')
  const refreshStarted = deferred()
  const finishRefresh = deferred()
  const requests = []
  let refreshCount = 0
  transport = async (config) => {
    if (config.url === '/auth/refresh') {
      refreshCount += 1
      assert.equal(config.headers.Authorization, undefined)
      assert.deepEqual(JSON.parse(config.data), { refreshToken: initialSession.refreshToken })
      refreshStarted.resolve()
      await finishRefresh.promise
      return ok(config, rotatedSession)
    }
    requests.push({ url: config.url, data: config.data, method: config.method, authorization: config.headers.Authorization })
    if (config.headers.Authorization === `Bearer ${initialSession.accessToken}`) return unauthorized(config)
    return ok(config, { saved: true })
  }
  const pending = Promise.all([http.post('/comments', { content: '未发送的输入' }), http.get('/user/me')])
  pending.catch(() => {})
  await Promise.race([refreshStarted.promise, pending])
  finishRefresh.resolve()
  assert.deepEqual(await pending, [{ saved: true }, { saved: true }])
  assert.equal(refreshCount, 1)
  assert.equal(requests.length, 4)
  assert.deepEqual(requests[2], {
    url: '/comments', data: JSON.stringify({ content: '未发送的输入' }), method: 'post', authorization: 'Bearer new-access'
  })
  assert.equal(auth.token, rotatedSession.accessToken)
  assert.equal(auth.refreshToken, rotatedSession.refreshToken)
  assert.deepEqual(auth.user, rotatedSession.user)
  assert.equal(storage.get('sg_refresh_token'), rotatedSession.refreshToken)
  assert.equal(router.currentRoute.value.fullPath, '/feed?postId=81#comments')
  assert.deepEqual(hardNavigations, [])
})

test('invalid refresh clears the whole session and uses SPA login with original path', async () => {
  await router.replace('/friends/feed?postId=81#comments')
  let refreshCount = 0
  let leaveCount = 0
  const removeGuard = router.beforeEach((to, from) => {
    if (from.path === '/friends/feed' && to.path === '/login') leaveCount += 1
  })
  transport = async (config) => {
    if (config.url === '/auth/refresh') refreshCount += 1
    return unauthorized(config)
  }
  try {
    await assert.rejects(http.get('/user/me'))
    assert.equal(refreshCount, 1)
    assert.equal(auth.token, '')
    assert.equal(auth.user, null)
    assert.equal(storage.has('sg_refresh_token'), false)
    assert.equal(storage.has('sg_user'), false)
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(router.currentRoute.value.query.redirect, '/friends/feed?postId=81#comments')
    assert.equal(leaveCount, 1)
    assert.deepEqual(hardNavigations, [])
  } finally {
    removeGuard()
  }
})

test('refresh transport failure keeps the session and current route for a later retry', async () => {
  transport = async (config) => {
    if (config.url === '/auth/refresh') throw new axios.AxiosError('timeout', 'ECONNABORTED', config)
    return unauthorized(config)
  }
  await assert.rejects(http.get('/user/me'), /timeout/)
  assert.equal(auth.token, initialSession.accessToken)
  assert.equal(storage.get('sg_refresh_token'), initialSession.refreshToken)
  assert.equal(router.currentRoute.value.path, '/feed')
  assert.deepEqual(hardNavigations, [])
})

test('a retried request that still returns 401 expires the session without a refresh loop', async () => {
  let refreshCount = 0
  let requestCount = 0
  transport = async (config) => {
    if (config.url === '/auth/refresh') {
      refreshCount += 1
      return ok(config, rotatedSession)
    }
    requestCount += 1
    return unauthorized(config)
  }
  await assert.rejects(http.get('/user/me'))
  assert.equal(refreshCount, 1)
  assert.equal(requestCount, 2)
  assert.equal(auth.token, '')
  assert.equal(router.currentRoute.value.path, '/login')
})

test('a late 401 from the old access token reuses the rotated token', async () => {
  const finishLateRequest = deferred()
  let refreshCount = 0
  transport = async (config) => {
    if (config.url === '/auth/refresh') {
      refreshCount += 1
      return ok(config, rotatedSession)
    }
    if (config.headers.Authorization === `Bearer ${initialSession.accessToken}`) {
      if (config.url === '/late') await finishLateRequest.promise
      return unauthorized(config)
    }
    return ok(config, config.url)
  }
  const late = http.get('/late')
  late.catch(() => {})
  try {
    assert.equal(await http.get('/first'), '/first')
  } finally {
    finishLateRequest.resolve()
  }
  assert.equal(await late, '/late')
  assert.equal(refreshCount, 1)
})

test('a legacy session with no refresh token is fully cleared on 401', async () => {
  storage.delete('sg_refresh_token')
  setActivePinia(createPinia())
  auth = useAuthStore()
  const urls = []
  transport = async (config) => {
    urls.push(config.url)
    return unauthorized(config)
  }
  await assert.rejects(http.get('/user/me'))
  assert.deepEqual(urls, ['/user/me'])
  assert.equal(auth.user, null)
  assert.equal(storage.has('sg_user'), false)
  assert.equal(router.currentRoute.value.query.redirect, '/feed')
})

test('login failures preserve existing session and never invoke refresh', async () => {
  const requests = []
  transport = async (config) => {
    requests.push({ url: config.url, authorization: config.headers.Authorization })
    return unauthorized(config)
  }
  await assert.rejects(auth.login('13800000000', 'invalid'))
  assert.deepEqual(requests, [{ url: '/auth/login', authorization: undefined }])
  assert.equal(auth.token, initialSession.accessToken)
  assert.equal(storage.get('sg_refresh_token'), initialSession.refreshToken)
  assert.equal(router.currentRoute.value.path, '/feed')
})

test('logout during refresh cannot restore credentials or retry the original request', async () => {
  const refreshStarted = deferred()
  const finishRefresh = deferred()
  let requests = 0
  transport = async (config) => {
    if (config.url === '/auth/refresh') {
      refreshStarted.resolve()
      await finishRefresh.promise
      return ok(config, rotatedSession)
    }
    requests += 1
    return unauthorized(config)
  }
  const pending = http.post('/comments', { content: '属于原账号' })
  pending.catch(() => {})
  await Promise.race([refreshStarted.promise, pending])
  auth.logout()
  finishRefresh.resolve()
  await assert.rejects(pending)
  assert.equal(requests, 1)
  assert.equal(auth.token, '')
  assert.equal(storage.has('sg_refresh_token'), false)
})

test('protected navigation retains path, query and hash for login', async () => {
  auth.logout()
  await router.replace('/settings?section=privacy#comments')
  assert.equal(router.currentRoute.value.path, '/login')
  assert.equal(router.currentRoute.value.query.redirect, '/settings?section=privacy#comments')
})

test('authenticated visits to login honor a valid internal redirect', async () => {
  await router.replace({ path: '/login', query: { redirect: '/post/81?from=search#comments' } })
  assert.equal(router.currentRoute.value.fullPath, '/post/81?from=search#comments')
})

for (const redirect of [
  'https://evil.example/path', '//evil.example/path', '/\\evil.example/path',
  '/%2f%2fevil.example', '/%5cevil.example', '/feed\n', '/login?redirect=/login',
  '/LOGIN/', '/feed/../login', '/missing-page', ['//evil.example', '/feed']
]) {
  test(`login rejects unsafe or looping redirect ${JSON.stringify(redirect)}`, async () => {
    await router.replace({ path: '/login', query: { redirect } })
    assert.equal(router.currentRoute.value.fullPath, '/feed')
    assert.deepEqual(hardNavigations, [])
  })
}

for (const status of [400, 403]) {
  test(`refresh rejection ${status} expires the session`, async () => {
    transport = async (config) => config.url === '/auth/refresh'
      ? reply(config, { code: status, message: 'refresh rejected', data: null }, status)
      : unauthorized(config)
    await assert.rejects(http.get('/user/me'))
    assert.equal(auth.token, '')
    assert.equal(auth.refreshToken, '')
    assert.equal(auth.user, null)
    assert.equal(router.currentRoute.value.path, '/login')
  })
}

for (const status of [404, 429, 500, 503]) {
  test(`refresh service failure ${status} preserves auth and can recover later`, async () => {
    let available = false
    let refreshCount = 0
    transport = async (config) => {
      if (config.url === '/auth/refresh') {
        refreshCount += 1
        return available ? ok(config, rotatedSession)
          : reply(config, { code: status, message: 'service unavailable', data: null }, status)
      }
      return config.headers.Authorization === 'Bearer new-access' ? ok(config, 'recovered') : unauthorized(config)
    }
    await assert.rejects(http.get('/user/me'), /service unavailable/)
    assert.equal(auth.token, initialSession.accessToken)
    assert.equal(auth.refreshToken, initialSession.refreshToken)
    assert.deepEqual(auth.user, initialSession.user)
    assert.equal(router.currentRoute.value.path, '/feed')
    available = true
    assert.equal(await http.get('/user/me'), 'recovered')
    assert.equal(refreshCount, 2)
  })
}

test('an invalid-refresh business envelope is terminal without recursion', async () => {
  let refreshCount = 0
  transport = async (config) => {
    if (config.url === '/auth/refresh') {
      refreshCount += 1
      return reply(config, { code: 401, message: 'invalid refresh', data: null })
    }
    return unauthorized(config)
  }
  await assert.rejects(http.get('/user/me'), /invalid refresh/)
  assert.equal(refreshCount, 1)
  assert.equal(auth.token, '')
})

test('a malformed refresh response does not overwrite credentials', async () => {
  transport = async (config) => config.url === '/auth/refresh' ? ok(config, { accessToken: 'partial' }) : unauthorized(config)
  await assert.rejects(http.get('/user/me'))
  assert.equal(auth.token, initialSession.accessToken)
  assert.equal(auth.refreshToken, initialSession.refreshToken)
  assert.deepEqual(auth.user, initialSession.user)
  assert.equal(storage.get('sg_token'), initialSession.accessToken)
  assert.equal(router.currentRoute.value.path, '/feed')
})

test('a late original 401 cannot clear a new login or replay under the new account', async () => {
  const requestStarted = deferred()
  const finishRequest = deferred()
  let requestCount = 0
  transport = async (config) => {
    if (config.url === '/auth/login') return ok(config, rotatedSession)
    requestCount += 1
    requestStarted.resolve()
    await finishRequest.promise
    return unauthorized(config)
  }
  const pending = http.post('/comments', { content: '原账号的输入' })
  pending.catch(() => {})
  await requestStarted.promise
  auth.logout()
  await auth.login('13800000000', '123456')
  finishRequest.resolve()
  await assert.rejects(pending)
  assert.equal(requestCount, 1)
  assert.equal(auth.token, rotatedSession.accessToken)
  assert.equal(storage.get('sg_refresh_token'), rotatedSession.refreshToken)
  assert.equal(router.currentRoute.value.path, '/feed')
})

test('ensureUser completing after logout cannot restore cached user data', async () => {
  const requestStarted = deferred()
  const finishRequest = deferred()
  auth.user = null
  storage.delete('sg_user')
  transport = async (config) => {
    requestStarted.resolve()
    await finishRequest.promise
    return ok(config, initialSession.user)
  }
  const pending = auth.ensureUser()
  await requestStarted.promise
  auth.logout()
  finishRequest.resolve()
  await pending
  assert.equal(auth.user, null)
  assert.equal(storage.has('sg_user'), false)
})

test('guest login normalizes unsafe return locations before preserving them', async () => {
  auth.logout()
  await router.replace({ path: '/login', query: { redirect: '//evil.example' } })
  assert.equal(router.currentRoute.value.query.redirect, '/feed')
})

const renderer = createRenderer({
  createComment: () => ({}),
  insert() {},
  remove() {},
  parentNode: () => null,
  nextSibling: () => null
})

async function mountSetup(path) {
  const { default: component } = await server.ssrLoadModule(path)
  const app = renderer.createApp({ ...component, render: () => null })
  app.provide(ssrContextKey, {})
  app.use(pinia)
  app.use(router)
  const instance = app.mount({})
  return { app, setup: instance.$.setupState }
}

test('login retains phone and code after failure then returns to the original route', async () => {
  auth.logout()
  await router.replace({ path: '/login', query: { redirect: '/search?q=flowers#results' } })
  const { app, setup } = await mountSetup('/src/views/LoginView.vue')
  const requests = []
  let accepted = false
  transport = async (config) => {
    requests.push(config.url)
    return accepted ? ok(config, rotatedSession) : unauthorized(config)
  }
  try {
    setup.phone = '13800000000'
    setup.code = '123456'
    await setup.doLogin()
    assert.equal(setup.phone, '13800000000')
    assert.equal(setup.code, '123456')
    assert.equal(setup.loading, false)
    assert.equal(router.currentRoute.value.path, '/login')
    accepted = true
    await setup.doLogin()
    assert.equal(router.currentRoute.value.fullPath, '/search?q=flowers#results')
    assert.deepEqual(requests, ['/auth/login', '/auth/login'])
    assert.deepEqual(hardNavigations, [])
  } finally {
    app.unmount()
  }
})

test('App restarts WebSocket with the rotated token and closes it when refresh is rejected', async () => {
  const sockets = []
  const originalDocument = globalThis.document
  const originalWebSocket = globalThis.WebSocket
  globalThis.document = { addEventListener() {}, removeEventListener() {} }
  globalThis.WebSocket = class {
    static OPEN = 1
    constructor(url) { this.url = url; sockets.push(this) }
    close() { this.closed = true }
  }
  let invalid = false
  transport = async (config) => {
    if (config.url === '/auth/refresh') return invalid ? unauthorized(config) : ok(config, rotatedSession)
    if (config.url === '/protected') {
      return !invalid && config.headers.Authorization === 'Bearer new-access' ? ok(config, 'ok') : unauthorized(config)
    }
    return ok(config, {})
  }
  let app
  try {
    ;({ app } = await mountSetup('/src/App.vue'))
    assert.equal(sockets.length, 1)
    assert.match(sockets[0].url, /token=expired-access$/)
    await http.get('/protected')
    await nextTick()
    assert.equal(sockets[0].closed, true)
    assert.equal(sockets[0].onclose, null)
    assert.equal(sockets.length, 2)
    assert.match(sockets[1].url, /token=new-access$/)
    invalid = true
    await assert.rejects(http.get('/protected'))
    await nextTick()
    assert.equal(sockets[1].closed, true)
    assert.equal(sockets[1].onclose, null)
    assert.equal(sockets.length, 2)
  } finally {
    app?.unmount()
    globalThis.document = originalDocument
    globalThis.WebSocket = originalWebSocket
  }
})
