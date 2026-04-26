import assert from 'node:assert/strict'

const baseUrl = process.env.TEST_BASE_URL || 'http://127.0.0.1:8101'
const username = process.env.TEST_LOGIN_USERNAME || 'admin'
const password = process.env.TEST_LOGIN_PASSWORD || 'admin123456'

async function requestJson(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  })

  const rawBody = await response.text()
  let body = null

  if (rawBody) {
    try {
      body = JSON.parse(rawBody)
    } catch {
      body = rawBody
    }
  }

  return { response, body }
}

async function runLoginFlowTest() {
  console.log(`[login-flow] baseUrl=${baseUrl}`)
  console.log(`[login-flow] username=${username}`)

  const loginPayload = { username, password }

  const { response: loginResponse, body: loginBody } = await requestJson('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(loginPayload)
  })

  assert.equal(
    loginResponse.status,
    200,
    `Expected login status 200, got ${loginResponse.status}. Body: ${JSON.stringify(loginBody)}`
  )
  assert.ok(loginBody && typeof loginBody === 'object', 'Login response must be a JSON object')
  assert.ok(typeof loginBody.token === 'string' && loginBody.token.length > 20, 'JWT token missing or too short')

  const jwtRegex = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/
  assert.match(loginBody.token, jwtRegex, 'Returned token does not look like a JWT')

  const { response: meResponse, body: meBody } = await requestJson('/api/auth/me', {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${loginBody.token}`
    }
  })

  assert.equal(
    meResponse.status,
    200,
    `Expected /api/auth/me status 200, got ${meResponse.status}. Body: ${JSON.stringify(meBody)}`
  )
  assert.ok(meBody && typeof meBody === 'object', 'Protected route response must be a JSON object')
  assert.equal(meBody.username, username, 'Protected route returned unexpected username')
  assert.ok(typeof meBody.userId === 'string' && meBody.userId.length > 0, 'Protected route missing userId')

  console.log('[login-flow] PASS: login + protected route verification succeeded')
}

runLoginFlowTest().catch((error) => {
  console.error('[login-flow] FAIL:', error.message)
  process.exitCode = 1
})
