import axios from 'axios'

const isProd = import.meta.env.PROD
const apiUrl = import.meta.env.VITE_API_URL ?? (!isProd ? '' : (typeof window !== 'undefined' ? `${window.location.protocol}//${window.location.host}` : 'http://localhost:8080'))

export const api = axios.create({
  baseURL: apiUrl || undefined,
  headers: { 'Content-Type': 'application/json' },
})

export function getWsUrl(path = '/game'): string {
  if (import.meta.env.VITE_WS_URL) {
    const base = import.meta.env.VITE_WS_URL.replace(/\/$/, '')
    return base + path
  }
  if (typeof window === 'undefined') return `ws://localhost:8080${path}`
  const protocol = isProd ? 'wss:' : 'ws:'
  const host = window.location.host
  return `${protocol}//${host}${path}`
}

export { apiUrl }
