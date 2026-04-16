import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

const FINANCE_TOKEN_KEY = 'finance_token'
const FINANCE_USER_INFO_KEY = 'finance_userInfo'
const ERP_TOKEN_KEY = 'erp_token'
const ERP_USER_INFO_KEY = 'erp_userInfo'
const ACTIVE_AUTH_SCOPE_KEY = 'active_auth_scope'
const DOMAIN_FINANCE = 'FINANCE'
const DOMAIN_ERP = 'ERP'

const getErrorMessage = (error, fallbackMessage) => {
    return error.response?.data?.message || error.response?.data || error.message || fallbackMessage
}

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

const resolveActiveSession = ({
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

export const useUserStore = defineStore('user', () => {
    // 状态
    const token = ref(localStorage.getItem(FINANCE_TOKEN_KEY) || '')
    const userInfo = ref(JSON.parse(localStorage.getItem(FINANCE_USER_INFO_KEY) || '{}'))
    const errorMessage = ref('')
    const erpToken = ref(localStorage.getItem(ERP_TOKEN_KEY) || '')
    const erpUserInfo = ref(JSON.parse(localStorage.getItem(ERP_USER_INFO_KEY) || '{}'))
    const erpErrorMessage = ref('')
    const activeAuthScope = ref(localStorage.getItem(ACTIVE_AUTH_SCOPE_KEY) || '')
    const userBadges = ref([])

    const activeSession = computed(() => resolveActiveSession({
        activeAuthScope: activeAuthScope.value,
        financeToken: token.value,
        financeUserInfo: userInfo.value,
        erpToken: erpToken.value,
        erpUserInfo: erpUserInfo.value
    }))

    // Getter: 登录判断
    const isLoggedIn = computed(() => Boolean(activeSession.value.accountDomain))
    const isErpLoggedIn = computed(() => activeSession.value.accountDomain === DOMAIN_ERP)
    const activeUserInfo = computed(() => activeSession.value.userInfo || {})
    const mustChangePassword = computed(() => Boolean(activeUserInfo.value.mustChangePassword))

    // Getter: 权限判断
    const isManager = computed(() => {
        const role = String(activeUserInfo.value.role || '').toUpperCase()
        return role === 'ADMIN' || role === 'MANAGER' || role === 'BUSINESS'
    })

    const hasWrnBadge = computed(() => userBadges.value.some(b => b.badgeName === 'wrn'))

    // 🚩✍ Getter: 团队队长判断
    const isTeamLeader = computed(() => {
        const role = normalizeRole(activeUserInfo.value?.role)
        const leaderRoles = ['DEV', 'ALGORITHM', 'DATA', 'RESEARCH']
        return leaderRoles.includes(role)
    })
    // ✅ 新增：同步更新头像的方法
    const updateAvatar = (newAvatarUrl) => {
        if (isErpLoggedIn.value && erpUserInfo.value) {
            erpUserInfo.value.avatar = newAvatarUrl
            localStorage.setItem(ERP_USER_INFO_KEY, JSON.stringify(erpUserInfo.value))
            return
        }

        if (userInfo.value) {
            userInfo.value.avatar = newAvatarUrl
            localStorage.setItem(FINANCE_USER_INFO_KEY, JSON.stringify(userInfo.value))
        }
    }

    const updateActiveUserInfo = (patch) => {
        const currentPatch = typeof patch === 'function'
            ? patch({ ...(activeUserInfo.value || {}) })
            : patch

        if (!currentPatch || typeof currentPatch !== 'object') {
            return
        }

        if (isErpLoggedIn.value && erpUserInfo.value) {
            erpUserInfo.value = { ...(erpUserInfo.value || {}), ...currentPatch }
            localStorage.setItem(ERP_USER_INFO_KEY, JSON.stringify(erpUserInfo.value))
            return
        }

        userInfo.value = { ...(userInfo.value || {}), ...currentPatch }
        localStorage.setItem(FINANCE_USER_INFO_KEY, JSON.stringify(userInfo.value))
    }

    const loginByDomain = async (loginForm, options) => {
        const { domain, tokenState, userInfoState, errorState, tokenKey, userInfoKey, activeScope } = options

        errorState.value = ''

        try {
            const res = await request.post('/api/auth/login', {
                ...loginForm,
                domain
            })
            const accessToken = res.token

            if (!accessToken) throw new Error('登录失败：未获取到 Token')

            tokenState.value = accessToken
            localStorage.setItem(tokenKey, accessToken)
            localStorage.setItem(ACTIVE_AUTH_SCOPE_KEY, activeScope)
            activeAuthScope.value = activeScope

            const user = await request.get('/api/auth/me')

            userInfoState.value = user
            localStorage.setItem(userInfoKey, JSON.stringify(user))

            await fetchBadges()

            return true
        } catch (error) {
            errorState.value = getErrorMessage(error, '登录失败')
            throw error
        }
    }

    const fetchBadges = async () => {
        const uid = activeUserInfo.value?.userId
        if (!uid) return
        try {
            const res = await request.get(`/api/user-badges/${uid}`)
            userBadges.value = Array.isArray(res) ? res : []
        } catch {
            userBadges.value = []
        }
    }

    const registerByDomain = async (registerForm, options) => {
        const { domain, errorState } = options

        errorState.value = ''

        try {
            await request.post('/api/auth/register', {
                ...(domain === 'FINANCE' ? { role: 'RESEARCH' } : {}),
                ...registerForm,
                domain
            })
        } catch (error) {
            errorState.value = getErrorMessage(error, '注册失败')
            throw error
        }
    }

    const logoutByDomain = ({ tokenState, userInfoState, errorState, tokenKey, userInfoKey, activeScope }) => {
        tokenState.value = ''
        userInfoState.value = {}
        errorState.value = ''
        localStorage.removeItem(tokenKey)
        localStorage.removeItem(userInfoKey)
        if (localStorage.getItem(ACTIVE_AUTH_SCOPE_KEY) === activeScope) {
            localStorage.removeItem(ACTIVE_AUTH_SCOPE_KEY)
            activeAuthScope.value = ''
        }
    }

    // 登录 Action
    const login = async (loginForm) => {
        return loginByDomain(loginForm, {
            domain: 'FINANCE',
            tokenState: token,
            userInfoState: userInfo,
            errorState: errorMessage,
            tokenKey: FINANCE_TOKEN_KEY,
            userInfoKey: FINANCE_USER_INFO_KEY,
            activeScope: 'FINANCE'
        })
    }

    const register = async (registerForm) => {
        return registerByDomain(registerForm, {
            domain: 'FINANCE',
            errorState: errorMessage
        })
    }

    const loginErp = async (loginForm) => {
        return loginByDomain(loginForm, {
            domain: 'ERP',
            tokenState: erpToken,
            userInfoState: erpUserInfo,
            errorState: erpErrorMessage,
            tokenKey: ERP_TOKEN_KEY,
            userInfoKey: ERP_USER_INFO_KEY,
            activeScope: 'ERP'
        })
    }

    const registerErp = async (registerForm) => {
        return registerByDomain(registerForm, {
            domain: 'ERP',
            errorState: erpErrorMessage
        })
    }

    const logout = () => {
        logoutByDomain({
            tokenState: token,
            userInfoState: userInfo,
            errorState: errorMessage,
            tokenKey: FINANCE_TOKEN_KEY,
            userInfoKey: FINANCE_USER_INFO_KEY,
            activeScope: 'FINANCE'
        })
    }

    const logoutErp = () => {
        logoutByDomain({
            tokenState: erpToken,
            userInfoState: erpUserInfo,
            errorState: erpErrorMessage,
            tokenKey: ERP_TOKEN_KEY,
            userInfoKey: ERP_USER_INFO_KEY,
            activeScope: 'ERP'
        })
    }

    const changePassword = async payload => {
        try {
            const response = await request.post('/api/auth/change-password', payload)
            updateActiveUserInfo({ mustChangePassword: false })
            return response
        } catch (error) {
            const targetError = isErpLoggedIn.value ? erpErrorMessage : errorMessage
            targetError.value = getErrorMessage(error, '密码修改失败')
            throw error
        }
    }

    const sendVerificationCode = async email => {
        try {
            const response = await request.post('/api/auth/password/send-code', { email })
            return response
        } catch (error) {
            throw error
        }
    }

    const verifyCode = async (email, code) => {
        try {
            const response = await request.post('/api/auth/password/verify-code', { email, code })
            return response
        } catch (error) {
            throw error
        }
    }

    const resetPassword = async (email, code, newPassword) => {
        try {
            const response = await request.post('/api/auth/password/reset', { email, code, newPassword })
            return response
        } catch (error) {
            throw error
        }
    }

    return {
        token,
        userInfo,
        errorMessage,
        erpToken,
        erpUserInfo,
        erpErrorMessage,
        activeAuthScope,
        activeSession,
        activeUserInfo,
        mustChangePassword,
        isLoggedIn,
        isErpLoggedIn,
        isManager,
        hasWrnBadge,
        fetchBadges,
        login,
        register,
        logout,
        loginErp,
        registerErp,
        logoutErp,
        updateAvatar,
        updateActiveUserInfo,
        changePassword,
        sendVerificationCode,
        verifyCode,
        resetPassword
    }
})
