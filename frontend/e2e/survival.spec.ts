import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Acerte ou Caia game creation', async ({ page }) => {
  await createRoomFromHome(page, 'SURVIVAL')
  await joinAsHost(page)
  await assertGameType(page, 'SURVIVAL')
  await startGame(page)
  await playOneRound(page, 'SURVIVAL')
})
