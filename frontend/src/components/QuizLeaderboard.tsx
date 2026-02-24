import { motion } from 'framer-motion'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'

interface Entry {
  id: string
  name: string
  score: number
}

interface QuizLeaderboardProps {
  ranking: Entry[] | undefined
  title?: string
  highlightId?: string
}

export function QuizLeaderboard({ ranking, title = 'Ranking', highlightId }: QuizLeaderboardProps) {
  if (!ranking?.length) return null
  return (
    <Box
      sx={{
        p: 2,
        borderRadius: 3,
        border: '1px solid rgba(255,255,255,0.08)',
        bgcolor: 'rgba(15, 23, 42, 0.8)',
        minWidth: 240,
      }}
    >
      <Typography variant="h6" sx={{ fontWeight: 800, color: '#10b981', mb: 1.5 }}>
        {title}
      </Typography>
      {ranking.map((e, i) => (
        <motion.div
          key={e.id}
          initial={{ opacity: 0, x: -8 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: i * 0.05 }}
        >
          <Box
            sx={{
              display: 'flex',
              justifyContent: 'space-between',
              py: 0.75,
              px: 1,
              borderRadius: 1,
              bgcolor: e.id === highlightId ? 'rgba(16, 185, 129, 0.15)' : 'transparent',
            }}
          >
            <Typography variant="body2" fontWeight={600}>
              {i + 1}. {e.name}
            </Typography>
            <Typography variant="body2" fontWeight={800} color="primary">
              {e.score}
            </Typography>
          </Box>
        </motion.div>
      ))}
    </Box>
  )
}
