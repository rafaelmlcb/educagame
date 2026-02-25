import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Combinação game creation', async ({ page }) => {
  await createRoomFromHome(page, 'COMBINATION')
  await joinAsHost(page)
  await assertGameType(page, 'COMBINATION')
  await startGame(page)
  await playOneRound(page, 'COMBINATION')
})
