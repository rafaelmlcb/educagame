import { useEffect, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import Grid from '@mui/material/Grid2'
import TextField from '@mui/material/TextField'
import { useWebSocket } from '@/hooks/useWebSocket'
import { useSound } from '@/hooks/useSound'
import type { GameSession, WsOutbound, WheelSegment, RoletrandoPayload, QuizPayload, MillionairePayload } from '@/types/game'
import { Roleta } from '@/components/Roleta'
import { Placar } from '@/components/Placar'
import { RoletrandoPhrase } from '@/components/RoletrandoPhrase'
import { QuizQuestionCard } from '@/components/QuizQuestionCard'
import { QuizLeaderboard } from '@/components/QuizLeaderboard'
import { MillionaireBoard } from '@/components/MillionaireBoard'
import { api } from '@/api/client'

export function GameRoomPage() {
  const { roomId } = useParams<{ roomId: string }>()
  const navigate = useNavigate()
  const [session, setSession] = useState<GameSession | null>(null)
  const [playerName, setPlayerName] = useState('')
  const [joined, setJoined] = useState(false)
  const [myConnectionId, setMyConnectionId] = useState<string | null>(null)
  const [wheelSegments, setWheelSegments] = useState<WheelSegment[]>([])
  const [wheelResultIndex, setWheelResultIndex] = useState<number | null>(null)
  const [wheelSpinning, setWheelSpinning] = useState(false)
  const [themeLoaded, setThemeLoaded] = useState(false)
  const [guessLetter, setGuessLetter] = useState('')
  const [solvePhrase, setSolvePhrase] = useState('')

  const playWheelSound = useSound(undefined)

  const handleMessage = useCallback((msg: WsOutbound) => {
    if (msg.type === 'STATE' && msg.payload) {
      setSession(msg.payload as GameSession)
    }
    if (msg.type === 'JOIN_OK' && msg.payload) {
      const pl = msg.payload as { connectionId?: string }
      if (pl.connectionId) setMyConnectionId(pl.connectionId)
    }
    if (msg.type === 'WHEEL_SPUN' && msg.payload) {
      const pl = msg.payload as { segmentIndex?: number }
      if (typeof pl.segmentIndex === 'number') {
        setWheelResultIndex(pl.segmentIndex)
        setWheelSpinning(true)
        playWheelSound()
      }
    }
    if (msg.type === 'ERROR') {
      const pl = msg.payload as { message?: string }
      console.error('WS error:', pl?.message)
    }
  }, [playWheelSound])

  const { status, send } = useWebSocket({
    onMessage: handleMessage,
    autoConnect: !!roomId,
  })

  useEffect(() => {
    if (!session?.theme || themeLoaded) return
    const theme = session.theme
    api.get<WheelSegment[]>(`/api/themes/${theme}/wheel`).catch(() => null)
      .then((r) => {
        const data = r?.data
        setWheelSegments(Array.isArray(data) ? data : [])
      })
      .finally(() => setThemeLoaded(true))
  }, [session?.theme, themeLoaded])


  const joinRoom = () => {
    if (!roomId || !playerName.trim()) return
    setJoined(true)
  }

  useEffect(() => {
    if (!joined || !roomId || !playerName.trim() || status !== 'open') return
    send({ type: 'JOIN', roomId, playerName: playerName.trim() })
  }, [joined, roomId, playerName, status, send])

  useEffect(() => {
    if (themeLoaded || !session?.theme) return
    if (session.gameType !== 'ROLETRANDO') {
      setThemeLoaded(true)
      return
    }
    api.get<WheelSegment[]>(`/api/themes/${session.theme}/wheel`).catch(() => null)
      .then((r) => setWheelSegments(Array.isArray(r?.data) ? r.data : []))
      .finally(() => setThemeLoaded(true))
  }, [session?.theme, session?.gameType, themeLoaded])

  const startGame = () => send({ type: 'START' })
  const spinWheel = () => send({ type: 'WHEEL_SPIN' })
  const submitGuess = () => {
    const letter = guessLetter.trim().toUpperCase()[0]
    if (letter) {
      send({ type: 'GUESS', letter })
      setGuessLetter('')
    }
  }
  const submitSolve = () => {
    if (solvePhrase.trim()) {
      send({ type: 'SOLVE', phrase: solvePhrase.trim() })
      setSolvePhrase('')
    }
  }

  const roletrandoPayload = session?.gamePayload as RoletrandoPayload | undefined
  const quizPayload = session?.gamePayload as QuizPayload | undefined
  const millionairePayload = session?.gamePayload as MillionairePayload | undefined
  const currentTurnPlayer = session?.players?.length
    ? session.players[session.currentTurnIndex % session.players.length]
    : null
  const isMyTurn = !!myConnectionId && currentTurnPlayer?.id === myConnectionId && !currentTurnPlayer?.bot
  const isHost = !!session?.hostConnectionId && session.hostConnectionId === myConnectionId

  const quizAnswered = quizPayload?.responses && myConnectionId && myConnectionId in quizPayload.responses
  const sendQuizAnswer = (answerIndex: number) => send({ type: 'QUIZ_ANSWER', answerIndex })
  const sendQuizNext = () => send({ type: 'QUIZ_NEXT' })
  const sendMillionaireAnswer = (answerIndex: number) => send({ type: 'MILLIONAIRE_ANSWER', answerIndex })
  const sendLifeline50 = () => send({ type: 'LIFELINE_50_50' })
  const sendLifelineUni = () => send({ type: 'LIFELINE_UNI' })
  const sendLifelineSkip = () => send({ type: 'LIFELINE_SKIP' })

  if (!roomId) {
    navigate('/')
    return null
  }

  if (!joined) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: '#0a0f1e', p: 2 }}>
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <Typography variant="h5" sx={{ fontWeight: 800, mb: 2, color: '#fff' }}>Entrar na sala {roomId}</Typography>
          <TextField
            label="Seu nome"
            value={playerName}
            onChange={(e) => setPlayerName(e.target.value)}
            fullWidth
            sx={{ mb: 2 }}
            inputProps={{ maxLength: 32 }}
          />
          <Button variant="contained" color="primary" onClick={joinRoom} disabled={!playerName.trim()} fullWidth>
            Entrar
          </Button>
        </motion.div>
      </Box>
    )
  }

  if (!themeLoaded && session?.gameType === 'ROLETRANDO') {
    return (
      <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: '#0a0f1e' }}>
        <Typography color="primary" fontWeight={700}>Carregando tema...</Typography>
      </Box>
    )
  }

  const segments = wheelSegments.length > 0 ? wheelSegments : [{ label: '100', value: 100, color: '#10b981' }]

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: '#0a0f1e', py: 3, px: 2 }}>
      <Box sx={{ maxWidth: 1000, mx: 'auto' }}>
        <Typography variant="h6" sx={{ fontWeight: 800, color: 'rgba(255,255,255,0.9)', mb: 2 }}>
          Sala {session?.roomId ?? roomId} · {session?.phase ?? '—'}
        </Typography>
        <Grid container size={{ xs: 12 }} spacing={3}>
          <Grid size={{ xs: 12, md: 8 }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
              {session?.gameType === 'ROLETRANDO' && (
                <>
                  <RoletrandoPhrase payload={roletrandoPayload} />
                  <Roleta
                    segments={segments}
                    resultIndex={wheelResultIndex}
                    spinning={wheelSpinning}
                    onSpinComplete={() => setWheelSpinning(false)}
                  />
                  {roletrandoPayload?.segmentType === 'LOSE_TURN' && (
                    <Typography color="error" fontWeight={700}>Perdeu a vez!</Typography>
                  )}
                  {roletrandoPayload?.segmentType === 'LOSE_ALL' && (
                    <Typography color="error" fontWeight={700}>Perdeu tudo!</Typography>
                  )}
                  {session.phase === 'PLAYING' && isMyTurn && (
                    <Button variant="contained" color="primary" onClick={spinWheel} disabled={wheelSpinning}>
                      Girar roleta
                    </Button>
                  )}
                  {session.phase === 'GUESSING' && isMyTurn && !roletrandoPayload?.solvedBy && (
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, width: '100%', maxWidth: 320 }}>
                      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                        <TextField
                          size="small"
                          placeholder="Letra"
                          value={guessLetter}
                          onChange={(e) => setGuessLetter(e.target.value.slice(0, 1))}
                          inputProps={{ maxLength: 1, style: { textTransform: 'uppercase' } }}
                          sx={{ flex: 0, width: 72 }}
                        />
                        <Button variant="contained" color="primary" onClick={submitGuess} disabled={!guessLetter.trim()}>
                          Palpitar letra
                        </Button>
                      </Box>
                      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                        <TextField
                          size="small"
                          placeholder="Adivinhar frase"
                          value={solvePhrase}
                          onChange={(e) => setSolvePhrase(e.target.value.slice(0, 200))}
                          fullWidth
                        />
                        <Button variant="outlined" color="secondary" onClick={submitSolve} disabled={!solvePhrase.trim()}>
                          Resolver
                        </Button>
                      </Box>
                    </Box>
                  )}
                  {session.phase === 'GUESSING' && !isMyTurn && currentTurnPlayer && (
                    <Typography color="text.secondary">Vez de: {currentTurnPlayer.name}</Typography>
                  )}
                </>
              )}
              {session?.gameType === 'QUIZ' && (
                <>
                  {session.phase === 'QUIZ_QUESTION' && (
                    <QuizQuestionCard
                      payload={quizPayload}
                      roundStartedAt={session.roundStartedAt}
                      onAnswer={sendQuizAnswer}
                      disabled={quizAnswered}
                    />
                  )}
                  {session.phase === 'QUIZ_FEEDBACK' && (
                    <Box sx={{ textAlign: 'center' }}>
                      <Typography variant="h6" color="primary" fontWeight={700}>Resposta registrada!</Typography>
                      <Typography variant="body2" color="text.secondary">Aguardando o host avançar.</Typography>
                      {isHost && <Button variant="contained" color="primary" onClick={sendQuizNext} sx={{ mt: 2 }}>Próxima etapa</Button>}
                    </Box>
                  )}
                  {session.phase === 'QUIZ_RANKING' && (
                    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', justifyContent: 'center' }}>
                      <QuizLeaderboard ranking={quizPayload?.roundRanking} title="Ranking da rodada" highlightId={myConnectionId ?? undefined} />
                      {isHost && <Button variant="contained" color="primary" onClick={sendQuizNext}>Próxima pergunta</Button>}
                    </Box>
                  )}
                  {session.phase === 'GAME_END' && quizPayload?.finalRanking && (
                    <QuizLeaderboard ranking={quizPayload.finalRanking} title="Ranking final" highlightId={myConnectionId ?? undefined} />
                  )}
                </>
              )}
              {session?.gameType === 'SHOW_DO_MILHAO' && session.phase === 'MILLIONAIRE_QUESTION' && (
                <MillionaireBoard
                  payload={millionairePayload}
                  onAnswer={sendMillionaireAnswer}
                  onLifeline50={sendLifeline50}
                  onLifelineUni={sendLifelineUni}
                  onLifelineSkip={sendLifelineSkip}
                  disabled={false}
                />
              )}
              {session?.gameType === 'SHOW_DO_MILHAO' && session.phase === 'GAME_END' && (
                <Box sx={{ textAlign: 'center', p: 2 }}>
                  <Typography variant="h6" color={millionairePayload?.won ? 'primary' : 'text.secondary'}>
                    {millionairePayload?.won ? 'Parabéns! Você ganhou!' : 'Resposta errada!'}
                  </Typography>
                  <Typography variant="body1">Prêmio: R$ {(millionairePayload?.finalPrize ?? 0).toLocaleString('pt-BR')}</Typography>
                </Box>
              )}
              {session?.phase === 'LOBBY' && (
                <Button variant="contained" color="primary" onClick={startGame}>
                  Iniciar jogo (apenas o host)
                </Button>
              )}
            </Box>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            {session?.players && <Placar players={session.players} currentTurnIndex={session.currentTurnIndex} />}
          </Grid>
        </Grid>
        <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.5)', mt: 2, display: 'block' }}>
          Conexão: {status}
        </Typography>
      </Box>
    </Box>
  )
}
