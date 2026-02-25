import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // Retries: increase locally and in CI to reduce transient flakiness.
  retries: process.env.CI ? 3 : 2,
  // Limit workers to keep backend resource usage stable during e2e runs.
  workers: process.env.CI ? undefined : 2,
  reporter: 'html',
  globalSetup: './e2e/global-setup',
  globalTeardown: './e2e/global-teardown',
  // Global expectations and timeouts to make tests more resilient.
  expect: { timeout: 60000 },
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    // Allow longer navigation time (backend startup or slow responses)
    navigationTimeout: 45000,
    // No hard action timeout (use expect timeouts instead)
    actionTimeout: 0,
    // Increase default timeout for slower CI instances
    timeout: 120000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run e2e:web',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
})
