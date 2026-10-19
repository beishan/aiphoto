import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/authSession.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } })
function setup(path = '/') {
  const local = new Map()
  const session = new Map([['user', '{}']])
  const redirects = []
  const context = { exports: {}, atob, Date,
    localStorage: { getItem: key => local.get(key), removeItem: key => local.delete(key) },
    sessionStorage: { removeItem: key => session.delete(key) },
    window: { location: { pathname: path, replace: url => redirects.push(url) } },
  }
  vm.runInNewContext(outputText, context)
  return { ...context.exports, local, session, redirects }
}
const token = payload => `header.${Buffer.from(JSON.stringify(payload)).toString('base64url')}.signature`

test('expiry boundary, missing and malformed JWTs are rejected', () => {
  const { isSessionTokenValid: valid } = setup()
  assert.equal(valid(token({ exp: 101 }), 100000), true)
  for (const value of [null, 'broken', 'a.b.c', token({}), token({ exp: '101' }), token({ exp: 100 })]) {
    assert.equal(valid(value, 100000), false)
  }
})
test('idle expired session clears credentials and redirects only once', () => {
  const app = setup('/crawler')
  app.local.set('token', token({ exp: 1 }))
  app.checkSession()
  app.checkSession()
  assert.deepEqual(app.redirects, ['/login'])
  assert.equal(app.local.has('token'), false)
  assert.equal(app.session.has('user'), false)
})
test('valid session and login page do not redirect', () => {
  const app = setup()
  app.local.set('token', token({ exp: Date.now() / 1000 + 60 }))
  app.checkSession()
  assert.deepEqual(app.redirects, [])
  const login = setup('/login')
  login.checkSession()
  assert.deepEqual(login.redirects, [])
})

test('API interceptor redirects for protected 401 only, preserving login errors and permission failures', async () => {
  const httpSource = readFileSync(new URL('../src/api/http.ts', import.meta.url), 'utf8')
  const { outputText: httpCode } = ts.transpileModule(httpSource, { compilerOptions: { module: ts.ModuleKind.CommonJS } })
  let onError
  let redirects = 0
  const http = { interceptors: { request: { use() {} }, response: { use(_success, error) { onError = error } } } }
  vm.runInNewContext(httpCode, {
    exports: {}, Promise,
    require: name => name === 'axios' ? { default: { create: () => http } } : { redirectToLogin: () => redirects++ },
  })
  for (const [status, url, expected] of [[403, '/photos', 0], [401, '/auth/login', 0], [401, '/photos', 1]]) {
    const error = { response: { status }, config: { url } }
    await assert.rejects(onError(error), value => value === error)
    assert.equal(redirects, expected)
  }
})
