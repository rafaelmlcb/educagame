import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Sensorial game creation', async ({ page }) => {
  await createRoomFromHome(page, 'SENSORY')
  await joinAsHost(page)
  await assertGameType(page, 'SENSORY')
  await startGame(page)
  await playOneRound(page, 'SENSORY')
})
