import { test, expect } from '@playwright/test'

test('home page loads and shows EducaGame title', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /EducaGame/i })).toBeVisible()
})

test('can navigate to theme admin', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: /Admin: temas/i }).click()
  await expect(page).toHaveURL(/\/admin\/themes/)
})

test('can navigate to stats admin', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: /Admin: estatísticas/i }).click()
  await expect(page).toHaveURL(/\/admin\/stats/)
})
