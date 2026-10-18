import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import ts from 'typescript'

const source = readFileSync(new URL('../src/api/pageResponse.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } })
const { normalizePage } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)

test('Spring VIA_DTO retains full image count and page navigation past the first 60', () => {
  const metadata = { totalElements: 145, totalPages: 3, size: 60, number: 1 }
  const rows = Array.from({ length: 60 }, (_, i) => ({ id: i + 61 }))
  const page = normalizePage({ content: rows, page: metadata })
  assert.equal(page.totalElements, 145)
  assert.equal(page.totalPages, 3)
  assert.equal(page.number, 1)
  assert.equal(page.content[0].id, 61)
  assert.equal(page.content.length, 60)
})

test('legacy pages and empty pages retain their metadata', () => {
  const legacy = { content: [], totalElements: 0, totalPages: 0, size: 60, number: 0 }
  assert.deepEqual(normalizePage(legacy), legacy)
  const { content, ...metadata } = legacy
  assert.deepEqual(normalizePage({ content, page: metadata }), legacy)
})
