import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const loginViewPath = resolve('src/views/LoginView.vue')
const credentialsFormPath = resolve('src/components/auth/AuthCredentialsForm.vue')
const registrationFormPath = resolve('src/components/auth/AuthRegistrationForm.vue')

async function run() {
  const [source, credentialsSource, registrationSource] = await Promise.all([
    readFile(loginViewPath, 'utf8'),
    readFile(credentialsFormPath, 'utf8'),
    readFile(registrationFormPath, 'utf8')
  ])

  assert.match(credentialsSource, /default:\s*'财务系统登录'/, 'login heading should be finance-specific Chinese copy')
  assert.match(credentialsSource, /default:\s*'请输入财务账号信息'/, 'login subtitle should describe finance login destination')
  assert.match(registrationSource, /default:\s*'财务系统注册'/, 'register heading should be finance-specific Chinese copy')
  assert.match(registrationSource, /default:\s*'创建财务系统账号'/, 'register subtitle should explain finance-only registration scope')
  assert.match(registrationSource, /default:\s*'注册财务账号'/, 'register button should use finance-specific wording')
  assert.match(registrationSource, /:aria-label="passwordVisible \? '隐藏密码' : '显示密码'"/, 'register password toggle should expose an accessible label')
  assert.match(source, /import\s+AuthFlipCardShell\s+from\s+['"]@\/components\/auth\/AuthFlipCardShell\.vue['"]/, 'login view should import the shared auth flip-card shell')
  assert.match(source, /<AuthFlipCardShell\b/, 'login view should render the shared auth flip-card shell')
  assert.doesNotMatch(source, /\.flip-wrap\s*\{/, 'flip-card shell styles should live outside LoginView')
  assert.doesNotMatch(source, /\.form-shell\s*\{/, 'form shell styles should live outside LoginView')
  assert.doesNotMatch(source, /:deep\(\.pill-input/, 'shared input styles should live outside LoginView')
  assert.doesNotMatch(source, /\.toggle-eye\s*\{/, 'shared toggle button styles should live outside LoginView')
  assert.doesNotMatch(source, /\.pill-btn\s*\{/, 'shared button styles should live outside LoginView')
  assert.doesNotMatch(source, /\.register-link\s*\{/, 'shared auth link styles should live outside LoginView')

  assert.doesNotMatch(source, /entry-switcher/, 'finance login should not show an ERP entry switcher')
  assert.doesNotMatch(source, /loginTarget/, 'finance login should not keep entry toggle state')
  assert.doesNotMatch(source, /新 Vue 系统入口/, 'finance login should not mention non-finance entry copy')
  assert.doesNotMatch(source, /resolveModernEntryRoute/, 'finance login should not route to modern non-finance entries')

  assert.match(credentialsSource, /v-if="loginError"/, 'login view should render backend login errors inline')
  assert.match(credentialsSource, /class="login-error"/, 'login view should include a dedicated inline error block')
  assert.match(source, /loginError\.value\s*=\s*message/, 'login errors should keep backend messages for direct rendering')
  assert.match(source, /await router\.push\('\/finance\/overview'\)/, 'finance login success should route to the finance overview')

  console.log('[finance-login-view] PASS')
}

run().catch((error) => {
  console.error('[finance-login-view] FAIL:', error.message)
  process.exitCode = 1
})
