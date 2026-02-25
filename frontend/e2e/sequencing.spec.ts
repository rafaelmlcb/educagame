import { test } from '@playwright/test'
import { assertGameType, createRoomFromHome, joinAsHost, playOneRound, startGame } from './helpers'

test('Ordenação game creation', async ({ page }) => {
  await createRoomFromHome(page, 'SEQUENCING')
  await joinAsHost(page)
  await assertGameType(page, 'SEQUENCING')
  await startGame(page)
  await playOneRound(page, 'SEQUENCING')
})
