import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { compile } from '@vue/compiler-dom'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'
import { renderToString } from '@vue/server-renderer'
import postcss from 'postcss'
import * as Vue from 'vue'

const filename = new URL('../src/views/ProfileView.vue', import.meta.url)
const source = readFileSync(filename, 'utf8')
const { descriptor, errors } = parse(source)
const stylesheet = postcss.parse(descriptor.styles.map((style) => style.content).join('\n'))
const { code } = compile(descriptor.template.content, { mode: 'function', prefixIdentifiers: true })
const render = new Function('Vue', code)(Vue)
const post = { id: 81, title: '周末的河岸', createdAt: '2026-10-12', type: 'IMAGE', images: ['river.jpg'], likeCount: 27 }

async function profileHtml(overrides = {}) {
  const context = {
    isPc: true, isMe: true, isAdmin: false, auth: { user: { id: 17 } }, profile: {
      id: 17, nickname: '真实昵称', bio: '慢慢记录生活', createdAt: '2025-03-01',
      postCount: 6, likeCount: 128, followingCount: 32, followerCount: 49
    },
    displayName: '真实昵称', remark: '', fallbackAvatar: 'avatar.svg',
    canSeeLike: true, canSeeFavorite: true, canSeeFollowing: true, canSeeFollower: true,
    activeTab: 'posts', gridItems: [post], posts: [post], likes: [], favorites: [],
    loading: false, loadingMore: false, likesLoading: false, favoritesLoading: false,
    hasMore: false, likesHasMore: false, favoritesHasMore: false,
    editOpen: false, remarkOpen: false, drawerOpen: false, shareFallbackOpen: false,
    avatarViewOpen: false, unreadTotal: 0, followText: '+ 关注',
    coverOf: (item) => item.images[0], formatDate: (date) => date,
    formatCount: (count) => String(count ?? 0), router: { push() {} },
    ...overrides
  }
  for (const [, handler] of descriptor.scriptSetup.content.matchAll(/(?:async )?function (\w+)\(/g)) {
    if (!(handler in context)) context[handler] = () => {}
  }
  const app = Vue.createSSRApp({ render, setup: () => context })
  app.component('AppSideNav', {
    props: ['active', 'user', 'unreadCount'],
    template: '<aside class="test-side-nav" :data-active="active" :data-user="user?.id" :data-unread="unreadCount" />'
  })
  app.component('BottomNav', { template: '<nav class="test-bottom-nav" />' })
  app.component('UnreadBadge', { template: '<span />' })
  return (await renderToString(app)).replace(/<!--[\s\S]*?-->/g, '')
}

function declaration(selector, property) {
  let value
  stylesheet.walkRules((rule) => {
    if (rule.selectors.includes(selector)) {
      rule.walkDecls(property, (entry) => { value = entry.value })
    }
  })
  return value
}

test('desktop uses shared navigation with self and visitor active states', async () => {
  assert.match(await profileHtml(), /class="test-side-nav" data-active="me"/)
  assert.match(await profileHtml({ isMe: false }), /class="test-side-nav" data-active(?:="")? data-user="17"/)
  assert.match(await profileHtml({ unreadTotal: 7 }), /data-user="17" data-unread="7"/)
  assert.match(source, /import AppSideNav from ['"]\.\.\/components\/AppSideNav\.vue['"]/)
  assert.doesNotMatch(descriptor.template.content, /class="pc-side"/)
  assert.equal(declaration('.pc-main', 'margin-left'), 'var(--sg-sidebar-w, 188px)')
})

test('desktop journal places real titles and dates below landscape covers', async () => {
  const html = await profileHtml()
  assert.match(html, /class="pc-cell-cover"/)
  assert.match(html, /class="pc-cell-title">周末的河岸<\/p>/)
  assert.match(html, /class="pc-cell-date"[^>]*>2026-10-12<\/time>/)
  assert.equal(declaration('.pc-grid', 'grid-template-columns'), 'repeat(3, minmax(0, 1fr))')
  assert.equal(declaration('.pc-cell-cover', 'aspect-ratio'), '4 / 3')
  assert.equal(declaration('.pc-banner', 'height'), '100px')
  assert.equal(declaration('.pc-avatar', 'border-radius'), '25px')
  assert.equal(declaration('.pc-tabs', 'width'), '100%')
})

test('mobile journal has paper panels and three portrait cover columns', async () => {
  const html = await profileHtml({ isPc: false })
  assert.match(html, /真实昵称/)
  assert.match(html, /慢慢记录生活/)
  assert.match(html, /拾光号 17/)
  assert.match(html, /test-bottom-nav/)
  assert.equal(declaration('.pf-grid', 'grid-template-columns'), 'repeat(3, minmax(0, 1fr))')
  assert.equal(declaration('.pf-cell', 'aspect-ratio'), '3 / 4')
  assert.equal(declaration('.pf-cell', 'border-radius'), '6px')
  assert.equal(declaration('.profile-page', 'background'), 'var(--sg-bg, #f4f1e9)')
  assert.equal(declaration('.pf-modal-panel', 'background'), 'var(--sg-card, #faf8f2)')
  assert.equal(declaration('.pf-drawer', 'background'), 'var(--sg-card, #faf8f2)')
})

test('author, administrator, privacy and visitor controls remain available', async () => {
  const author = await profileHtml({ isAdmin: true, drawerOpen: true })
  for (const label of ['真实昵称', '慢慢记录生活', '作品', '获赞', '关注', '粉丝', '编辑资料', '管理后台', '账号设置', '观看历史', '分享主页']) {
    assert.ok(author.includes(label), label)
  }
  for (const isPc of [true, false]) {
    const visitor = await profileHtml({ isPc, isMe: false, canSeeLike: false, canSeeFavorite: false })
    assert.ok(visitor.includes('私信'))
    assert.ok(visitor.includes('+ 关注'))
    assert.ok(!visitor.includes('>点赞<'))
    assert.ok(!visitor.includes('>收藏<'))
    assert.ok(!visitor.includes('编辑资料'))
  }
})

test('profile script and template compile together without a running server', () => {
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'profile-theme' })
  const result = compileTemplate({
    source: descriptor.template.content, filename: filename.pathname, id: 'profile-theme',
    compilerOptions: { bindingMetadata: script.bindings }
  })
  assert.deepEqual(result.errors, [])
})

test('desktop header matches the preview hierarchy instead of the legacy action row', async () => {
  const html = await profileHtml({ isAdmin: true })
  const head = html.match(/<header class="pc-head">([\s\S]*?)<\/header>/)?.[1] || ''
  assert.ok(!head.includes('pc-stats'), 'statistics belong below the identity/action row')
  assert.equal((head.match(/<button/g) || []).length, 2, 'only edit and more belong in the desktop header')
  assert.ok(!head.includes('加入拾光'), 'join date must not stretch the preview header')
  assert.match(html, /class="pc-profile-top"/)
  assert.equal(declaration('.pc-head', 'display'), 'flex')
  assert.equal(declaration('.pc-stats', 'margin'), '20px 0 25px 124px')
})

test('mobile keeps its original top-right more entry and captions below covers', async () => {
  const html = await profileHtml({ isPc: false })
  const head = html.match(/class="pf-head-row">([\s\S]*?)<div class="pf-stats"/)?.[1] || ''
  assert.ok(head.includes('pf-bio'), 'bio belongs in the identity column')
  assert.equal((head.match(/<button/g) || []).length, 1, 'only edit belongs next to the identity')
  for (const isMe of [true, false]) {
    const mobile = await profileHtml({ isPc: false, isMe })
    const top = mobile.match(/<header class="pf-top">([\s\S]*?)<\/header>/)?.[1] || ''
    assert.match(top, /class="pf-more-btn" aria-label="更多"/)
    assert.equal((top.match(/<circle/g) || []).length, 3, 'more uses its original three-dot icon')
    assert.ok(!top.includes('profile-search'))
    assert.ok(!mobile.includes('更多 ···'))
  }
  assert.match(html, /class="pf-cell-title">周末的河岸<\/p>/)
  assert.equal(declaration('.pf-avatar', 'border-radius'), '25px')
  assert.equal(declaration('.pf-head-side', 'padding-top'), '27px')
  assert.equal(declaration('.pf-stats', 'margin'), '33px 0 23px')
})

test('more drawer retains all existing account tools on either device', async () => {
  for (const isPc of [true, false]) {
    const html = await profileHtml({ isPc, isAdmin: true, drawerOpen: true })
    for (const label of ['账号设置', '观看历史', '分享主页', '管理后台', '退出登录']) assert.ok(html.includes(label), label)
  }
})

test('mobile visitor reserves space for follow and chat without spilling a long nickname', async () => {
  const html = await profileHtml({ isPc: false, isMe: false, displayName: '这是一个比较长的用户昵称', profile: { id: 18, bio: '这是访客的个人签名' } })
  assert.match(html, /pf-head-visitor/)
  assert.match(html, /class="pf-name" title="这是一个比较长的用户昵称"/)
  assert.equal(declaration('.pf-name', 'text-overflow'), 'ellipsis')
  assert.equal(declaration('.pf-bio::before', 'float'), 'right')
  assert.equal(declaration('.pf-bio::before', 'height'), '20px')
  assert.equal(declaration('.pf-bio::before', 'width'), '80px')
  assert.equal(declaration('.pf-head-visitor .pf-bio::before', 'width'), '128px')
})

test('empty profiles use different guidance for the owner and visitors on either device', async () => {
  for (const isPc of [true, false]) {
    const emptyProfile = { isPc, gridItems: [], posts: [] }
    assert.ok((await profileHtml(emptyProfile)).includes('还没有作品，去发布第一条拾光吧'))
    const visitor = await profileHtml({ ...emptyProfile, isMe: false })
    assert.ok(!visitor.includes('还没有作品，去发布第一条拾光吧'))
    assert.ok(visitor.includes('该用户还没有发布作品'))
  }
})

test('publishing empty-state guidance stays hidden while posts are loading', async () => {
  for (const isPc of [true, false]) {
    for (const pending of [{ loading: true }, { loadingMore: true }]) {
      const html = await profileHtml({ isPc, gridItems: [], posts: [], ...pending })
      assert.ok(!html.includes('还没有作品，去发布第一条拾光吧'))
    }
  }
})
