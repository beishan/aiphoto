import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import ts from 'typescript'

function compile(path) {
  return ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText
}

test('new tab restores account before the authenticated layout mounts', async () => {
  let guard
  const session = new Map()
  const user = { id: 7, dockConfig: { iconStyle: 'custom' } }
  let requests = 0
  const router = { beforeEach: fn => { guard = fn } }
  vm.runInNewContext(compile('../src/router/index.ts'), {
    exports: {},
    localStorage: { getItem: () => 'valid-token' },
    sessionStorage: { getItem: key => session.get(key), setItem: (key, value) => session.set(key, value) },
    require: name => ({
      'vue-router': { createRouter: () => router, createWebHistory() {} },
      '@/utils/authSession': { isSessionTokenValid: () => true },
      '@/stores/websocket': { connectWebSocket() {} },
      '@/api/userApi': { userApi: { me: async () => { requests++; return { data: user } } } },
    })[name],
  })
  await guard({ meta: { requiresAuth: true } }, {}, () => {
    assert.deepEqual(JSON.parse(session.get('user')), user)
  })
  await guard({ meta: { requiresAuth: true } }, {}, () => {})
  assert.equal(requests, 1)
  session.set('user', '{invalid')
  await guard({ meta: { requiresAuth: true } }, {}, () => {})
  assert.equal(requests, 2)
})

test('Dock uses current account settings rather than another account device settings', () => {
  let user = { id: 7, dockConfig: { iconStyle: 'custom', iconSize: 36 } }
  const context = { exports: {},
    sessionStorage: { getItem: () => JSON.stringify(user) },
    localStorage: { getItem: () => JSON.stringify({ iconStyle: 'minimal' }) },
  }
  vm.runInNewContext(compile('../src/utils/themeAppearance.ts'), context)
  assert.equal(context.exports.loadDockConfig().iconStyle, 'custom')
  assert.equal(context.exports.loadDockConfig().iconSize, 36)
  user = { id: 8, dockConfig: null }
  assert.equal(context.exports.loadDockConfig().iconStyle, 'macos26')
})
