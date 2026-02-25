import type { FullConfig } from '@playwright/test'

declare global {
  // eslint-disable-next-line no-var
  var __BACKEND_PROCESS__: { kill: (signal?: string) => void } | undefined
}

export default async function globalTeardown(_config: FullConfig) {
  const p = globalThis.__BACKEND_PROCESS__
  if (p) {
    try {
      p.kill('SIGTERM')
    } catch {
      // ignore
    }
    globalThis.__BACKEND_PROCESS__ = undefined
  }
}
