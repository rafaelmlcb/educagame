import { motion } from 'framer-motion'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { Player } from '@/types/game'

interface PlacarProps {
  players: Player[]
  currentTurnIndex?: number
}

export function Placar({ players, currentTurnIndex = 0 }: PlacarProps) {
  return (
    <Box
      className="rounded-card border border-white/5 bg-glass shadow-card backdrop-blur-glass"
      sx={{
        p: 2,
        minWidth: 220,
      }}
    >
      <Typography variant="h6" sx={{ fontWeight: 800, mb: 1.5, color: '#10b981' }}>
        Placar
      </Typography>
      {players.map((p, i) => (
        <motion.div
          key={p.id}
          initial={{ opacity: 0, x: -8 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: i * 0.05 }}
        >
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              py: 0.75,
              px: 1,
              borderRadius: 1,
              bgcolor: currentTurnIndex % players.length === i ? 'rgba(16, 185, 129, 0.15)' : 'transparent',
              border: currentTurnIndex % players.length === i ? '1px solid rgba(16, 185, 129, 0.4)' : 'none',
            }}
          >
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {p.name}
              {!p.connected && ' (off)'}
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 800, color: '#10b981' }}>
              {p.score}
            </Typography>
          </Box>
        </motion.div>
      ))}
    </Box>
  )
}
