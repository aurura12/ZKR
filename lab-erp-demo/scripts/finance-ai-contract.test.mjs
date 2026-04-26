import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const projectRoot = path.resolve(__dirname, '..')

const readProjectFile = relativePath => readFile(path.join(projectRoot, relativePath), 'utf8')

const [aiApiSource, aiStoreSource, aiViewSource] = await Promise.all([
  readProjectFile('src/api/finance/ai.js'),
  readProjectFile('src/stores/financeAiStore.js'),
  readProjectFile('src/views/finance/FinanceAiChatView.vue')
])

assert.ok(!aiApiSource.includes('/api/ai/reset'), 'finance AI api must not call the unapproved /api/ai/reset contract')
assert.ok(!aiApiSource.includes('resetFinanceAi'), 'finance AI api must not export resetFinanceAi')
assert.ok(!aiStoreSource.includes('resetFinanceAi'), 'finance AI store must not import or invoke resetFinanceAi')
assert.ok(!aiStoreSource.includes('resetConversation'), 'finance AI store must not expose a resetConversation action')
assert.ok(!aiViewSource.toLowerCase().includes('reset'), 'finance AI chat placeholder copy must not advertise reset flows')

console.log('finance AI contract guard passed')
