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
    }
  }
}

const tempRoot = await mkdtemp(path.join(projectRoot, '.tmp-erp-auth-store-'))

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
        return { token: 'erp-token-123' }
      }
      if (url === '/api/auth/register') {
        return { success: true }
      }
      throw new Error(`Unexpected POST ${url} ${JSON.stringify(payload)}`)
    },
    get: async url => {
      if (url === '/api/auth/me') {
        return { userId: 'erp-1', username: 'erp-alice', role: 'USER', accountDomain: 'ERP' }
      }
      throw new Error(`Unexpected GET ${url}`)
    }
  })

  await loginStore.loginErp({ username: 'erp-alice', password: 'secret' })

  const sentLoginPayload = requestCalls.find(call => call.method === 'post' && call.url === '/api/auth/login')?.payload
  assert.equal(sentLoginPayload.domain, 'ERP', 'ERP login should send the ERP domain')
  assert.equal(loginStore.erpUserInfo.accountDomain, 'ERP', 'ERP login should persist ERP accountDomain from /api/auth/me')
  assert.equal(globalThis.localStorage.getItem('token'), null, 'ERP login should not rely on the shared token key')
  assert.equal(globalThis.localStorage.getItem('userInfo'), null, 'ERP login should not rely on the shared userInfo key')
  assert.equal(globalThis.localStorage.getItem('finance_token'), null, 'ERP login should not write into the finance token key')
  assert.equal(globalThis.localStorage.getItem('finance_userInfo'), null, 'ERP login should not write into the finance user info key')
  assert.equal(globalThis.localStorage.getItem('erp_token'), 'erp-token-123', 'ERP login should persist the ERP token under the ERP-scoped key')
  assert.equal(JSON.parse(globalThis.localStorage.getItem('erp_userInfo')).accountDomain, 'ERP', 'ERP login should persist ERP user info under the ERP-scoped key')

  requestCalls.length = 0

  setActivePinia(createPinia())
  const registerStore = useUserStore()
  await registerStore.registerErp({ role: 'BUSINESS', username: 'erp-bob', password: 'secret', teamName: 'Ops', name: 'Ops', email: 'ops@example.com' })

  const sentRegisterPayload = requestCalls.find(call => call.method === 'post' && call.url === '/api/auth/register')?.payload
  assert.equal(sentRegisterPayload.domain, 'ERP', 'ERP register should send the ERP domain')
  assert.equal(sentRegisterPayload.role, 'BUSINESS', 'ERP register should submit an ERP role')
  assert.equal(sentRegisterPayload.name, 'Ops', 'ERP register should submit the required name field')
  assert.equal(sentRegisterPayload.email, 'ops@example.com', 'ERP register should submit the required email field')

  requestCalls.length = 0
  globalThis.localStorage.clear()
  globalThis.localStorage.setItem('erp_token', 'stale-erp-token')
  globalThis.localStorage.setItem('erp_userInfo', JSON.stringify({ username: 'erp-user' }))
  globalThis.localStorage.setItem('finance_token', 'finance-token-should-stay')
  globalThis.localStorage.setItem('finance_userInfo', JSON.stringify({ username: 'finance-user-should-stay' }))

  setActivePinia(createPinia())
  const errorStore = useUserStore()
  assert.equal(errorStore.erpToken, 'stale-erp-token', 'store initialization should read the ERP-scoped token key')
  assert.equal(errorStore.erpUserInfo.username, 'erp-user', 'store initialization should read the ERP-scoped user info key')
  const mismatchError = new Error('Request failed with status code 403')
  mismatchError.response = { data: { message: '该账号仅允许登录 ERP 系统' } }

  __setHandlers({
    post: async url => {
      if (url === '/api/auth/login') {
        throw mismatchError
      }
      return { ok: true }
    }
  })

  await assert.rejects(() => errorStore.loginErp({ username: 'finance-user', password: 'secret' }))
  assert.equal(errorStore.erpErrorMessage, '该账号仅允许登录 ERP 系统', 'store should preserve backend ERP login errors directly in ERP state')

  errorStore.logoutErp()
  assert.equal(globalThis.localStorage.getItem('erp_token'), null, 'ERP logout should clear the ERP token key')
  assert.equal(globalThis.localStorage.getItem('erp_userInfo'), null, 'ERP logout should clear the ERP user info key')
  assert.equal(globalThis.localStorage.getItem('finance_token'), 'finance-token-should-stay', 'ERP logout should not touch the finance token key')
  assert.equal(JSON.parse(globalThis.localStorage.getItem('finance_userInfo')).username, 'finance-user-should-stay', 'ERP logout should not touch the finance user info key')

  console.log('[erp-auth-store] PASS')
} finally {
  delete globalThis.localStorage
  await rm(tempRoot, { recursive: true, force: true })
}
