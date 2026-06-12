export const DOMAIN_FINANCE = 'FINANCE'
export const DOMAIN_ERP = 'ERP'

const ERP_ROUTE_PREFIXES = ['/manager', '/workspace']
const ERP_EXACT_PATHS = ['/admin/users/create', '/admin/wage-management', '/admin/server-management']
const ERP_DASHBOARD_ROLES = new Set(['ADMIN', 'MANAGER', 'BUSINESS'])

const normalizeDomain = value => {
  const normalizedValue = String(value || '').trim().toUpperCase()

  return normalizedValue || null
}

const normalizeRole = value => String(value || '').trim().toUpperCase()

const hasToken = value => Boolean(String(value || '').trim())

const getSessionDomain = ({ token, userInfo, fallbackDomain }) => {
  if (!hasToken(token)) {
    return null
  }

  return normalizeDomain(userInfo?.accountDomain) || normalizeDomain(fallbackDomain)
}

const getSessionScopeOrder = activeAuthScope => {
  const normalizedScope = normalizeDomain(activeAuthScope)

  if (normalizedScope === DOMAIN_ERP) {
    return [DOMAIN_ERP, DOMAIN_FINANCE]
  }

  if (normalizedScope === DOMAIN_FINANCE) {
    return [DOMAIN_FINANCE, DOMAIN_ERP]
  }

  return [DOMAIN_FINANCE, DOMAIN_ERP]
}

export const resolveActiveSession = ({
  activeAuthScope,
  financeToken,
  financeUserInfo,
  erpToken,
  erpUserInfo
} = {}) => {
  const sessionsByDomain = {
    [DOMAIN_FINANCE]: {
      token: financeToken,
      userInfo: financeUserInfo,
      accountDomain: getSessionDomain({ token: financeToken, userInfo: financeUserInfo, fallbackDomain: DOMAIN_FINANCE })
    },
    [DOMAIN_ERP]: {
      token: erpToken,
      userInfo: erpUserInfo,
      accountDomain: getSessionDomain({ token: erpToken, userInfo: erpUserInfo, fallbackDomain: DOMAIN_ERP })
    }
  }

  for (const domain of getSessionScopeOrder(activeAuthScope)) {
    const session = sessionsByDomain[domain]

    if (session.accountDomain) {
      return {
        token: session.token,
        userInfo: session.userInfo,
        accountDomain: session.accountDomain,
        role: normalizeRole(session.userInfo?.role)
      }
    }
  }

  return {
    token: '',
    userInfo: null,
    accountDomain: null,
    role: ''
  }
}

export const resolveRouteDomain = to => {
  const matchedRoute = to?.matched?.find(routeRecord => routeRecord?.meta?.routeDomain)

  if (matchedRoute) {
    return normalizeDomain(matchedRoute.meta.routeDomain)
  }

  const currentPath = String(to?.path || '')

  if (currentPath.startsWith('/finance')) {
    return DOMAIN_FINANCE
  }

  if (ERP_ROUTE_PREFIXES.some(prefix => currentPath.startsWith(prefix)) || ERP_EXACT_PATHS.includes(currentPath)) {
    return DOMAIN_ERP
  }

  return null
}

export const canAccessRouteDomain = ({ accountDomain, to }) => {
  const normalizedAccountDomain = normalizeDomain(accountDomain)
  const routeDomain = resolveRouteDomain(to)

  if (!routeDomain) {
    return true
  }

  if (!normalizedAccountDomain) {
    return false
  }

  return normalizedAccountDomain === routeDomain
}

export const getErpLandingRoute = role => {
  return ERP_DASHBOARD_ROLES.has(normalizeRole(role)) ? '/manager/dashboard' : '/workspace'
}

export const getAnonymousProtectedRedirect = to => {
  const routeDomain = resolveRouteDomain(to)

  if (routeDomain === DOMAIN_ERP) {
    return '/erp-login'
  }

  if (routeDomain === DOMAIN_FINANCE) {
    return '/login'
  }

  return '/login'
}

export const getAuthenticatedEntryRedirect = ({
  to,
  activeAuthScope,
  financeToken,
  financeUserInfo,
  erpToken,
  erpUserInfo
} = {}) => {
  const targetPath = String(to?.path || '')

  if (targetPath !== '/erp-login' && targetPath !== '/login') {
    return null
  }

  const activeSession = resolveActiveSession({
    activeAuthScope,
    financeToken,
    financeUserInfo,
    erpToken,
    erpUserInfo
  })

  if (activeSession.accountDomain === DOMAIN_FINANCE) {
    return '/finance/classic'
  }

  if (activeSession.accountDomain === DOMAIN_ERP) {
    return getErpLandingRoute(activeSession.role)
  }

  return null
}

export const getDefaultRouteForDomain = accountDomain => {
  const normalizedAccountDomain = normalizeDomain(accountDomain)

  if (normalizedAccountDomain === DOMAIN_FINANCE) {
    return '/finance/classic'
  }

  if (normalizedAccountDomain === DOMAIN_ERP) {
    return getErpLandingRoute()
  }

  return '/login'
}
