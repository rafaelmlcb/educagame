import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid2'
import { ArrowLeft } from 'lucide-react'
import { api } from '@/api/client'
import { GlassCard } from '@/components/GlassCard'

export function ThemeSelectPage() {
  const [themes, setThemes] = useState<string[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.get<string[]>('/api/themes').then((r) => { setThemes(r.data ?? []); setLoading(false); }).catch(() => setLoading(false))
  }, [])

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: '#0a0f1e', py: 4, px: 2 }}>
      <Box sx={{ maxWidth: 700, mx: 'auto' }}>
        <Button startIcon={<ArrowLeft size={18} />} component={Link} to="/" sx={{ mb: 3, color: '#10b981' }}>
          Voltar
        </Button>
        <Typography variant="h4" sx={{ fontWeight: 900, color: '#fff', mb: 2 }}>
          Temas disponíveis
        </Typography>
        {loading ? (
          <Typography color="text.secondary">Carregando...</Typography>
        ) : (
          <Grid container size={{ xs: 12 }} spacing={2}>
            {themes.map((t, i) => (
              <Grid size={{ xs: 12, sm: 6 }} key={t}>
                <GlassCard delay={i * 0.05}>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#10b981' }}>{t}</Typography>
                  <Typography variant="body2" color="text.secondary">Tema para Roletrando, Quiz e Show do Milhão</Typography>
                </GlassCard>
              </Grid>
            ))}
          </Grid>
        )}
      </Box>
    </Box>
  )
}
