import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
const gallery = readFileSync(new URL('../src/views/GalleryView.vue', import.meta.url), 'utf8')
const functions = gallery.slice(gallery.indexOf('let galleryActive = false'), gallery.indexOf('watch(zoomLevel,'))
  + gallery.slice(gallery.indexOf('async function loadMore()'), gallery.indexOf('function openViewer('))
function setup(total = 200, overflowAt = 120) {
  const calls = []
  const store = { loading: false, totalPages: Math.ceil(total / 40), totalElements: total, photos: Array.from({ length: 40 }, (_, id) => ({ id })) }
  store.loadMore = async page => {
    calls.push(page)
    const start = page * 40
    const added = Array.from({ length: Math.max(0, Math.min(40, total - start)) }, (_, i) => ({ id: start + i }))
    store.photos.push(...added)
    return added.length > 0
  }
  const viewport = { clientHeight: 800, scrollTop: 0, get scrollHeight() { return store.photos.length >= overflowAt ? 1200 : 800 } }
  const c = { photoStore: store, page: { value: 0 }, pageSize: 40, loadingMore: { value: false }, scrollContainer: { value: viewport }, nextTick: async () => {}, message: { error() {} } }
  vm.createContext(c)
  vm.runInContext(ts.transpileModule(functions, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText + '\ngalleryActive = true', c)
  return { c, calls, store }
}
test('loads older pages even when the first forty thumbnails do not create a scrollbar', async () => {
  const { c, calls, store } = setup()
  await c.fillViewport()
  assert.deepEqual(calls, [1, 2])
  assert.equal(store.photos.length, 120)
  assert.equal(c.page.value, 2)
})
test('stops at the last page when the whole library fits in the viewport', async () => {
  const { c, calls, store } = setup(65, 1000)
  await c.fillViewport()
  assert.deepEqual(calls, [1])
  assert.equal(store.photos.length, 65)
})
test('failed loading does not mark older pages as exhausted and can be retried', async () => {
  const { c, store, calls } = setup()
  const original = store.loadMore
  store.loadMore = async () => { throw new Error('offline') }
  await c.fillViewport()
  assert.equal(c.page.value, 0)
  assert.equal(store.totalPages, 5)
  store.loadMore = original
  await c.fillViewport()
  assert.deepEqual(calls, [1, 2])
})
