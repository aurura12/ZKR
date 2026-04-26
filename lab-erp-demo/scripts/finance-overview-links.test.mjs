import assert from 'node:assert/strict'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const projectRoot = path.resolve(import.meta.dirname, '..')
const routesModule = await import(pathToFileURL(path.join(projectRoot, 'src/router/financeRoutes.js')).href)

const { financeNavigationItems, financeOverviewLinkTargets, createFinanceOverviewLinkActions } = routesModule

assert.ok(financeOverviewLinkTargets, 'finance overview link targets should be exported for stable navigation behavior')
assert.equal(financeOverviewLinkTargets.risk, 'finance-clearing', 'risk overview links should target clearing')
assert.equal(financeOverviewLinkTargets.reconciliation, 'finance-clearing', 'reconciliation overview links should target clearing')
assert.equal(financeOverviewLinkTargets.reporting, 'finance-cost-batches', 'reporting overview links should target cost batch')

const registeredRouteNames = new Set(financeNavigationItems.map(item => item.routeName))

assert.ok(registeredRouteNames.has(financeOverviewLinkTargets.risk), 'risk target should match a registered finance route')
assert.ok(registeredRouteNames.has(financeOverviewLinkTargets.reconciliation), 'reconciliation target should match a registered finance route')
assert.ok(registeredRouteNames.has(financeOverviewLinkTargets.reporting), 'reporting target should match a registered finance route')

const pushedRoutes = []
const actions = createFinanceOverviewLinkActions({
  push(location) {
    pushedRoutes.push(location)
    return Promise.resolve(location)
  }
})

await actions.openClearingWorkbench()
await actions.openCostBatchWorkbench()

assert.deepEqual(
  pushedRoutes,
  [{ name: 'finance-clearing' }, { name: 'finance-cost-batches' }],
  'overview link actions should push the expected named finance routes'
)

console.log('finance overview link guard passed')
