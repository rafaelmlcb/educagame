import axios from 'axios'
import { log, newRequestId, setCurrentRequestId } from './logger'

const isProd = import.meta.env.PROD
const apiUrl = import.meta.env.VITE_API_URL ?? (!isProd ? '' : (typeof window !== 'undefined' ? `${window.location.protocol}//${window.location.host}` : 'http://localhost:8080'))

export const api = axios.create({
  baseURL: apiUrl || undefined,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const cfg: any = config
  const headers = (cfg.headers ||= {})
  const requestId = (headers['X-Request-Id'] as string | undefined) ?? newRequestId()
  headers['X-Request-Id'] = requestId
  setCurrentRequestId(requestId)

  const method = (config.method ?? 'GET').toUpperCase()
  const url = config.url ?? ''
  log.debug('http:request', { requestId, method, url, baseURL: config.baseURL })
  return config
})

api.interceptors.response.use(
  (response) => {
    const requestId = (response.config as any).headers?.['X-Request-Id'] as string | undefined
    log.debug('http:response', { requestId, status: response.status, url: response.config.url })
    return response
  },
  (error) => {
    const cfg = (error?.config ?? {}) as any
    const requestId = cfg.headers?.['X-Request-Id'] as string | undefined
    const status = error?.response?.status
    const data = error?.response?.data
    log.error('http:error', { requestId, status, method: (cfg.method ?? 'GET').toUpperCase(), url: cfg.url, message: error?.message, data })
    throw error
  },
)

export function getWsUrl(path = '/game'): string {
  if (import.meta.env.VITE_WS_URL) {
    const base = import.meta.env.VITE_WS_URL.replace(/\/$/, '')
    return base + path
  }
  if (import.meta.env.VITE_API_URL) {
    const httpBase = import.meta.env.VITE_API_URL.replace(/\/$/, '')
    const wsBase = httpBase.replace(/^http:/, 'ws:').replace(/^https:/, 'wss:')
    return wsBase + path
  }
  if (typeof window === 'undefined') return `ws://localhost:8080${path}`
  if (import.meta.env.DEV) return `ws://localhost:8080${path}`
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  return `${protocol}//${host}${path}`
}

export { apiUrl }
