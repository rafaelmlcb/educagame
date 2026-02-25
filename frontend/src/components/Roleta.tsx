import { useEffect, useRef } from 'react'
import { motion, useAnimation } from 'framer-motion'
import Box from '@mui/material/Box'
import type { WheelSegment } from '@/types/game'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'

interface RoletaProps {
  segments: WheelSegment[]
  resultIndex: number | null
  spinning: boolean
  onSpinComplete?: () => void
}

const SEGMENT_ANGLE = 360

export function Roleta({ segments, resultIndex, spinning, onSpinComplete }: RoletaProps) {
  const controls = useAnimation()
  const spunRef = useRef(false)
  const size = 280
  const radius = size / 2

  useEffect(() => {
    if (!spinning || resultIndex == null || !segments.length || spunRef.current) return
    spunRef.current = true
    const segmentAngle = SEGMENT_ANGLE / segments.length
    const targetAngle = 360 * 5 + (segments.length - 1 - resultIndex) * segmentAngle - segmentAngle / 2
    controls.start({
      rotate: targetAngle,
      transition: { duration: 4, ease: [0.2, 0.8, 0.2, 1] },
    })
  }, [spinning, resultIndex, segments.length, controls])

  useEffect(() => {
    if (!spinning) spunRef.current = false
  }, [spinning])

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', p: 2 }}>
      <Card sx={{ width: size, height: size, borderRadius: '50%', boxShadow: 'none', bgcolor: 'transparent' }}>
        <CardContent sx={{ p: 0, position: 'relative', width: '100%', height: '100%' }}>
          <motion.div
            animate={controls}
            style={{
              width: '100%',
              height: '100%',
              borderRadius: '50%',
              position: 'relative',
            }}
            onAnimationComplete={() => { onSpinComplete?.(); spunRef.current = false }}
          >
        {segments.map((seg, i) => {
          const angle = (360 / segments.length) * i
          return (
            <Box
              key={i}
              sx={{
                position: 'absolute',
                left: radius,
                top: radius,
                width: radius,
                height: 2,
                transformOrigin: 'left center',
                transform: `rotate(${angle}deg)`,
                background: seg.color ?? '#3b82f6',
                borderRight: '1px solid rgba(0,0,0,0.2)',
              }}
            />
          )
        })}
        {segments.map((seg, i) => {
          const angle = (360 / segments.length) * i + (360 / segments.length) / 2
          const rad = (angle * Math.PI) / 180
          const r = radius * 0.7
          const x = radius + Math.cos(rad) * r
          const y = radius + Math.sin(rad) * r
          return (
            <Box
              key={`label-${i}`}
              sx={{
                position: 'absolute',
                left: x - 20,
                top: y - 10,
                width: 40,
                textAlign: 'center',
                fontSize: 11,
                fontWeight: 700,
                color: '#fff',
                textShadow: '0 1px 2px rgba(0,0,0,0.8)',
              }}
            >
              {seg.label}
            </Box>
          )
        })}
          </motion.div>
          <Box
            sx={{
              position: 'absolute',
              top: -8,
              left: '50%',
              transform: 'translateX(-50%)',
              width: 0,
              height: 0,
              borderLeft: '12px solid transparent',
              borderRight: '12px solid transparent',
              borderTop: '20px solid #10b981',
              filter: 'drop-shadow(0 2px 4px rgba(0,0,0,0.5))',
            }}
          />
        </CardContent>
      </Card>
    </Box>
  )
}
