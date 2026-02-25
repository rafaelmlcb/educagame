import { useEffect, useRef, useState, useCallback } from 'react'
import { getWsUrl } from '@/api/client'
import type { WsOutbound } from '@/types/game'
import { log, newRequestId } from '@/api/logger'

export type WsStatus = 'connecting' | 'open' | 'closed' | 'error'

export interface UseWebSocketOptions {
  path?: string
  onMessage?: (msg: WsOutbound) => void
  onOpen?: () => void
  onClose?: () => void
  autoConnect?: boolean
}

export function useWebSocket(options: UseWebSocketOptions = {}) {
  const { path = '/game', onMessage, onOpen, onClose, autoConnect = true } = options
  const [status, setStatus] = useState<WsStatus>('closed')
  const wsRef = useRef<WebSocket | null>(null)
  const attemptIdRef = useRef<string | null>(null)
  const onMessageRef = useRef(onMessage)
  const onOpenRef = useRef(onOpen)
  const onCloseRef = useRef(onClose)
  onMessageRef.current = onMessage
  onOpenRef.current = onOpen
  onCloseRef.current = onClose

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return
    const url = getWsUrl(path)
    const attemptId = newRequestId()
    attemptIdRef.current = attemptId
    log.debug('ws:connect', { attemptId, url })
    setStatus('connecting')
    const ws = new WebSocket(url)
    wsRef.current = ws
    ws.onopen = () => {
      log.debug('ws:open', { attemptId })
      setStatus('open')
      onOpenRef.current?.()
    }
    ws.onclose = (event) => {
      log.debug('ws:close', { attemptId, code: event.code, reason: event.reason, wasClean: event.wasClean })
      setStatus('closed')
      wsRef.current = null
      onCloseRef.current?.()
    }
    ws.onerror = () => {
      log.warn('ws:error', { attemptId })
      setStatus('error')
    }
    ws.onmessage = (event) => {
      try {
        const data: WsOutbound = JSON.parse(event.data)
        log.debug('ws:recv', { attemptId, type: data?.type })
        onMessageRef.current?.(data)
      } catch {
        // ignore
      }
    }
  }, [path])

  const disconnect = useCallback(() => {
    if (wsRef.current) {
      log.debug('ws:disconnect', { attemptId: attemptIdRef.current })
      wsRef.current.close()
      wsRef.current = null
    }
    setStatus('closed')
  }, [])

  const send = useCallback((payload: object) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      const t = (payload as { type?: string })?.type
      log.debug('ws:send', { attemptId: attemptIdRef.current, type: t })
      wsRef.current.send(JSON.stringify(payload))
    } else {
      const t = (payload as { type?: string })?.type
      log.debug('ws:drop', { attemptId: attemptIdRef.current, type: t })
    }
  }, [])

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | null = null
    if (autoConnect) {
      timer = setTimeout(() => {
        connect()
      }, 0)
    }
    return () => {
      if (timer) clearTimeout(timer)
      disconnect()
    }
  }, [autoConnect, connect, disconnect])

  return { status, connect, disconnect, send }
}
