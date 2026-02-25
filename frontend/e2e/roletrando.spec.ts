import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Roletrando game creation and basic functionality', async ({ page }) => {
  await createRoomFromHome(page, 'ROLETRANDO')
  await joinAsHost(page)
  await assertGameType(page, 'ROLETRANDO')
  await startGame(page)
  await playOneRound(page, 'ROLETRANDO')
})
