import { motion } from 'framer-motion'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { RoletrandoPayload } from '@/types/game'

interface RoletrandoPhraseProps {
  payload: RoletrandoPayload | null | undefined
}

export function RoletrandoPhrase({ payload }: RoletrandoPhraseProps) {
  if (!payload?.phrase) return null
  const phrase = payload.phrase
  const revealed = new Set(payload.revealed ?? [])
  const display: string[] = []
  for (let i = 0; i < phrase.length; i++) {
    const ch = phrase[i]
    if (ch === ' ') display.push(' ')
    else if (revealed.has(ch)) display.push(ch)
    else display.push('_')
  }

  return (
    <Box
      sx={{
        p: 2,
        borderRadius: 3,
        border: '1px solid rgba(255,255,255,0.08)',
        bgcolor: 'rgba(15, 23, 42, 0.6)',
        textAlign: 'center',
      }}
    >
      <Typography
        variant="h5"
        sx={{
          fontFamily: 'monospace',
          letterSpacing: 4,
          fontWeight: 800,
          color: '#fff',
          wordBreak: 'break-all',
        }}
      >
        {display.map((c, i) => (
          <motion.span
            key={i}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: i * 0.02 }}
          >
            {c}
          </motion.span>
        ))}
      </Typography>
      {payload.solvedBy && (
        <Typography variant="body2" sx={{ mt: 1, color: '#10b981', fontWeight: 700 }}>
          Resolvido por: {payload.solvedBy}
        </Typography>
      )}
    </Box>
  )
}
