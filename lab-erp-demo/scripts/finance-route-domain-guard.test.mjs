import assert from 'node:assert/strict'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const projectRoot = path.resolve(import.meta.dirname, '..')
const financeRoutesModule = await import(pathToFileURL(path.join(projectRoot, 'src/router/financeRoutes.js')).href)

let domainAccessModule = null

try {
  domainAccessModule = await import(pathToFileURL(path.join(projectRoot, 'src/router/domainAccess.js')).href)
} catch {
  domainAccessModule = null
}

assert.ok(domainAccessModule, 'router domain access should be centralized in src/router/domainAccess.js')

const { financeRoutes } = financeRoutesModule
const {
  DOMAIN_FINANCE,
  DOMAIN_ERP,
  canAccessRouteDomain,
  getDefaultRouteForDomain,
  resolveRouteDomain
} = domainAccessModule

assert.equal(financeRoutes[0].meta.routeDomain, DOMAIN_FINANCE, 'finance shell route should be marked as finance-only')

for (const childRoute of financeRoutes[0].children) {
  if (childRoute.redirect) {
    continue
  }

  assert.equal(childRoute.meta.routeDomain, DOMAIN_FINANCE, `${childRoute.name} should be marked as finance-only`)
}

assert.equal(resolveRouteDomain({ path: '/finance/overview' }), DOMAIN_FINANCE, 'finance paths should resolve to the finance domain')
assert.equal(resolveRouteDomain({ path: '/manager/dashboard' }), DOMAIN_ERP, 'manager paths should resolve to the ERP domain')
assert.equal(resolveRouteDomain({ path: '/workspace/project/42' }), DOMAIN_ERP, 'workspace paths should resolve to the ERP domain')
assert.equal(resolveRouteDomain({ path: '/profile' }), DOMAIN_ERP, 'profile path should resolve to the ERP domain')
assert.equal(resolveRouteDomain({ path: '/login' }), null, 'public routes should not resolve to a protected domain')

assert.equal(canAccessRouteDomain({ accountDomain: DOMAIN_FINANCE, to: { path: '/finance/overview' } }), true, 'finance users should keep finance access')
assert.equal(canAccessRouteDomain({ accountDomain: DOMAIN_FINANCE, to: { path: '/manager/dashboard' } }), false, 'finance users should not reach manager routes')
assert.equal(canAccessRouteDomain({ accountDomain: DOMAIN_FINANCE, to: { path: '/workspace/project/42' } }), false, 'finance users should not reach workspace routes')
assert.equal(canAccessRouteDomain({ accountDomain: DOMAIN_FINANCE, to: { path: '/profile' } }), false, 'finance users should not reach profile routes')
assert.equal(canAccessRouteDomain({ accountDomain: DOMAIN_ERP, to: { path: '/finance/overview' } }), false, 'ERP users should not reach finance routes')
assert.equal(canAccessRouteDomain({ accountDomain: DOMAIN_ERP, to: { path: '/manager/dashboard' } }), true, 'ERP users should keep ERP access')
assert.equal(canAccessRouteDomain({ accountDomain: '', to: { path: '/finance/overview' } }), false, 'blank account domains should not reach finance routes')
assert.equal(canAccessRouteDomain({ accountDomain: '   ', to: { path: '/manager/dashboard' } }), false, 'whitespace account domains should not reach ERP routes')
assert.equal(canAccessRouteDomain({ to: { path: '/finance/overview' } }), false, 'missing account domains should not reach finance routes')
assert.equal(canAccessRouteDomain({ to: { path: '/manager/dashboard' } }), false, 'missing account domains should not reach ERP routes')
assert.equal(canAccessRouteDomain({ accountDomain: '', to: { path: '/login' } }), true, 'blank account domains should still reach public routes')
assert.equal(canAccessRouteDomain({ to: { path: '/login' } }), true, 'missing account domains should still reach public routes')

assert.equal(getDefaultRouteForDomain(DOMAIN_FINANCE), '/finance/overview', 'finance users should redirect to the finance landing route')
assert.equal(getDefaultRouteForDomain(DOMAIN_ERP), '/manager/dashboard', 'ERP users should redirect to the ERP landing route')

console.log('[finance-route-domain-guard] PASS')
