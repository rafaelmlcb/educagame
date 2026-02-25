import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Fato ou Fake game creation', async ({ page }) => {
  await createRoomFromHome(page, 'BINARY_DECISION')
  await joinAsHost(page)
  await assertGameType(page, 'BINARY_DECISION')
  await startGame(page)
  await playOneRound(page, 'BINARY_DECISION')
})
