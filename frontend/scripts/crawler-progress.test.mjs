import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/crawlProgress.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } })
const { crawlJobProgress } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
const downloading = { phase: 'DOWNLOAD', status: 'RUNNING', pagesFound: 1, pagesProcessed: 1, imagesTotal: 100, imagesProcessed: 27 }

test('parsed pages do not make image collection show 100%; lists and details share the same ratio', () => {
  assert.equal(crawlJobProgress(downloading), 27)
  assert.equal(crawlJobProgress(downloading, { total: 100, processed: 27 }), 27)
  assert.equal(crawlJobProgress({ ...downloading, imagesProcessed: 0 }), 0)
})
test('active tasks never round up to 100% before finishing', () => {
  assert.equal(crawlJobProgress({ ...downloading, imagesTotal: 1000, imagesProcessed: 999 }), 99)
  assert.equal(crawlJobProgress({ ...downloading, imagesProcessed: 100 }), 99)
  assert.equal(crawlJobProgress({ ...downloading, status: 'COMPLETED' }), 100)
})
test('failed and skipped images count as processed; partial tasks can finish processing', () => {
  assert.equal(crawlJobProgress({ ...downloading, phase: 'REVIEW', status: 'PARTIAL', imagesProcessed: 100 }), 100)
  assert.equal(crawlJobProgress({ ...downloading, status: 'CANCELLED', imagesProcessed: 40 }), 40)
})
test('undiscovered images and missing counts do not fall back to page completion', () => {
  assert.equal(crawlJobProgress({ ...downloading, imagesTotal: 0 }), 0)
  assert.equal(crawlJobProgress({ phase: 'DOWNLOAD', status: 'RUNNING', pagesFound: 1, pagesProcessed: 1 }), 0)
})
test('discovery and confirmation retain their existing progress behavior', () => {
  assert.equal(crawlJobProgress({ ...downloading, phase: 'DISCOVERY', listProcessed: 16 }), 16)
  assert.equal(crawlJobProgress({ ...downloading, phase: 'AWAITING_CONFIRMATION' }), 45)
})
