import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import LinearProgress from '@mui/material/LinearProgress'
import type { QuizPayload } from '@/types/game'

interface QuizQuestionCardProps {
  payload: QuizPayload | null | undefined
  roundStartedAt?: number
  onAnswer: (answerIndex: number) => void
  disabled?: boolean
}

export function QuizQuestionCard({ payload, roundStartedAt, onAnswer, disabled }: QuizQuestionCardProps) {
  const timeLimitMs = payload?.timeLimitMs ?? 15000
  const [remainingMs, setRemainingMs] = useState(timeLimitMs)
  const start = roundStartedAt ?? Date.now()

  useEffect(() => {
    const interval = setInterval(() => {
      const elapsed = Date.now() - start
      setRemainingMs(Math.max(0, timeLimitMs - elapsed))
    }, 200)
    return () => clearInterval(interval)
  }, [start, timeLimitMs])

  const progress = Math.max(0, Math.min(1, remainingMs / timeLimitMs))
  const options = payload?.options ?? []

  return (
    <Box
      sx={{
        p: 2,
        borderRadius: 3,
        border: '1px solid rgba(255,255,255,0.08)',
        bgcolor: 'rgba(15, 23, 42, 0.8)',
        width: '100%',
        maxWidth: 560,
      }}
    >
      <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 1 }}>
        {payload?.question}
      </Typography>
      <LinearProgress
        variant="determinate"
        value={progress * 100}
        sx={{ mb: 2, height: 8, borderRadius: 1, bgcolor: 'rgba(255,255,255,0.1)' }}
        color={remainingMs < 3000 ? 'error' : 'primary'}
      />
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {options.map((opt, i) => (
          <Button
            key={i}
            variant="outlined"
            onClick={() => onAnswer(i)}
            disabled={disabled}
            sx={{
              justifyContent: 'flex-start',
              textTransform: 'none',
              borderColor: 'rgba(255,255,255,0.2)',
              color: '#fff',
              '&:hover': { borderColor: '#10b981', bgcolor: 'rgba(16,185,129,0.1)' },
            }}
          >
            {String.fromCharCode(65 + i)}. {opt}
          </Button>
        ))}
      </Box>
    </Box>
  )
}
