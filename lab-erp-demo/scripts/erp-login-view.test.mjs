import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const erpLoginViewPath = resolve('src/views/ErpLoginView.vue')
const routerPath = resolve('src/router/index.js')

async function run() {
  const [viewSource, routerSource] = await Promise.all([
    readFile(erpLoginViewPath, 'utf8'),
    readFile(routerPath, 'utf8')
  ])

  assert.match(viewSource, /import\s+AuthFlipCardShell\s+from\s+['"]@\/components\/auth\/AuthFlipCardShell\.vue['"]/, 'ERP login view should import the shared auth flip-card shell')
  assert.match(viewSource, /import\s+AuthCredentialsForm\s+from\s+['"]@\/components\/auth\/AuthCredentialsForm\.vue['"]/, 'ERP login view should import the shared auth credentials form')
  assert.match(viewSource, /import\s+AuthRegistrationForm\s+from\s+['"]@\/components\/auth\/AuthRegistrationForm\.vue['"]/, 'ERP login view should import the shared auth registration form')
  assert.match(viewSource, /<AuthFlipCardShell\b[^>]*:flipped="isRegisterMode"/, 'ERP login view should render the shared flip-card shell')
  assert.match(viewSource, /<template\s+#login>/, 'ERP login view should provide a login face')
  assert.match(viewSource, /<template\s+#register>/, 'ERP login view should provide a register face')
  assert.match(viewSource, /<AuthCredentialsForm[\s\S]*title="ERP 系统登录"/, 'ERP login face should use ERP-specific login copy through the shared credentials form')
  assert.match(viewSource, /<AuthCredentialsForm[\s\S]*subtitle="进入企业资源计划协作台"/, 'ERP login face should describe the ERP destination through the shared credentials form')
  assert.match(viewSource, /<AuthCredentialsForm[\s\S]*submit-text="登录 ERP 系统"/, 'ERP login action should use ERP wording through the shared credentials form')
  assert.match(viewSource, /<AuthCredentialsForm[\s\S]*:login-error="loginError"/, 'ERP login face should render backend login errors directly')
  assert.match(viewSource, /<AuthRegistrationForm[\s\S]*mode="erp"/, 'ERP register face should configure the shared registration form for ERP mode')
  assert.match(viewSource, /<AuthRegistrationForm[\s\S]*title="ERP 系统注册"/, 'ERP register face should use ERP-specific register copy through the shared registration form')
  assert.match(viewSource, /<AuthRegistrationForm[\s\S]*subtitle="创建 ERP 团队协作账号"/, 'ERP register face should describe ERP registration through the shared registration form')
  assert.match(viewSource, /<AuthRegistrationForm[\s\S]*role-label="ERP 角色"/, 'ERP register face should provide an ERP role selector')
  assert.match(viewSource, /<AuthRegistrationForm[\s\S]*:role="registerForm\.role"/, 'ERP register face should bind ERP role state')
  assert.match(viewSource, /<AuthRegistrationForm[\s\S]*:email="registerForm\.email"/, 'ERP register face should bind ERP email state')
  assert.match(viewSource, /<AuthRegistrationForm[\s\S]*submit-text="注册 ERP 账号"/, 'ERP register action should use ERP wording through the shared registration form')
  assert.match(viewSource, /<AuthRegistrationForm[\s\S]*:submit-error="registerError"/, 'ERP register face should render backend register errors directly')
  assert.match(viewSource, /userStore\.loginErp\(/, 'ERP login view should call the ERP-scoped login action')
  assert.match(viewSource, /userStore\.registerErp\(/, 'ERP login view should call the ERP-scoped register action')
  assert.match(viewSource, /await router\.push\(getErpLandingRoute\(userStore\.erpUserInfo\?\.role\)\)/, 'ERP login success should route by ERP role landing rule')

  assert.match(routerSource, /import\s+ErpLoginView\s+from\s+['"]\.\.\/views\/ErpLoginView\.vue['"]/, 'router should import the ERP login view')
  assert.match(routerSource, /path:\s*['"]\/erp-login['"][\s\S]*name:\s*['"]erp-login['"][\s\S]*component:\s*ErpLoginView/, 'router should register the /erp-login route')

  console.log('[erp-login-view] PASS')
}

run().catch((error) => {
  console.error('[erp-login-view] FAIL:', error.message)
  process.exitCode = 1
})
