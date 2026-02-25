import { test, expect } from '@playwright/test';
import { createRoomFromHome, joinAsHost, startGame, assertGameType, playOneRound } from './helpers'

test('All 10 games are available and functional', async ({ page }) => {
  page.on('console', (msg) => {
    console.log(`[browser:${msg.type()}] ${msg.text()}`)
  })

  const gameTypes = [
    'ROLETRANDO',
    'QUIZ_SPEED',
    'QUIZ_INCREMENTAL',
    'SURVIVAL',
    'SEQUENCING',
    'DETECTIVE',
    'BUZZER',
    'SENSORY',
    'BINARY_DECISION',
    'COMBINATION',
  ] as const

  for (const gameType of gameTypes) {
    const roomId = await createRoomFromHome(page, gameType)
    await joinAsHost(page, `Host_${gameType}_${Date.now()}`)
    await assertGameType(page, gameType)
    await startGame(page)
    await playOneRound(page, gameType)

    // Return to home for next game
    await page.goto('/')
    await expect(page.getByTestId(`create-room-${gameType}`)).toBeVisible()
    console.log(`✅ ${gameType} one-round flow passed (room=${roomId})`)
  }
});
