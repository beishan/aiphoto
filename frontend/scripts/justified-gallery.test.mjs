import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import justifiedLayout from 'justified-layout'
const source = readFileSync(new URL('../src/components/JustifiedPhotoGrid.vue', import.meta.url), 'utf8')
const logic = source.slice(source.indexOf('const items = computed'), source.indexOf('</script>'))
function layout(width, photos) {
  const context = { props: { photos, startIndex: 0, count: photos.length, rowHeight: 260 }, width: { value: width }, justifiedLayout,
    computed: fn => ({ get value() { return fn() } }) }
  vm.createContext(context)
  vm.runInContext(ts.transpileModule(logic, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText + '\nglobalThis.result = layout.value', context)
  return context.result
}
test('mixed portrait and landscape images align full rows with four pixel gaps', () => {
  const photos = Array.from({ length: 25 }, (_, i) => ({ width: i % 2 ? 1600 : 800, height: 1200 }))
  const result = layout(1024, photos)
  assert.equal(result.boxes.length, photos.length)
  const rows = new Map()
  result.boxes.forEach(box => {
    assert.ok(box.width > 0 && box.height > 0)
    assert.ok(box.left + box.width <= 1024.01)
    const row = rows.get(box.top) || []
    row.push(box); rows.set(box.top, row)
  })
  const allRows = [...rows.values()]
  allRows.forEach((row, index) => {
    row.forEach((box, i) => {
      assert.equal(box.height, row[0].height)
      if (i) assert.ok(Math.abs(box.left - row[i - 1].left - row[i - 1].width - 4) < 0.01)
    })
    if (index < allRows.length - 1) assert.ok(Math.abs(row.at(-1).left + row.at(-1).width - 1024) < 0.01)
  })
})
test('narrow screens and missing dimensions retain every image', () => {
  const photos = [{ width: null, height: null }, { width: 800, height: 1600 }, { width: 0, height: 0 }]
  const result = layout(320, photos)
  assert.equal(result.boxes.length, 3)
  result.boxes.forEach(box => assert.ok(Number.isFinite(box.width) && box.left + box.width <= 320.01))
  assert.equal(layout(0, photos).boxes.length, 0)
})

