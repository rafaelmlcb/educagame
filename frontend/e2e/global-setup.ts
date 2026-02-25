import type { FullConfig } from '@playwright/test'
import { spawn } from 'child_process'

declare global {
  // eslint-disable-next-line no-var
  var __BACKEND_PROCESS__:
    | {
        kill: (signal?: string) => void
      }
    | undefined
}

async function waitForHealth(url: string, timeoutMs: number) {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url)
      if (res.ok) return
    } catch {
      // ignore
    }
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error(`Backend did not become healthy within ${timeoutMs}ms: ${url}`)
}

async function isHealthy(url: string) {
  try {
    const res = await fetch(url)
    return res.ok
  } catch {
    return false
  }
}

export default async function globalSetup(_config: FullConfig) {
  const backendCwd = '/home/rafael/educagame/backend'
  const healthUrl = 'http://localhost:8080/q/health'

  // If backend is already running, reuse it (avoid port 8080 conflict)
  if (await isHealthy(healthUrl)) {
    globalThis.__BACKEND_PROCESS__ = undefined
    return
  }

  // Build backend (prod jar)
  await new Promise<void>((resolve, reject) => {
    const p = spawn('bash', ['-lc', 'mvn package -DskipTests'], { cwd: backendCwd, stdio: 'inherit' })
    p.on('exit', (code: number | null) => (code === 0 ? resolve() : reject(new Error(`mvn package failed: ${code}`))))
    p.on('error', reject)
  })

  // Start backend jar
  const child = spawn('bash', ['-lc', 'java -jar target/quarkus-app/quarkus-run.jar'], {
    cwd: backendCwd,
    stdio: 'inherit',
  })

  globalThis.__BACKEND_PROCESS__ = child

  await waitForHealth(healthUrl, 60_000)
}
