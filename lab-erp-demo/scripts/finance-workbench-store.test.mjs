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

const tempRoot = await mkdtemp(path.join(projectRoot, '.tmp-finance-workbench-store-'))

try {
  const [storeSource, adaptersSource, enumsSource] = await Promise.all([
    readFile(path.join(projectRoot, 'src/stores/financeWorkbenchStore.js'), 'utf8'),
    readFile(path.join(projectRoot, 'src/utils/financeAdapters.js'), 'utf8'),
    readFile(path.join(projectRoot, 'src/utils/financeEnums.js'), 'utf8')
  ])

  await writeTempModule('utils/financeEnums.js', enumsSource, tempRoot)
  await writeTempModule(
    'utils/financeAdapters.js',
    adaptersSource.replace("@/utils/financeEnums", './financeEnums.js'),
    tempRoot
  )

  await writeTempModule(
    'api/finance/workbench.js',
    `let handlers = {
      runCostBatch: async () => ({ success: true, data: null }),
      getCostBatchPreview: async () => ({ success: true, data: null }),
      getClearingVentures: async () => ({ success: true, data: [] }),
      executeClearing: async () => ({ success: true, data: null }),
      prepareDividendSheet: async () => ({ success: true, data: null }),
      getDividendSheets: async () => ({ success: true, data: [] }),
      confirmDividendSheet: async () => ({ success: true, data: null }),
      createAdjustment: async () => ({ success: true, data: null }),
      getAdjustmentLogs: async () => ({ success: true, data: [] })
    }

    export const __setHandlers = nextHandlers => {
      handlers = { ...handlers, ...nextHandlers }
    }

    export const runCostBatch = payload => handlers.runCostBatch(payload)
    export const getCostBatchPreview = (ventureId, ledgerMonth) => handlers.getCostBatchPreview(ventureId, ledgerMonth)
    export const getClearingVentures = params => handlers.getClearingVentures(params)
    export const executeClearing = payload => handlers.executeClearing(payload)
    export const prepareDividendSheet = payload => handlers.prepareDividendSheet(payload)
    export const getDividendSheets = params => handlers.getDividendSheets(params)
    export const confirmDividendSheet = payload => handlers.confirmDividendSheet(payload)
    export const createAdjustment = payload => handlers.createAdjustment(payload)
    export const getAdjustmentLogs = params => handlers.getAdjustmentLogs(params)
    `,
    tempRoot
  )

  const storeModulePath = await writeTempModule(
    'stores/financeWorkbenchStore.js',
    storeSource
      .replace("@/api/finance/workbench", '../api/finance/workbench.js')
      .replace("@/utils/financeAdapters", '../utils/financeAdapters.js'),
    tempRoot
  )

  const cacheToken = Date.now()
  const [{ __setHandlers }, { useFinanceWorkbenchStore, useFinanceClearingSurface }] = await Promise.all([
    import(pathToFileURL(path.join(tempRoot, 'api/finance/workbench.js')).href),
    import(`${pathToFileURL(storeModulePath).href}?t=${cacheToken}`)
  ])

  setActivePinia(createPinia())
  const store = useFinanceWorkbenchStore()
  const clearingSurface = useFinanceClearingSurface()
  const clearingExecutions = []

  __setHandlers({
    getClearingVentures: async () => ({
      success: true,
      message: 'Clearing ventures loaded',
      data: [
        {
          venture: {
            legacy_venture_id: 201,
            display_name: 'Nested Legacy Venture'
          },
          ledger_month: '2026-03',
          status: 'PENDING',
          total_cost: '1200.00',
          final_revenue: '1600.00'
        },
        {
          legacy_venture_id: 305,
          display_name: 'Top Level Legacy Venture',
          ledger_month: '2026-04',
          status: 'READY',
          total_cost: '880.00',
          final_revenue: '1100.00'
        },
        {
          venture: {
            project_id: 'P-88',
            display_name: 'Project Only Venture'
          },
          ledger_month: '2026-05',
          status: 'READY',
          total_cost: '760.00',
          final_revenue: '900.00'
        }
      ],
      traceId: 'trace-clearing-list'
    }),
    executeClearing: async payload => {
      clearingExecutions.push(payload)
      return {
        success: true,
        message: 'clearing completed',
        data: {
          clearing_sheet_id: 44,
          ledger_month: payload.venture_id === 201 ? '2026-03' : '2026-05',
          final_revenue: payload.final_revenue,
          total_cost: payload.venture_id === 201 ? '1200.00' : '760.00',
          status: 'CLEARED'
        },
        traceId: 'trace-clearing-exec'
      }
    },
    getDividendSheets: async params => ({
      success: true,
      message: 'Dividend sheets loaded',
      data: {
        rows: [{ projectId: params.projectId, amount: 88.5 }],
        totalCount: 1
      },
      traceId: 'trace-dividend'
    }),
    runCostBatch: async payload => ({
      success: true,
      message: 'cost batch completed',
      data: {
        batch_id: 10,
        ledger_month: payload.ledgerMonth || payload.ledger_month,
        status: 'COMPLETED',
        reused_existing_batch: true
      },
      traceId: 'trace-cost'
    })
  })

  const dividendEnvelope = await store.loadDividendSheets({ projectId: 'P1' })
  await clearingSurface.ensureVenturesLoaded()

  assert.equal(dividendEnvelope.ok, true, 'store actions should treat success-flag envelopes as successful')
  assert.equal(store.dividendSheets.length, 1, 'store should normalize backend rows payloads into dividend sheet lists')
  assert.equal(store.dividendSheets[0].projectId, 'P1', 'store should preserve normalized dividend rows in state')

  assert.deepEqual(
    clearingSurface.ventureOptions.value.map(option => option.value),
    ['legacy:201', 'legacy:305', 'project:P-88'],
    'clearing surface should normalize nested and top-level venture ids through one shared resolver'
  )
  assert.equal(clearingSurface.canExecute.value, true, 'default selected clearing venture should be executable when it appears in the list')

  clearingSurface.selectVenture('project:P-88')
  assert.equal(
    clearingSurface.selectedVentureId.value,
    'project:P-88',
    'surface selection should keep project-based venture identities stable'
  )
  assert.equal(clearingSurface.canExecute.value, true, 'project-id-only ventures should remain executable when selectable')

  await clearingSurface.executeSelected()
  assert.deepEqual(
    clearingExecutions[0],
    {
      ventureId: 'P-88',
      finalRevenue: '900.00'
    },
    'surface execution should reuse the same resolved venture identity used for list selection'
  )

  await store.submitCostBatch({ ledgerMonth: '2026-03', rerunExistingMonth: true })

  assert.equal(store.ledgerMonth, '2026-03', 'store should keep the selected ledger month after cost batch runs')
  assert.equal(store.costRunResult.batchId, 10, 'store should expose normalized batch ids from backend mutation payloads')
  assert.equal(store.costRunResult.ledgerMonth, '2026-03', 'store should expose normalized ledger month aliases')
  assert.equal(store.costRunResult.referenceId, 10, 'store should derive mutation reference ids from backend payload aliases')
  assert.equal(store.costRunResult.operation, 'COMPLETED', 'store should derive operation summaries from backend status fields')

  console.log('finance workbench store contract tests passed')
} finally {
  await rm(tempRoot, { recursive: true, force: true })
}
