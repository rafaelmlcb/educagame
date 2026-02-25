import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import type { MillionairePayload } from '@/types/game'

const PRIZES = [0, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 250000, 500000, 1000000]

interface MillionaireBoardProps {
  payload: MillionairePayload | null | undefined
  onAnswer: (index: number) => void
  onLifeline50: () => void
  onLifelineUni: () => void
  onLifelineSkip: () => void
  disabled?: boolean
}

export function MillionaireBoard({
  payload,
  onAnswer,
  onLifeline50,
  onLifelineUni,
  onLifelineSkip,
  disabled,
}: MillionaireBoardProps) {
  const level = payload?.level ?? 1
  const options = payload?.options ?? []
  const removed = payload?.removedOptions ?? []
  const audiencePercents = payload?.audiencePercents

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, width: '100%', maxWidth: 640 }}>
      <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', justifyContent: 'center' }}>
        {PRIZES.slice(1, 11).map((value, i) => (
          <Box
            key={value}
            sx={{
              px: 1,
              py: 0.5,
              borderRadius: 1,
              bgcolor: level === i + 1 ? '#10b981' : 'rgba(255,255,255,0.06)',
              border: level === i + 1 ? '2px solid #10b981' : '1px solid rgba(255,255,255,0.05)',
            }}
          >
            <Typography variant="caption" fontWeight={700}>
              {value >= 1000 ? value / 1000 + 'k' : value}
            </Typography>
          </Box>
        ))}
      </Box>
      <Box
        sx={{
          p: 2,
          borderRadius: 3,
          border: '1px solid rgba(255,255,255,0.08)',
          bgcolor: 'rgba(15, 23, 42, 0.8)',
          width: '100%',
        }}
      >
        <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 2 }}>
          {payload?.question}
        </Typography>
        <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
          <Button
            size="small"
            variant="outlined"
            onClick={onLifeline50}
            disabled={disabled || payload?.lifeline50Used}
            sx={{ color: '#3b82f6' }}
          >
            50:50
          </Button>
          <Button
            size="small"
            variant="outlined"
            onClick={onLifelineUni}
            disabled={disabled || payload?.lifelineUniUsed}
            sx={{ color: '#3b82f6' }}
          >
            Universitários
          </Button>
          <Button
            size="small"
            variant="outlined"
            onClick={onLifelineSkip}
            disabled={disabled || payload?.lifelineSkipUsed}
            sx={{ color: '#3b82f6' }}
          >
            Pular
          </Button>
        </Box>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {options.map((opt, i) => {
            const isRemoved = removed.includes(i)
            if (isRemoved) return null
            const pct = audiencePercents?.[i]
            return (
              <Button
                key={i}
                variant="outlined"
                onClick={() => onAnswer(i)}
                disabled={disabled}
                fullWidth
                sx={{
                  justifyContent: 'space-between',
                  textTransform: 'none',
                  borderColor: 'rgba(255,255,255,0.2)',
                  color: '#fff',
                  '&:hover': { borderColor: '#10b981', bgcolor: 'rgba(16,185,129,0.1)' },
                }}
              >
                <span>{String.fromCharCode(65 + i)}. {opt}</span>
                {pct != null && (
                  <Typography variant="caption" color="primary">
                    {pct}%
                  </Typography>
                )}
              </Button>
            )
          })}
        </Box>
      </Box>
    </Box>
  )
}
