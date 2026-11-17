import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import ts from 'typescript'
const source = readFileSync(new URL('../src/utils/taskGallery.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 } })
const { allTaskImportIds } = await import('data:text/javascript;base64,' + Buffer.from(outputText).toString('base64'))
test('all import covers pages beyond visible cards and excludes ineligible items', async () => {
  const calls = []
  const ids = await allTaskImportIds(async page => {
    calls.push(page)
    return { content: Array.from({ length: page === 2 ? 1 : 200 }, (_, i) => ({ id: page * 200 + i + 1 })), totalElements: 401 }
  }, asset => asset.id !== 201, () => true)
  assert.deepEqual(calls, [0, 1, 2])
  assert.equal(ids.length, 400)
  assert.equal(ids.at(-1), 401)
  assert.equal(ids.includes(201), false)
})
test('closing window while fetching prevents import of stale task data', async () => {
  let active = true
  const ids = await allTaskImportIds(async () => { active = false; return { content: [{ id: 1 }], totalElements: 1 } }, () => true, () => active)
  assert.deepEqual(ids, [])
})
