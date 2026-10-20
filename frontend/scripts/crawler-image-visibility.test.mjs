import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import ts from 'typescript'

const source = readFileSync(new URL('../src/composables/useCrawlerImageVisibility.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } })

function setup() {
  const storage = new Map([['token', 'test-token']])
  const session = new Map([['user', JSON.stringify({ id: 1 })]])
  const mounted = []
  const unmounted = []
  const window = new EventTarget()
  const context = {
    exports: {}, window, Event,
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) },
    sessionStorage: { getItem: key => session.get(key) ?? null, setItem: (key, value) => session.set(key, value) },
    require: name => name !== 'vue' ? { userApi: {
      me: async () => ({ data: JSON.parse(session.get('user')) }),
      updateCrawlerDisplay: async value => ({ data: { ...JSON.parse(session.get('user')), crawlerShowImages: value } }),
    } } : ({ ref: value => ({ value }), onMounted: fn => mounted.push(fn), onUnmounted: fn => unmounted.push(fn) }),
  }
  vm.runInNewContext(outputText, context)
  return { ...context, session, mounted, unmounted }
}

test('visibility persists per account and updates mounted crawler views immediately', async () => {
  const app = setup()
  const settings = app.exports.useCrawlerImageVisibility()
  const page = app.exports.useCrawlerImageVisibility()
  app.mounted.forEach(fn => fn())
  assert.equal(page.showCrawlerImages.value, true)
  await settings.setShowCrawlerImages(false)
  assert.equal(page.showCrawlerImages.value, false)
  assert.equal(app.exports.useCrawlerImageVisibility().showCrawlerImages.value, false)
  app.session.set('user', JSON.stringify({ id: 2 }))
  app.window.dispatchEvent(new Event('user-profile-updated'))
  assert.equal(page.showCrawlerImages.value, true)
  app.session.set('user', JSON.stringify({ id: 1, crawlerShowImages: false }))
  app.window.dispatchEvent(new Event('storage'))
  assert.equal(page.showCrawlerImages.value, false)
  await settings.setShowCrawlerImages(true)
  assert.equal(page.showCrawlerImages.value, true)
  app.unmounted.forEach(fn => fn())
})

test('missing or malformed identity never saves a shared preference', async () => {
  const app = setup()
  for (const user of ['null', '{invalid']) {
    app.session.set('user', user)
    const settings = app.exports.useCrawlerImageVisibility()
    assert.equal(settings.showCrawlerImages.value, true)
    await assert.rejects(settings.setShowCrawlerImages(false), /请先登录/)
  }
})

test('save response cannot overwrite a newly logged-in account', async () => {
  const app = setup()
  const settings = app.exports.useCrawlerImageVisibility()
  const pending = settings.setShowCrawlerImages(false)
  app.session.set('user', JSON.stringify({ id: 2, crawlerShowImages: true }))
  await pending
  assert.equal(JSON.parse(app.session.get('user')).id, 2)
  assert.equal(JSON.parse(app.session.get('user')).crawlerShowImages, true)
})

test('failed session update preserves displayed account value', async () => {
  const app = setup()
  app.sessionStorage.setItem = () => { throw new Error('storage unavailable') }
  const settings = app.exports.useCrawlerImageVisibility()
  await assert.rejects(settings.setShowCrawlerImages(false), /storage unavailable/)
  assert.equal(settings.showCrawlerImages.value, true)
})
