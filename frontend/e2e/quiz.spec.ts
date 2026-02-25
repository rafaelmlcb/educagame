import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Quiz Kahoot game creation and basic functionality', async ({ page }) => {
  await createRoomFromHome(page, 'QUIZ_SPEED')
  await joinAsHost(page)
  await assertGameType(page, 'QUIZ_SPEED')
  await startGame(page)
  await playOneRound(page, 'QUIZ_SPEED')
})
