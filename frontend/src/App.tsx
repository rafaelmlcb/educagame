import { Routes, Route } from 'react-router-dom'
import { HomePage } from '@/pages/HomePage'
import { GameRoomPage } from '@/pages/GameRoomPage'
import { ThemeSelectPage } from '@/pages/ThemeSelectPage'
import { AdminStatsPage } from '@/pages/AdminStatsPage'

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/room/:roomId" element={<GameRoomPage />} />
      <Route path="/admin/themes" element={<ThemeSelectPage />} />
      <Route path="/admin/stats" element={<AdminStatsPage />} />
    </Routes>
  )
}
