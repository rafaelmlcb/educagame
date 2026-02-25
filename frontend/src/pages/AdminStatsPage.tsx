import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Grid from '@mui/material/Grid2'
import { ArrowLeft } from 'lucide-react'
import { api } from '@/api/client'

interface Summary {
  uptimeMs: number
  totalGamesCreated: number
  gamesByType: Record<string, number>
}

export function AdminStatsPage() {
  const [summary, setSummary] = useState<Summary | null>(null)
  const [leaderboard, setLeaderboard] = useState<Record<string, { name: string; score: number; games?: number }[]>>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.get<Summary>('/api/stats/summary').then((r) => setSummary(r.data)).catch(() => {})
    Promise.all([
      api.get('/api/stats/leaderboard?mode=ROLETRANDO&limit=10').then((r) => r.data),
      api.get('/api/stats/leaderboard?mode=QUIZ&limit=10').then((r) => r.data),
      api.get('/api/stats/leaderboard?mode=SHOW_DO_MILHAO&limit=10').then((r) => r.data),
    ])
      .then(([r, q, m]) => setLeaderboard({ ROLETRANDO: r, QUIZ: q, SHOW_DO_MILHAO: m }))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const uptimeMin = summary ? Math.floor(summary.uptimeMs / 60000) : 0

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: '#0a0f1e', py: 4, px: 2 }}>
      <Box sx={{ maxWidth: 900, mx: 'auto' }}>
        <Button startIcon={<ArrowLeft size={18} />} component={Link} to="/" sx={{ mb: 3, color: '#10b981' }}>
          Voltar
        </Button>
        <Typography variant="h4" sx={{ fontWeight: 900, color: '#fff', mb: 2 }}>
          Estatísticas do servidor
        </Typography>
        {loading ? (
          <Typography color="text.secondary">Carregando...</Typography>
        ) : (
          <>
            <Grid container size={{ xs: 12 }} spacing={2} sx={{ mb: 4 }}>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Paper component={motion.div} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} sx={{ p: 2, borderRadius: 3 }}>
                  <Typography variant="body2" color="text.secondary">Uptime</Typography>
                  <Typography variant="h5" fontWeight={800} color="primary">{uptimeMin} min</Typography>
                </Paper>
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Paper component={motion.div} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }} sx={{ p: 2, borderRadius: 3 }}>
                  <Typography variant="body2" color="text.secondary">Jogos criados</Typography>
                  <Typography variant="h5" fontWeight={800} color="primary">{summary?.totalGamesCreated ?? 0}</Typography>
                </Paper>
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Paper component={motion.div} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} sx={{ p: 2, borderRadius: 3 }}>
                  <Typography variant="body2" color="text.secondary">Por modo</Typography>
                  <Typography variant="body2">{JSON.stringify(summary?.gamesByType ?? {})}</Typography>
                </Paper>
              </Grid>
            </Grid>
            <Typography variant="h6" sx={{ fontWeight: 800, color: 'rgba(255,255,255,0.9)', mb: 2 }}>
              Leaderboards (Top 10)
            </Typography>
            <Grid container size={{ xs: 12 }} spacing={2}>
              {(['ROLETRANDO', 'QUIZ', 'SHOW_DO_MILHAO'] as const).map((mode) => (
                <Grid size={{ xs: 12, md: 4 }} key={mode}>
                  <Paper sx={{ p: 2, borderRadius: 3 }}>
                    <Typography variant="subtitle1" fontWeight={700} color="primary" sx={{ mb: 1 }}>{mode.replace('_', ' ')}</Typography>
                    {(leaderboard[mode] ?? []).map((e: { name: string; score: number }, i: number) => (
                      <Box key={i} sx={{ display: 'flex', justifyContent: 'space-between', py: 0.5 }}>
                        <Typography variant="body2">{i + 1}. {e.name}</Typography>
                        <Typography variant="body2" fontWeight={700}>{e.score}</Typography>
                      </Box>
                    ))}
                  </Paper>
                </Grid>
              ))}
            </Grid>
          </>
        )}
      </Box>
    </Box>
  )
}
