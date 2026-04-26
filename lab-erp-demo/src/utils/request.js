import axios from 'axios'

const ACTIVE_AUTH_SCOPE_KEY = 'active_auth_scope'
const FINANCE_TOKEN_KEY = 'finance_token'
const ERP_TOKEN_KEY = 'erp_token'

const getActiveToken = () => {
    const scope = localStorage.getItem(ACTIVE_AUTH_SCOPE_KEY)
    if (scope === 'ERP') {
        return localStorage.getItem(ERP_TOKEN_KEY) || ''
    }
    if (scope === 'FINANCE') {
        return localStorage.getItem(FINANCE_TOKEN_KEY) || ''
    }
    return localStorage.getItem(FINANCE_TOKEN_KEY) || localStorage.getItem(ERP_TOKEN_KEY) || localStorage.getItem('token') || ''
}

// 1. 创建 axios 实例
const service = axios.create({
    baseURL: '',

    timeout: 120000
})

// 2. 请求拦截器 (保持你原来的)
service.interceptors.request.use(
    config => {
        const requestUrl = String(config.url || '')
        const isAuthRequest = requestUrl.startsWith('/api/auth/login') || requestUrl.startsWith('/api/auth/register')

        if (isAuthRequest && config.headers?.Authorization) {
            delete config.headers.Authorization
        }

        if (isAuthRequest) {
            return config
        }

        const token = getActiveToken()
        if (token) {
            config.headers['Authorization'] = 'Bearer ' + token
        }
        return config
    },
    error => {
        console.log(error)
        return Promise.reject(error)
    }
)

// 3. 🟢 新增：响应拦截器 (这是你缺失的关键代码！)
service.interceptors.response.use(
    response => {
        // 自动脱壳：直接返回后端给的真实数据，剥离 axios 的外壳
        return response.data
    },
    error => {
        console.error('请求出错:', error)
        if (error.response) {
            const status = error.response.status
            const data = error.response.data
            if (data && data.message && typeof data.message === 'string') {
                error.message = data.message
            } else if (status === 403) {
                error.message = '当前账号无此权限'
            } else if (status === 503) {
                error.message = '服务暂不可用，请稍后重试'
            }
        }
        return Promise.reject(error)
    }
)

export default service
