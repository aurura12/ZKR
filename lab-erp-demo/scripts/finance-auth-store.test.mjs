import assert from 'node:assert/strict'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { createPinia, setActivePinia } from 'pinia'

const projectRoot = path.resolve(import.meta.dirname, '..')

const writeTempModule = async (relativePath, source, tempRoot) => {
  const filePath = path.join(tempRoot, relativePath)
  await mkdir(path.dirname(filePath), { recursive: true })
  await writeFile(filePath, source, 'utf8')
  return filePath
}

const createLocalStorageMock = () => {
  const store = new Map()

  return {
    getItem(key) {
      return store.has(key) ? store.get(key) : null
    },
    setItem(key, value) {
      store.set(key, String(value))
    },
    removeItem(key) {
      store.delete(key)
    },
    clear() {
      store.clear()
    },
    has(key) {
      return store.has(key)
    }
  }
}

const tempRoot = await mkdtemp(path.join(projectRoot, '.tmp-finance-auth-store-'))

try {
  const storeSource = await readFile(path.join(projectRoot, 'src/stores/userStore.js'), 'utf8')

  await writeTempModule(
    'utils/request.js',
    `let handlers = {
      post: async () => ({ token: 'default-token' }),
      get: async () => ({})
    }

    export const requestCalls = []

    export const __setHandlers = nextHandlers => {
      handlers = { ...handlers, ...nextHandlers }
    }

    export default {
      post(url, payload) {
        requestCalls.push({ method: 'post', url, payload })
        return handlers.post(url, payload)
      },
      get(url) {
        requestCalls.push({ method: 'get', url })
        return handlers.get(url)
      }
    }
    `,
    tempRoot
  )

  const storeModulePath = await writeTempModule(
    'stores/userStore.js',
    storeSource.replace("@/utils/request", '../utils/request.js'),
    tempRoot
  )

  globalThis.localStorage = createLocalStorageMock()

  const cacheToken = Date.now()
  const [{ __setHandlers, requestCalls }, { useUserStore }] = await Promise.all([
    import(pathToFileURL(path.join(tempRoot, 'utils/request.js')).href),
    import(`${pathToFileURL(storeModulePath).href}?t=${cacheToken}`)
  ])

  setActivePinia(createPinia())
  const loginStore = useUserStore()

  __setHandlers({
    post: async (url, payload) => {
      if (url === '/api/auth/login') {
        return { token: 'finance-token-123' }
      }
      if (url === '/api/auth/register') {
        return { success: true }
      }
      throw new Error(`Unexpected POST ${url} ${JSON.stringify(payload)}`)
    },
    get: async url => {
      if (url === '/api/auth/me') {
        return { userId: 'u-1', username: 'alice', role: 'ADMIN', accountDomain: 'FINANCE' }
      }
      throw new Error(`Unexpected GET ${url}`)
    }
  })

  await loginStore.login({ username: 'alice', password: 'secret' })

  const sentLoginPayload = requestCalls.find(call => call.method === 'post' && call.url === '/api/auth/login')?.payload
  assert.equal(sentLoginPayload.domain, 'FINANCE', 'login should send the finance domain')
  assert.equal(loginStore.userInfo.accountDomain, 'FINANCE', 'login should persist accountDomain from /api/auth/me')
  assert.equal(globalThis.localStorage.getItem('token'), null, 'login should not rely on the shared token key')
  assert.equal(globalThis.localStorage.getItem('userInfo'), null, 'login should not rely on the shared userInfo key')
  assert.equal(globalThis.localStorage.getItem('finance_token'), 'finance-token-123', 'login should persist the finance token under the finance-scoped key')
  assert.equal(JSON.parse(globalThis.localStorage.getItem('finance_userInfo')).accountDomain, 'FINANCE', 'login should persist accountDomain into the finance-scoped localStorage key')

  requestCalls.length = 0

  setActivePinia(createPinia())
  const registerStore = useUserStore()
  await registerStore.register({ username: 'bob', password: 'secret', email: 'bob@example.com' })

  const sentRegisterPayload = requestCalls.find(call => call.method === 'post' && call.url === '/api/auth/register')?.payload
  assert.equal(sentRegisterPayload.domain, 'FINANCE', 'register should send the finance domain')

  requestCalls.length = 0
  globalThis.localStorage.clear()
  globalThis.localStorage.setItem('finance_token', 'stale-finance-token')
  globalThis.localStorage.setItem('finance_userInfo', JSON.stringify({ username: 'finance-user' }))
  globalThis.localStorage.setItem('token', 'shared-token-should-be-ignored')
  globalThis.localStorage.setItem('userInfo', JSON.stringify({ username: 'shared-user-should-be-ignored' }))

  setActivePinia(createPinia())
  const errorStore = useUserStore()
  assert.equal(errorStore.token, 'stale-finance-token', 'store initialization should read the finance-scoped token key')
  assert.equal(errorStore.userInfo.username, 'finance-user', 'store initialization should read the finance-scoped user info key')
  const mismatchError = new Error('Request failed with status code 403')
  mismatchError.response = { data: { message: '该账号仅允许登录财务系统' } }

  __setHandlers({
    post: async url => {
      if (url === '/api/auth/login') {
        throw mismatchError
      }
      return { ok: true }
    }
  })

  await assert.rejects(() => errorStore.login({ username: 'erp-user', password: 'secret' }))
  assert.equal(errorStore.errorMessage, '该账号仅允许登录财务系统', 'store should keep backend domain mismatch copy in state')

  errorStore.logout()
  assert.equal(globalThis.localStorage.getItem('finance_token'), null, 'logout should clear the finance-scoped token key')
  assert.equal(globalThis.localStorage.getItem('finance_userInfo'), null, 'logout should clear the finance-scoped user info key')
  assert.equal(globalThis.localStorage.getItem('token'), 'shared-token-should-be-ignored', 'logout should not touch the shared token key')
  assert.equal(JSON.parse(globalThis.localStorage.getItem('userInfo')).username, 'shared-user-should-be-ignored', 'logout should not touch the shared userInfo key')

  console.log('[finance-auth-store] PASS')
} finally {
  delete globalThis.localStorage
  await rm(tempRoot, { recursive: true, force: true })
}
