import { motion } from 'framer-motion'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'

interface GlassCardProps {
  children: React.ReactNode
  className?: string
  delay?: number
  'data-testid'?: string
}

export default function GlassCard({ children, className = '', delay = 0, 'data-testid': dataTestId }: GlassCardProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay }}
    >
      <Card
        data-testid={dataTestId}
        className={className}
        sx={{
          borderRadius: 3,
          border: '1px solid rgba(255,255,255,0.05)',
          backgroundColor: 'rgba(15, 23, 42, 0.8)',
          backdropFilter: 'blur(12px)',
          boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
          p: 0,
          transition: 'transform 0.2s, box-shadow 0.2s',
          '&:hover': {
            transform: 'translateY(-2px)',
            boxShadow: '0 12px 40px rgba(0,0,0,0.5)',
          },
        }}
      >
        <CardContent sx={{ p: 2 }}>{children}</CardContent>
      </Card>
    </motion.div>
  )
}
