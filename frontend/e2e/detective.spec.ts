import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Detetive game creation', async ({ page }) => {
  await createRoomFromHome(page, 'DETECTIVE')
  await joinAsHost(page)
  await assertGameType(page, 'DETECTIVE')
  await startGame(page)
  await playOneRound(page, 'DETECTIVE')
})
