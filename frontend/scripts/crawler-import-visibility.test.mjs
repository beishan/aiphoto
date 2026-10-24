import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
const action = source.slice(source.indexOf('async function importSelected('), source.indexOf('\nfunction newImportKey('))
function setup(failSecond = false) {
  let release
  const gate = new Promise(resolve => { release = resolve })
  const context = {
    selectedAssetGroups: () => [{ jobId: 1, ids: [11] }, { jobId: 2, ids: [22] }],
    busy: { value: '' }, pendingImportAssetIds: { value: new Set() }, selectedAssets: { value: new Set([11, 22]) },
    activeTab: { value: 'review' }, taskDrawerVisible: { value: false }, closePreviewViewer() {},
    targetAlbumId: { value: null }, targetTagIds: { value: [] }, pendingImport: { value: null },
    recentImportProgressCount: { value: 0 }, newImportKey: () => 'key',
    crawlApi: { importAssets: async jobId => { await gate; if (failSecond && jobId === 2) throw new Error('rejected'); return { data: { batchId: jobId } } } },
    loadImportBatch: async () => {}, monitorImportBatches: (...args) => { context.monitored = args },
    reloadVisibleAssets: async () => { context.refreshed = true },
    ElMessage: { info() {}, error() {} },
  }
  vm.createContext(context)
  vm.runInContext(ts.transpileModule(action, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, context)
  return { context, release }
}

test('hides every selected image before submission resolves and keeps accepted batches hidden', async () => {
  const { context: c, release } = setup()
  const result = c.importSelected([11, 22])
  assert.deepEqual([...c.pendingImportAssetIds.value], [11, 22])
  assert.equal(c.selectedAssets.value.size, 0)
  release()
  await result
  assert.deepEqual([...c.pendingImportAssetIds.value], [11, 22])
  assert.deepEqual([...c.monitored[0]], [1, 2])
})

test('restores rejected images and refreshes while retaining monitoring for accepted groups', async () => {
  const { context: c, release } = setup(true)
  const result = c.importSelected([11, 22])
  release()
  await result
  assert.deepEqual([...c.pendingImportAssetIds.value], [11])
  assert.deepEqual([...c.monitored[0]], [1])
  assert.equal(c.refreshed, true)
  assert.equal(c.busy.value, '')
})
