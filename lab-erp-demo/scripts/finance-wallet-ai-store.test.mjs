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

const tempRoot = await mkdtemp(path.join(projectRoot, '.tmp-finance-wallet-ai-store-'))

try {
  const [walletStoreSource, aiStoreSource, adaptersSource, enumsSource] = await Promise.all([
    readFile(path.join(projectRoot, 'src/stores/financeWalletStore.js'), 'utf8'),
    readFile(path.join(projectRoot, 'src/stores/financeAiStore.js'), 'utf8'),
    readFile(path.join(projectRoot, 'src/utils/financeAdapters.js'), 'utf8'),
    readFile(path.join(projectRoot, 'src/utils/financeEnums.js'), 'utf8')
  ])

  assert.equal(
    aiStoreSource.includes('context_mode'),
    false,
    'finance AI store should not handle context_mode aliases directly'
  )

  await writeTempModule('utils/financeEnums.js', enumsSource, tempRoot)
  await writeTempModule(
    'utils/financeAdapters.js',
    adaptersSource.replace("@/utils/financeEnums", './financeEnums.js'),
    tempRoot
  )

  await writeTempModule(
    'api/finance/wallets.js',
    `let handlers = {
      getFinanceWallets: async () => ({ success: true, data: { wallets: [] } }),
      getFinanceTransactions: async () => ({ success: true, data: { items: [] } }),
      saveBankBalance: async () => ({ success: true, data: null })
    }

    export const __setHandlers = nextHandlers => {
      handlers = { ...handlers, ...nextHandlers }
    }

    export const getFinanceWallets = params => handlers.getFinanceWallets(params)
    export const getFinanceTransactions = params => handlers.getFinanceTransactions(params)
    export const saveBankBalance = payload => handlers.saveBankBalance(payload)
    `,
    tempRoot
  )

  await writeTempModule(
    'api/finance/ai.js',
    `let handlers = {
      queryFinanceRag: async () => ({ success: true, data: [] }),
      rebuildFinanceRag: async () => ({ success: true, data: null }),
      chatWithFinanceAi: async () => ({ success: true, data: null })
    }

    export const __setHandlers = nextHandlers => {
      handlers = { ...handlers, ...nextHandlers }
    }

    export const queryFinanceRag = payload => handlers.queryFinanceRag(payload)
    export const rebuildFinanceRag = payload => handlers.rebuildFinanceRag(payload)
    export const chatWithFinanceAi = payload => handlers.chatWithFinanceAi(payload)
    `,
    tempRoot
  )

  const walletStorePath = await writeTempModule(
    'stores/financeWalletStore.js',
    walletStoreSource
      .replace("@/api/finance/wallets", '../api/finance/wallets.js')
      .replace("@/utils/financeAdapters", '../utils/financeAdapters.js'),
    tempRoot
  )

  const aiStorePath = await writeTempModule(
    'stores/financeAiStore.js',
    aiStoreSource
      .replace("@/api/finance/ai", '../api/finance/ai.js')
      .replace("@/utils/financeAdapters", '../utils/financeAdapters.js'),
    tempRoot
  )

  const cacheToken = Date.now()
  const [{ __setHandlers: setWalletHandlers }, { __setHandlers: setAiHandlers }, { useFinanceWalletStore }, { useFinanceAiStore }] = await Promise.all([
    import(pathToFileURL(path.join(tempRoot, 'api/finance/wallets.js')).href),
    import(pathToFileURL(path.join(tempRoot, 'api/finance/ai.js')).href),
    import(`${pathToFileURL(walletStorePath).href}?t=${cacheToken}`),
    import(`${pathToFileURL(aiStorePath).href}?t=${cacheToken}`)
  ])

  setActivePinia(createPinia())

  const walletStore = useFinanceWalletStore()
  setWalletHandlers({
    getFinanceTransactions: async () => ({
      success: true,
      message: 'finance transactions loaded',
      data: {
        items: [{ source_table: 'DIVIDEND_SHEET', balance_after: 50 }],
        totalCount: 1
      }
    }),
    saveBankBalance: async payload => ({
      success: true,
      message: 'bank balance snapshot recorded',
      data: {
        id: '88',
        message: `${payload.operator}:${payload.balance}`
      }
    })
  })

  await walletStore.loadTransactions({
    source_table: 'DIVIDEND_SHEET',
    user_id: 'U-1'
  })

  assert.equal(walletStore.transactions.length, 1, 'wallet store should normalize transaction list payloads')
  assert.equal(walletStore.transactions[0].sourceTable, 'DIVIDEND_SHEET', 'wallet store should expose camelCase transaction aliases')

  await walletStore.submitBankBalance({ balance: 123.45, operator: 'auditor', remark: 'close' })
  assert.equal(walletStore.bankMutation.success, true, 'wallet store should keep successful mutation state')
  assert.equal(walletStore.bankMutation.referenceId, '88', 'wallet store should derive bank mutation reference ids')

  const aiStore = useFinanceAiStore()
  setAiHandlers({
    chatWithFinanceAi: async () => ({
      success: true,
      data: {
        context_mode: 'rag',
        history: [{ role: 'assistant', created_at: '2026-03-12T09:00:00Z' }]
      }
    })
  })

  await aiStore.sendMessage({ prompt: 'hello' })

  assert.equal(aiStore.contextMode, 'rag', 'AI store should receive normalized context mode from adapters')
  assert.equal(aiStore.conversation[0].createdAt, '2026-03-12T09:00:00Z', 'AI store should receive normalized history aliases from adapters')

  console.log('finance wallet and AI store contract tests passed')
} finally {
  await rm(tempRoot, { recursive: true, force: true })
}
