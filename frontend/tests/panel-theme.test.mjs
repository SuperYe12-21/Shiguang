import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'
import postcss from 'postcss'

const components = new Map(['CommentPanel', 'RichInput', 'PostMorePanel', 'SharePanel', 'EmojiPanel'].map((name) => {
  const filename = new URL(`../src/components/${name}.vue`, import.meta.url)
  const { descriptor, errors } = parse(readFileSync(filename, 'utf8'))
  return [name, { filename, descriptor, errors, stylesheet: postcss.parse(descriptor.styles.map((style) => style.content).join('\n')) }]
}))

function declaration(name, selector, property) {
  let value
  components.get(name).stylesheet.walkRules((rule) => {
    if (rule.selectors.includes(selector)) {
      rule.walkDecls(property, (entry) => { value = entry.value })
    }
  })
  return value
}

test('desktop and mobile comments share paper surfaces and reserve the shared desktop width', () => {
  assert.equal(declaration('CommentPanel', '.cp-panel', 'background'), 'var(--sg-card, #faf8f2)')
  assert.equal(declaration('CommentPanel', '.cp-panel', 'color'), 'var(--sg-text, #3b4239)')
  assert.equal(declaration('CommentPanel', '.cp-panel-pc', 'width'), 'var(--sg-comment-w)')
  assert.equal(declaration('CommentPanel', '.cp-panel-mobile', 'height'), '52vh')
  for (const selector of ['.cp-panel-pc', '.cp-panel-mobile']) {
    assert.equal(declaration('CommentPanel', selector, 'background'), undefined)
    assert.equal(declaration('CommentPanel', selector, 'color'), undefined)
  }
  assert.match(components.get('CommentPanel').descriptor.template.content, /:dark="false"/)
})

test('panel masks do not dim the video', () => {
  for (const [name, selectors] of [
    ['CommentPanel', ['.cp-mask', '.cp-mask-pc']],
    ['PostMorePanel', ['.pm-mask']],
    ['SharePanel', ['.sp-mask']]
  ]) {
    for (const selector of selectors) {
      assert.equal(declaration(name, selector, 'background'), 'transparent', `${name} ${selector}`)
    }
  }
})

test('paper comment text, actions and dividers have no dark-panel overrides', () => {
  const stylesheet = components.get('CommentPanel').stylesheet
  stylesheet.walkDecls((entry) => {
    assert.doesNotMatch(entry.value, /rgba\(255,\s*255,\s*255|#fff\b|#ff(?:4757|8a8a)|#e04f5f/i)
  })
  assert.equal(declaration('CommentPanel', '.cp-content', 'color'), 'var(--sg-text, #3b4239)')
  assert.equal(declaration('CommentPanel', '.cp-like.is-liked', 'color'), 'var(--sg-primary, #b66c4d)')
  assert.equal(declaration('CommentPanel', '.cp-badge', 'background'), 'var(--sg-primary-soft, #eee4d8)')
})

test('settings and sharing use paper surfaces with readable secondary text and terracotta actions', () => {
  for (const [name, prefix] of [['PostMorePanel', 'pm'], ['SharePanel', 'sp']]) {
    assert.equal(declaration(name, `.${prefix}-panel`, 'background'), 'var(--sg-card, #faf8f2)')
    assert.equal(declaration(name, `.${prefix}-panel`, 'color'), 'var(--sg-text, #3b4239)')
    assert.equal(declaration(name, `.${prefix}-input`, 'color'), 'var(--sg-text, #3b4239)')
    assert.equal(declaration(name, `.${prefix}-btn.ghost`, 'color'), 'var(--sg-text, #3b4239)')
  }
  assert.equal(declaration('PostMorePanel', '.pm-sub', 'color'), 'var(--sg-text-2, #777c70)')
  assert.equal(declaration('SharePanel', '.sp-name', 'color'), 'var(--sg-text, #3b4239)')
  assert.equal(declaration('SharePanel', '.sp-hint', 'color'), 'var(--sg-text-2, #777c70)')
  assert.equal(declaration('PostMorePanel', '.pm-btn.primary', 'background'), 'var(--sg-primary, #b66c4d)')
  assert.equal(declaration('SharePanel', '.sp-btn', 'background'), 'var(--sg-primary, #b66c4d)')
})

test('composer and emoji picker use shared warm tokens while explicit dark inputs stay legible', () => {
  assert.equal(declaration('RichInput', '.ri', 'background'), 'var(--sg-card, #faf8f2)')
  assert.equal(declaration('RichInput', '.ri', 'color'), 'var(--sg-text, #3b4239)')
  assert.equal(declaration('RichInput', '.ri-emoji', 'background'), 'var(--sg-card, #faf8f2)')
  assert.equal(declaration('RichInput', '.ri-input::placeholder', 'color'), 'var(--sg-text-3, #99998d)')
  assert.equal(declaration('RichInput', '.ri-send', 'background'), 'var(--sg-primary, #b66c4d)')
  assert.equal(declaration('RichInput', '.ri-send:hover:not(:disabled)', 'background'), 'var(--sg-primary-deep, #9a573d)')
  assert.equal(declaration('RichInput', '.ri-dark .ri-input', 'color'), '#fff')
  assert.equal(declaration('EmojiPanel', '.emoji-panel', 'background'), 'var(--sg-card, #faf8f2)')
  assert.equal(declaration('EmojiPanel', '.emoji-title', 'color'), 'var(--sg-text-2, #777c70)')
  assert.equal(declaration('EmojiPanel', '.emoji-btn:hover', 'background'), 'var(--sg-primary-soft, #eee4d8)')
})

test('all auxiliary panels are free of the former bright-red theme', () => {
  for (const [name, { stylesheet }] of components) {
    stylesheet.walkDecls((entry) => {
      assert.doesNotMatch(entry.value, /#(?:ff5c5c|ff4747|ff4757|ff8a8a|ff5b5b|ff4d4f|ffa06b|e04f5f)|rgba\(255,\s*92,\s*92/i, `${name}: ${entry.toString()}`)
    })
  }
})

test('all five component scripts and templates compile without a running server', () => {
  for (const [name, { filename, descriptor, errors }] of components) {
    assert.deepEqual(errors, [], name)
    const script = compileScript(descriptor, { id: `panel-theme-${name}` })
    const result = compileTemplate({
      source: descriptor.template.content, filename: filename.pathname, id: `panel-theme-${name}`,
      compilerOptions: { bindingMetadata: script.bindings }
    })
    assert.deepEqual(result.errors, [], name)
  }
})
