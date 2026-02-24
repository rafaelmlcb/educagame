import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import Grid from '@mui/material/Grid2'
import Paper from '@mui/material/Paper'
import Link from '@mui/material/Link'
import { Gamepad2, Plus } from 'lucide-react'
import { api } from '@/api/client'
import type { Room } from '@/types/game'
import { GlassCard } from '@/components/GlassCard'

export function HomePage() {
  const navigate = useNavigate()
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.get<Room[]>('/api/rooms').then((r) => { setRooms(r.data); setLoading(false); }).catch(() => setLoading(false))
  }, [])

  const createRoom = async (gameType: string) => {
    const { data } = await api.post<{ roomId: string }>('/api/rooms', { theme: 'default', gameType, privateRoom: false })
    navigate(`/room/${data.roomId}`)
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        background: '#0a0f1e',
        py: 4,
        px: 2,
      }}
    >
      <Box sx={{ maxWidth: 900, mx: 'auto' }}>
        <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }}>
          <Typography variant="h3" sx={{ fontWeight: 900, color: '#fff', mb: 0.5, display: 'flex', alignItems: 'center', gap: 1 }}>
            <Gamepad2 size={40} strokeWidth={2} color="#10b981" />
            EducaGame
          </Typography>
          <Typography variant="body1" sx={{ color: 'rgba(255,255,255,0.7)', mb: 3 }}>
            Jogos educacionais multiplayer em tempo real
          </Typography>
        </motion.div>

        <Grid container size={{ xs: 12 }} spacing={2} sx={{ mb: 4 }}>
          <Grid size={{ xs: 12, md: 4 }}>
            <GlassCard delay={0.1}>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1, color: '#10b981' }}>Roletrando</Typography>
              <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.8)', mb: 2 }}>
                Gire a roleta e acumule pontos.
              </Typography>
              <Button variant="contained" color="primary" startIcon={<Plus size={18} />} onClick={() => createRoom('ROLETRANDO')} fullWidth>
                Criar sala
              </Button>
            </GlassCard>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <GlassCard delay={0.2}>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1, color: '#3b82f6' }}>Quiz</Typography>
              <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.8)', mb: 2 }}>
                Responda perguntas e marque pontos.
              </Typography>
              <Button variant="contained" color="secondary" startIcon={<Plus size={18} />} onClick={() => createRoom('QUIZ')} fullWidth>
                Criar sala
              </Button>
            </GlassCard>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <GlassCard delay={0.3}>
              <Typography variant="h6" sx={{ fontWeight: 800, mb: 1, color: '#f59e0b' }}>Show do Milhão</Typography>
              <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.8)', mb: 2 }}>
                Suba de nível e ganhe o milhão.
              </Typography>
              <Button variant="contained" sx={{ bgcolor: '#f59e0b', '&:hover': { bgcolor: '#d97706' } }} startIcon={<Plus size={18} />} onClick={() => createRoom('SHOW_DO_MILHAO')} fullWidth>
                Criar sala
              </Button>
            </GlassCard>
          </Grid>
        </Grid>

        <Typography variant="h6" sx={{ fontWeight: 800, color: 'rgba(255,255,255,0.9)', mb: 2 }}>
          Salas abertas
        </Typography>
        {loading ? (
          <Typography color="text.secondary">Carregando...</Typography>
        ) : rooms.length === 0 ? (
          <Paper sx={{ p: 3, textAlign: 'center', color: 'rgba(255,255,255,0.6)' }}>
            Nenhuma sala aberta. Crie uma acima ou entre com um código.
          </Paper>
        ) : (
          <Grid container size={{ xs: 12 }} spacing={2}>
            {rooms.map((room) => (
              <Grid size={{ xs: 12, sm: 6 }} key={room.roomId}>
                <Paper
                  component={motion.div}
                  whileHover={{ y: -2 }}
                  sx={{
                    p: 2,
                    cursor: 'pointer',
                    border: '1px solid rgba(255,255,255,0.05)',
                  }}
                  onClick={() => navigate(`/room/${room.roomId}`)}
                >
                  <Typography fontWeight={700}>{room.roomId}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {room.gameType} · {room.playerCount}/{room.maxPlayers} · {room.theme}
                  </Typography>
                </Paper>
              </Grid>
            ))}
          </Grid>
        )}

        <Box sx={{ mt: 4, display: 'flex', gap: 2 }}>
          <Link href="/admin/themes" underline="hover" color="primary" sx={{ fontWeight: 600 }}>
            Admin: temas
          </Link>
          <Link href="/admin/stats" underline="hover" color="primary" sx={{ fontWeight: 600 }}>
            Admin: estatísticas
          </Link>
        </Box>
      </Box>
    </Box>
  )
}
