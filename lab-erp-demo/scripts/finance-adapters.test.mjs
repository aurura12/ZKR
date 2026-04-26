import assert from 'node:assert/strict'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const projectRoot = path.resolve(import.meta.dirname, '..')

const writeTempModule = async (relativePath, source, tempRoot) => {
  const filePath = path.join(tempRoot, relativePath)
  await mkdir(path.dirname(filePath), { recursive: true })
  await writeFile(filePath, source, 'utf8')
  return filePath
}

const tempRoot = await mkdtemp(path.join(projectRoot, '.tmp-finance-adapters-'))

try {
  const [adaptersSource, enumsSource] = await Promise.all([
    readFile(path.join(projectRoot, 'src/utils/financeAdapters.js'), 'utf8'),
    readFile(path.join(projectRoot, 'src/utils/financeEnums.js'), 'utf8')
  ])

  await writeTempModule('utils/financeEnums.js', enumsSource, tempRoot)
  const adapterModulePath = await writeTempModule(
    'utils/financeAdapters.js',
    adaptersSource.replace("@/utils/financeEnums", './financeEnums.js'),
    tempRoot
  )

  const {
    normalizeFinanceBankBalancePayload,
    normalizeFinanceAiResponse,
    normalizeFinanceListPayload,
    normalizeFinanceMutationResult,
    normalizeFinanceTransactionQuery,
    unwrapFinanceEnvelope
  } = await import(
    `${pathToFileURL(adapterModulePath).href}?t=${Date.now()}`
  )

  const envelope = unwrapFinanceEnvelope({
    success: true,
    message: 'loaded',
    data: { rows: [{ id: 1 }] },
    traceId: 'trace-123',
    timestamp: '2026-03-12T08:00:00Z',
    meta: {
      page: 2,
      size: 25,
      total: 80,
      total_pages: 4,
      trace_id: 'trace-123',
      timestamp: '2026-03-12T08:00:00Z'
    }
  })

  assert.equal(envelope.status, 'success', 'envelope should derive success status from the backend success flag')
  assert.equal(envelope.ok, true, 'envelope should mark success responses as ok even when status is omitted')
  assert.equal(envelope.success, true, 'envelope should expose a success boolean for stores and views')
  assert.equal(envelope.meta.totalPages, 4, 'envelope meta should normalize total_pages aliases')
  assert.equal(envelope.meta.traceId, 'trace-123', 'envelope meta should normalize trace id aliases')

  const failedEnvelope = unwrapFinanceEnvelope({
    success: false,
    message: 'validation failed',
    data: null,
    trace_id: 'trace-failed',
    meta: {
      trace_id: 'trace-failed'
    }
  }, [])

  assert.equal(failedEnvelope.status, 'error', 'failure envelopes should derive error status from success false')
  assert.equal(failedEnvelope.ok, false, 'failure envelopes should not be marked as ok')
  assert.equal(failedEnvelope.success, false, 'failure envelopes should preserve the backend success flag')
  assert.equal(failedEnvelope.traceId, 'trace-failed', 'failure envelopes should normalize trace_id aliases')

  assert.deepEqual(
    normalizeFinanceListPayload({ rows: [{ id: 2 }] }),
    [{ id: 2 }],
    'list payloads should normalize rows aliases from backend list responses'
  )

  const wallets = normalizeFinanceListPayload({ wallets: [{ wallet_id: 9 }] })
  assert.equal(wallets.length, 1, 'list payloads should normalize wallet collections from finance wallet responses')
  assert.equal(wallets[0].wallet_id, 9, 'wallet payloads should preserve backend snake_case fields')
  assert.equal(wallets[0].walletId, 9, 'wallet payloads should expose camelCase aliases for backend snake_case fields')

  const aiEnvelope = unwrapFinanceEnvelope({
    success: true,
    data: {
      context_mode: 'rag',
      history: [{ role: 'assistant', created_at: '2026-03-12T08:00:00Z' }]
    }
  })
  const aiResponse = normalizeFinanceAiResponse(aiEnvelope.data)

  assert.equal(aiResponse.contextMode, 'rag', 'adapter should normalize AI context_mode aliases')
  assert.equal(aiResponse.history[0].createdAt, '2026-03-12T08:00:00Z', 'adapter should normalize nested AI history aliases')

  const mutation = normalizeFinanceMutationResult({
    batch_id: 10,
    ledger_month: '2026-03',
    status: 'COMPLETED',
    reused_existing_batch: true
  })

  assert.equal(mutation.referenceId, 10, 'mutation results should derive reference ids from backend aliases')
  assert.equal(mutation.batchId, 10, 'mutation results should expose camelCase aliases for backend snake_case fields')
  assert.equal(mutation.ledgerMonth, '2026-03', 'mutation results should expose ledger month aliases')
  assert.equal(mutation.operation, 'COMPLETED', 'mutation results should derive operation from backend status values')
  assert.equal(mutation.reusedExistingBatch, true, 'mutation results should preserve snake_case aliases in camelCase form')

  const idleMutation = normalizeFinanceMutationResult(null)
  assert.equal(idleMutation.success, false, 'idle mutation state must not look like a completed success')
  assert.equal(idleMutation.operation, '', 'idle mutation state should stay empty')
  assert.equal(idleMutation.referenceId, null, 'idle mutation state should not expose a reference id')

  const transactionQuery = normalizeFinanceTransactionQuery({
    user_id: 'U-1',
    transaction_type: 'DIVIDEND',
    cash_flow_direction: 'IN',
    source_table: 'DIVIDEND_SHEET',
    limit: 20
  })

  assert.deepEqual(
    transactionQuery,
    {
      limit: 20,
      userId: 'U-1',
      type: 'DIVIDEND',
      direction: 'IN',
      sourceTable: 'DIVIDEND_SHEET'
    },
    'transaction query normalization should accept backend snake_case aliases'
  )

  const bankPayload = normalizeFinanceBankBalancePayload({
    balance: 123.45,
    operator: 'auditor',
    remark: 'manual close'
  })

  assert.deepEqual(
    bankPayload,
    {
      balance: 123.45,
      operator: 'auditor',
      remark: 'manual close'
    },
    'bank balance payload normalization should keep only backend-supported fields'
  )

  console.log('finance adapter contract tests passed')
} finally {
  await rm(tempRoot, { recursive: true, force: true })
}
