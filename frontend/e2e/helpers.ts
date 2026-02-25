import { expect, type Page } from '@playwright/test'

export async function createRoomFromHome(page: Page, gameType: string) {
  await page.goto('/')

  const [resp] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/rooms') && r.request().method() === 'POST'),
    page.getByTestId(`create-room-${gameType}`).click(),
  ])

  expect(resp.status()).toBe(201)
  const data = await resp.json()
  expect(data).toHaveProperty('roomId')
  expect(data).toHaveProperty('gameType')
  expect(data.gameType).toBe(gameType)

  await expect(page).toHaveURL(/\/room\//)
  return data.roomId as string
}

export async function joinAsHost(page: Page, name?: string) {
  const playerName = name ?? `E2E_${Date.now()}`
  await page.getByTestId('player-name-input').fill(playerName)
  await page.getByTestId('join-game').click()
  await expect(page.getByTestId('game-room')).toBeVisible()
  // Wait for session STATE to arrive
  await expect(page.getByTestId('game-type')).not.toHaveText('—', { timeout: 15000 })
  return playerName
}

export async function startGame(page: Page) {
  await page.getByTestId('start-game').click()
  // wait for phase to move away from LOBBY (caption text contains phase)
  await expect(page.getByText(/·\s*LOBBY/)).not.toBeVisible({ timeout: 15000 })
}

export async function assertGameType(page: Page, gameType: string) {
  await expect(page.getByTestId('game-type')).not.toHaveText('—', { timeout: 15000 })
  await expect(page.getByTestId('game-type')).toContainText(gameType)
}

export async function playOneRound(page: Page, gameType: string) {
  switch (gameType) {
    case 'ROLETRANDO': {
      // Start state is PLAYING, host should be current turn.
      await page.getByTestId('roletrando-spin').click()
      await expect(page.getByTestId('roletrando-spin-ok')).toBeVisible({ timeout: 15000 })
      return
    }
    case 'QUIZ_SPEED': {
      // Wait for answer options
      await page.getByRole('button', { name: /A\./ }).first().click()
      await expect(page.getByTestId('quiz-answer-ok')).toBeVisible({ timeout: 15000 })
      return
    }
    case 'QUIZ_INCREMENTAL': {
      // Millionaire board: click first available option
      await page.getByRole('button', { name: /^A\./ }).first().click()
      await expect(page.getByTestId('millionaire-answer-ok')).toBeVisible({ timeout: 15000 })
      return
    }
    case 'SURVIVAL': {
      await expect(page.getByTestId('survival-masked')).toBeVisible({ timeout: 15000 })
      await page.getByTestId('survival-answer-input').fill('teste')
      await page.getByTestId('survival-submit').click()
      await expect(page.getByTestId('survival-action-ok')).toBeVisible({ timeout: 15000 })
      return
    }
    case 'SEQUENCING': {
      await page.getByTestId('sequencing-submit').click()
      await expect(page.getByTestId('sequencing-action-ok')).toBeVisible({ timeout: 15000 })
      return
    }
    case 'DETECTIVE': {
      await page.getByTestId('detective-guess-input').fill('teste')
      await page.getByTestId('detective-submit').click()
      await expect(page.getByTestId('detective-action-ok')).toBeVisible({ timeout: 15000 })
      return
    }
    case 'BUZZER': {
      await page.getByTestId('buzzer-buzz').click()
      await page.getByTestId('buzzer-option-0').click()
      await expect(page.getByTestId('buzzer-action-ok')).toBeVisible({ timeout: 15000 })
      return
    }
    case 'SENSORY': {
      await page.getByTestId('sensory-guess-input').fill('teste')
      await page.getByTestId('sensory-submit').click()
      await expect(page.getByTestId('sensory-action-ok')).toBeVisible({ timeout: 15000 })
      return
    }
    case 'BINARY_DECISION': {
      await page.getByTestId('binary-true').click()
      await expect(page.getByTestId('binary-action-ok')).toBeVisible({ timeout: 15000 })
      return
    }
    case 'COMBINATION': {
      await page.getByTestId('combination-action').click()
      await expect(page.getByTestId('combination-action-ok')).toBeVisible({ timeout: 15000 })
      return
    }
  }

  throw new Error(`Unsupported gameType: ${gameType}`)
}
