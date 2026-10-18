import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { parse } from '@vue/compiler-sfc'

test('collection dialogs preserve a single element root for the out-in route transition', () => {
  const source = readFileSync(new URL('../src/views/CrawlerView.vue', import.meta.url), 'utf8')
  const { descriptor, errors } = parse(source)
  assert.deepEqual(errors, [])
  const roots = descriptor.template.ast.children.filter(node => node.type !== 3
    && !(node.type === 2 && !node.content.trim()))

  // Vue Transition cannot run leave hooks on a component Fragment. In out-in
  // mode this leaves the router waiting instead of mounting the next page.
  assert.equal(roots.length, 1, 'route dialogs must be inside the page root, not its siblings')
  assert.equal(roots[0].type, 1)
  assert.equal(roots[0].tag, 'main')
})
