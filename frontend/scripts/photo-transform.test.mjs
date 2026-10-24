import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/photoTransform.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } })
const { clampPhotoOffset, photoWheelScale, zoomPhotoOffset } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)

test('wheel zoom respects fit and maximum limits with consistent direction', () => {
  assert.equal(photoWheelScale(1, 100), 1)
  assert.equal(photoWheelScale(8, -100), 8)
  assert.ok(photoWheelScale(2, -100) > 2)
  assert.ok(photoWheelScale(2, 100) < 2)
  assert.equal(photoWheelScale(2, 0), 2)
})
test('zoom keeps the image point beneath the mouse stationary', () => {
  const offset = zoomPhotoOffset(30, 120, 2, 4)
  assert.equal((120 - offset) / 4, (120 - 30) / 2)
  assert.equal(zoomPhotoOffset(0, 0, 1, 2), 0)
})
test('drag clamps both image edges and centers images smaller than the viewport', () => {
  assert.equal(clampPhotoOffset(900, 800, 1000, 2), 300)
  assert.equal(clampPhotoOffset(-900, 800, 1000, 2), -300)
  assert.equal(clampPhotoOffset(120, 800, 1000, 2), 120)
  assert.equal(clampPhotoOffset(120, 300, 1000, 2), 0)
  assert.equal(clampPhotoOffset(120, 800, 1000, 1), 0)
})
