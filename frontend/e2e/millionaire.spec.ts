import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Show do Milhão game creation', async ({ page }) => {
  await createRoomFromHome(page, 'QUIZ_INCREMENTAL')
  await joinAsHost(page)
  await assertGameType(page, 'QUIZ_INCREMENTAL')
  await startGame(page)
  await playOneRound(page, 'QUIZ_INCREMENTAL')
})
