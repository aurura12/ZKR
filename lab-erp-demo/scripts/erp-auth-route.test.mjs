import assert from 'node:assert/strict'
import path from 'node:path'
import { readFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

const projectRoot = path.resolve(import.meta.dirname, '..')
const domainAccessModule = await import(pathToFileURL(path.join(projectRoot, 'src/router/domainAccess.js')).href)
const routerSource = await readFile(path.join(projectRoot, 'src/router/index.js'), 'utf8')

const {
  DOMAIN_ERP,
  DOMAIN_FINANCE,
  getAnonymousProtectedRedirect,
  getAuthenticatedEntryRedirect,
  getErpLandingRoute
} = domainAccessModule

assert.equal(getErpLandingRoute('BUSINESS'), '/manager/dashboard', 'BUSINESS ERP users should land on the manager dashboard')
assert.equal(getErpLandingRoute('user'), '/workspace', 'non-BUSINESS ERP users should land on the workspace')
assert.equal(getErpLandingRoute(''), '/workspace', 'blank ERP roles should fall back to the workspace')

assert.equal(
  getAnonymousProtectedRedirect({ path: '/workspace' }),
  '/erp-login',
  'anonymous ERP navigation should redirect to the ERP login entry'
)

assert.equal(
  getAnonymousProtectedRedirect({ path: '/finance/overview' }),
  '/login',
  'anonymous finance navigation should keep redirecting to the finance login entry'
)

assert.equal(
  getAuthenticatedEntryRedirect({
    to: { path: '/erp-login' },
    financeToken: 'finance-token',
    financeUserInfo: { accountDomain: DOMAIN_FINANCE, role: 'MANAGER' }
  }),
  '/finance/overview',
  'finance sessions should be redirected away from /erp-login to the finance overview'
)

assert.equal(
  getAuthenticatedEntryRedirect({
    to: { path: '/erp-login' },
    erpToken: 'erp-token',
    erpUserInfo: { accountDomain: DOMAIN_ERP, role: 'BUSINESS' }
  }),
  '/manager/dashboard',
  'BUSINESS ERP sessions should be redirected away from /erp-login to the manager dashboard'
)

assert.equal(
  getAuthenticatedEntryRedirect({
    to: { path: '/erp-login' },
    erpToken: 'erp-token',
    erpUserInfo: { accountDomain: DOMAIN_ERP, role: 'ENGINEER' }
  }),
  '/workspace',
  'non-BUSINESS ERP sessions should be redirected away from /erp-login to the workspace'
)

assert.equal(
  getAuthenticatedEntryRedirect({
    to: { path: '/erp-login' },
    activeAuthScope: DOMAIN_ERP,
    financeToken: 'finance-token',
    financeUserInfo: { accountDomain: DOMAIN_FINANCE, role: 'MANAGER' },
    erpToken: 'erp-token',
    erpUserInfo: { accountDomain: DOMAIN_ERP, role: 'ENGINEER' }
  }),
  '/workspace',
  'when both sessions exist, the active ERP auth scope should win for /erp-login redirects'
)

assert.equal(
  getAuthenticatedEntryRedirect({
    to: { path: '/erp-login' }
  }),
  null,
  'anonymous visitors should still be allowed to reach /erp-login'
)

assert.equal(
  getAuthenticatedEntryRedirect({
    to: { path: '/workspace' },
    erpToken: 'erp-token',
    erpUserInfo: { accountDomain: DOMAIN_ERP, role: 'BUSINESS' }
  }),
  null,
  'authenticated redirects should only run for the ERP entry route'
)

assert.match(routerSource, /getAuthenticatedEntryRedirect/, 'router guard should delegate ERP entry redirects to the shared route helper')
assert.match(routerSource, /getAnonymousProtectedRedirect/, 'router guard should delegate anonymous protected-route redirects to the shared route helper')
assert.match(routerSource, /userStore\.erpToken/, 'router guard should inspect the ERP token state')
assert.match(routerSource, /userStore\.erpUserInfo/, 'router guard should inspect the ERP user info state')
assert.match(routerSource, /active_auth_scope/, 'router guard should honor the persisted active auth scope when both sessions exist')

console.log('[erp-auth-route] PASS')
