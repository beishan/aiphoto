import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import ts from 'typescript'
const source = readFileSync(new URL('../src/utils/thumbnailQueue.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } })
const { createThumbnailQueue } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
const tick = () => new Promise(resolve => setImmediate(resolve))

test('overlapping refreshes share requests and never exceed four active thumbnails', async () => {
  let active = 0, peak = 0, calls = 0
  const releases = []
  const queue = createThumbnailQueue(async () => {
    calls++; active++; peak = Math.max(peak, active)
    await new Promise(resolve => releases.push(resolve))
    active--
  })
  const first = queue.enqueue(1)
  assert.equal(queue.enqueue(1), first)
  const tasks = [first, ...Array.from({ length: 11 }, (_, i) => queue.enqueue(i + 2))]
  await tick()
  assert.equal(calls, 4)
  while (releases.length) {
    releases.splice(0).forEach(resolve => resolve())
    await tick()
  }
  await Promise.all(tasks)
  assert.equal(calls, 12)
  assert.equal(peak, 4)
})
test('cancel aborts in-flight requests and discards queued work; next page can load', async () => {
  const calls = []
  const queue = createThumbnailQueue(async (id, signal) => {
    calls.push(id)
    await new Promise(resolve => signal.addEventListener('abort', resolve, { once: true }))
    assert.equal(signal.aborted, true)
  }, 1)
  const tasks = [queue.enqueue(1), queue.enqueue(2)]
  await tick()
  queue.cancel()
  await Promise.all(tasks)
  assert.deepEqual(calls, [1])
  const next = queue.enqueue(3)
  await tick()
  queue.dispose()
  await next
  await queue.enqueue(4)
  assert.deepEqual(calls, [1, 3])
})
test('a failed request releases its slot and can be retried', async () => {
  let calls = 0
  const queue = createThumbnailQueue(async () => { calls++; throw new Error('network failure') }, 1)
  await Promise.all([queue.enqueue(1), queue.enqueue(2)])
  await queue.enqueue(1)
  assert.equal(calls, 3)
})
