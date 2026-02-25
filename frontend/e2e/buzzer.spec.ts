import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Buzzer game creation', async ({ page }) => {
  await createRoomFromHome(page, 'BUZZER')
  await joinAsHost(page)
  await assertGameType(page, 'BUZZER')
  await startGame(page)
  await playOneRound(page, 'BUZZER')
})
