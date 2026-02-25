import { test, expect } from '@playwright/test'
import { createRoomFromHome, joinAsHost, joinRoomById, startGame, playOneRound } from './helpers'

test('two users: host creates room and guest joins then play one round', async ({ browser }) => {
  const hostContext = await browser.newContext()
  const guestContext = await browser.newContext()

  const host = await hostContext.newPage()
  const guest = await guestContext.newPage()

  // Host creates room
  const gameType = 'QUIZ_SPEED'
  const roomId = await createRoomFromHome(host, gameType)
  const hostName = await joinAsHost(host, 'Host-E2E')

  // Guest joins by room id
  const guestName = await joinRoomById(guest, roomId, 'Guest-E2E')

  // Host starts the game
  await startGame(host)

  // Play one round from host side (should progress the game)
  await playOneRound(host, gameType)

  // Also let guest perform an answer action if applicable
  try {
    await playOneRound(guest, gameType)
  } catch (e) {
    // non-fatal for games where only host action is required
  }

  // Basic assertions: both players see the room and game type
  await expect(host.getByTestId('game-room')).toBeVisible()
  await expect(guest.getByTestId('game-room')).toBeVisible()
  await expect(host.getByTestId('game-type')).toContainText(gameType)
  await expect(guest.getByTestId('game-type')).toContainText(gameType)

  await hostContext.close()
  await guestContext.close()
})
