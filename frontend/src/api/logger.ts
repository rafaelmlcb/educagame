export type LogLevel = 'debug' | 'info' | 'warn' | 'error'

function shouldLog(level: LogLevel): boolean {
  const configured = (import.meta.env.VITE_LOG_LEVEL as LogLevel | undefined) ?? (import.meta.env.DEV ? 'debug' : 'info')
  const order: Record<LogLevel, number> = { debug: 10, info: 20, warn: 30, error: 40 }
  return order[level] >= order[configured]
}

export const log = {
  debug: (msg: unknown, meta?: Record<string, unknown>) => { if (shouldLog('debug')) console.debug(JSON.stringify({ ts: new Date().toISOString(), level: 'debug', msg, meta })) },
  info: (msg: unknown, meta?: Record<string, unknown>) => { if (shouldLog('info')) console.info(JSON.stringify({ ts: new Date().toISOString(), level: 'info', msg, meta })) },
  warn: (msg: unknown, meta?: Record<string, unknown>) => { if (shouldLog('warn')) console.warn(JSON.stringify({ ts: new Date().toISOString(), level: 'warn', msg, meta })) },
  error: (msg: unknown, meta?: Record<string, unknown>) => { if (shouldLog('error')) console.error(JSON.stringify({ ts: new Date().toISOString(), level: 'error', msg, meta })) },
}

export function newRequestId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

let _currentRequestId: string | undefined
export function setCurrentRequestId(id: string) { _currentRequestId = id }
export function getCurrentRequestId(): string | undefined { return _currentRequestId }

export default log
