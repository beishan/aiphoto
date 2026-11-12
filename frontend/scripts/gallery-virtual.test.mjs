import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'
const source = readFileSync(new URL('../src/utils/visiblePhotoRange.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } })
const { visiblePhotoRange } = await import('data:text/javascript;base64,' + Buffer.from(outputText).toString('base64'))
test('scrolling both ways restores the same photo positions with bounded range', () => {
  const boxes = Array.from({ length: 100000 }, (_, i) => ({ top: Math.floor(i / 4) * 104, height: 100 }))
  const initial = visiblePhotoRange(boxes, 0, 1000)
  const later = visiblePhotoRange(boxes, 100000, 101000)
  assert.ok(later.start > initial.end)
  assert.ok(later.end - later.start < 50)
  assert.deepEqual(visiblePhotoRange(boxes, 0, 1000), initial)
  assert.deepEqual(visiblePhotoRange([], 0, 1000), { start: 0, end: 0 })
})
