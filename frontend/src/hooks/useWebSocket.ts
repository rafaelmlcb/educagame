import { useEffect, useRef, useState, useCallback } from 'react'
import { getWsUrl } from '@/api/client'
import type { WsOutbound } from '@/types/game'

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
  const onMessageRef = useRef(onMessage)
  const onOpenRef = useRef(onOpen)
  const onCloseRef = useRef(onClose)
  onMessageRef.current = onMessage
  onOpenRef.current = onOpen
  onCloseRef.current = onClose

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return
    const url = getWsUrl(path)
    console.debug('[ws] connect', url)
    setStatus('connecting')
    const ws = new WebSocket(url)
    wsRef.current = ws
    ws.onopen = () => {
      console.debug('[ws] open')
      setStatus('open')
      onOpenRef.current?.()
    }
    ws.onclose = () => {
      console.debug('[ws] close')
      setStatus('closed')
      wsRef.current = null
      onCloseRef.current?.()
    }
    ws.onerror = () => {
      console.debug('[ws] error')
      setStatus('error')
    }
    ws.onmessage = (event) => {
      try {
        const data: WsOutbound = JSON.parse(event.data)
        console.debug('[ws] recv', data?.type)
        onMessageRef.current?.(data)
      } catch {
        // ignore
      }
    }
  }, [path])

  const disconnect = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.close()
      wsRef.current = null
    }
    setStatus('closed')
  }, [])

  const send = useCallback((payload: object) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      const t = (payload as { type?: string })?.type
      console.debug('[ws] send', t ?? payload)
      wsRef.current.send(JSON.stringify(payload))
    } else {
      const t = (payload as { type?: string })?.type
      console.debug('[ws] drop(send while not open)', t ?? payload)
    }
  }, [])

  useEffect(() => {
    if (autoConnect) connect()
    return () => disconnect()
  }, [autoConnect, connect, disconnect])

  return { status, connect, disconnect, send }
}
