import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Typography from '@mui/material/Typography'
import Grid from '@mui/material/Grid2'
import TextField from '@mui/material/TextField'
import { useWebSocket } from '@/hooks/useWebSocket'
import { useSound } from '@/hooks/useSound'
import type {
  GameSession,
  WsOutbound,
  WheelSegment,
  RoletrandoPayload,
  QuizPayload,
  MillionairePayload,
  SurvivalPayload,
  SequencingPayload,
  DetectivePayload,
  BuzzerPayload,
  SensoryPayload,
  BinaryPayload,
  CombinationPayload,
} from '@/types/game'
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
  const [pendingJoin, setPendingJoin] = useState<{ roomId: string; playerName: string } | null>(null)
  const [joinSent, setJoinSent] = useState(false)
  const lastJoinAttemptAtRef = useRef<number>(0)
  const [wheelSegments, setWheelSegments] = useState<WheelSegment[]>([])
  const [wheelResultIndex, setWheelResultIndex] = useState<number | null>(null)
  const [wheelSpinning, setWheelSpinning] = useState(false)
  const [themeLoaded, setThemeLoaded] = useState(false)
  const [guessLetter, setGuessLetter] = useState('')
  const [solvePhrase, setSolvePhrase] = useState('')
  const [actionAck, setActionAck] = useState<Record<string, number>>({})

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
    onOpen: () => {
      if (pendingJoin && !joinSent) {
        send({ type: 'JOIN', roomId: pendingJoin.roomId, playerName: pendingJoin.playerName })
        setJoinSent(true)
      }
    },
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
    setPendingJoin({ roomId, playerName: playerName.trim() })
    setJoinSent(false)
    setJoined(true)
  }

  useEffect(() => {
    if (!joined || !pendingJoin || joinSent || status !== 'open') return
    send({ type: 'JOIN', roomId: pendingJoin.roomId, playerName: pendingJoin.playerName })
    setJoinSent(true)
  }, [joined, pendingJoin, joinSent, status, send])

  // Resilience: if we never receive STATE (session stays null), retry JOIN periodically.
  useEffect(() => {
    if (!joined || !pendingJoin || status !== 'open' || session) return
    const now = Date.now()
    if (now - lastJoinAttemptAtRef.current < 1000) return
    lastJoinAttemptAtRef.current = now
    send({ type: 'JOIN', roomId: pendingJoin.roomId, playerName: pendingJoin.playerName })
  }, [joined, pendingJoin, status, session, send])

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
  const spinWheel = () => {
    send({ type: 'WHEEL_SPIN' })
    setActionAck((s) => ({ ...s, ROLETRANDO_SPIN: Date.now() }))
  }
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

  const [genericTextAnswer, setGenericTextAnswer] = useState('')

  const roletrandoPayload = session?.gamePayload as RoletrandoPayload | undefined
  const quizPayload = session?.gamePayload as QuizPayload | undefined
  const millionairePayload = session?.gamePayload as MillionairePayload | undefined
  const survivalPayload = session?.gamePayload as SurvivalPayload | undefined
  const sequencingPayload = session?.gamePayload as SequencingPayload | undefined
  const detectivePayload = session?.gamePayload as DetectivePayload | undefined
  const buzzerPayload = session?.gamePayload as BuzzerPayload | undefined
  const sensoryPayload = session?.gamePayload as SensoryPayload | undefined
  const binaryPayload = session?.gamePayload as BinaryPayload | undefined
  const combinationPayload = session?.gamePayload as CombinationPayload | undefined
  const currentTurnPlayer = session?.players?.length
    ? session.players[session.currentTurnIndex % session.players.length]
    : null
  const isMyTurn = !!myConnectionId && currentTurnPlayer?.id === myConnectionId && !currentTurnPlayer?.bot
  const isHost = !!session?.hostConnectionId && session.hostConnectionId === myConnectionId

  const quizAnswered = !!(quizPayload?.responses && myConnectionId && myConnectionId in quizPayload.responses)
  const sendQuizAnswer = (answerIndex: number) => {
    send({ type: 'QUIZ_ANSWER', answerIndex })
    setActionAck((s) => ({ ...s, QUIZ_ANSWER: Date.now() }))
  }
  const sendQuizNext = () => send({ type: 'QUIZ_NEXT' })
  const sendMillionaireAnswer = (answerIndex: number) => {
    send({ type: 'MILLIONAIRE_ANSWER', answerIndex })
    setActionAck((s) => ({ ...s, MILLIONAIRE_ANSWER: Date.now() }))
  }
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
            inputProps={{ maxLength: 32, 'data-testid': 'player-name-input' }}
          />
          <Button variant="contained" color="primary" onClick={joinRoom} disabled={!playerName.trim()} fullWidth data-testid="join-game">
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
    <Box sx={{ minHeight: '100vh', bgcolor: '#0a0f1e', py: 3, px: 2 }} data-testid="game-room">
      <Box sx={{ maxWidth: 1000, mx: 'auto' }}>
        <Typography variant="h6" sx={{ fontWeight: 800, color: 'rgba(255,255,255,0.9)', mb: 2 }}>
          Sala {session?.roomId ?? roomId} · {session?.phase ?? '—'}
        </Typography>
        <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.65)', display: 'block', mb: 2 }} data-testid="game-type">
          {session?.gameType ?? '—'}
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
                    <Button variant="contained" color="primary" onClick={spinWheel} disabled={wheelSpinning} data-testid="roletrando-spin">
                      Girar roleta
                    </Button>
                  )}
                  {actionAck.ROLETRANDO_SPIN && <Box data-testid="roletrando-spin-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
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
                          data-testid="roletrando-letter"
                        />
                        <Button variant="contained" color="primary" onClick={submitGuess} disabled={!guessLetter.trim()} data-testid="roletrando-guess">
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
                          data-testid="roletrando-solve-text"
                        />
                        <Button variant="outlined" color="secondary" onClick={submitSolve} disabled={!solvePhrase.trim()} data-testid="roletrando-solve">
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
              {session?.gameType === 'QUIZ_SPEED' && (
                <>
                  {session.phase === 'QUIZ_QUESTION' && (
                    <QuizQuestionCard
                      payload={quizPayload}
                      roundStartedAt={session.roundStartedAt}
                      onAnswer={sendQuizAnswer}
                      disabled={quizAnswered}
                    />
                  )}
                  {actionAck.QUIZ_ANSWER && <Box data-testid="quiz-answer-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
                  {session.phase === 'QUIZ_FEEDBACK' && (
                    <Box sx={{ textAlign: 'center' }}>
                      <Typography variant="h6" color="primary" fontWeight={700}>Resposta registrada!</Typography>
                      <Typography variant="body2" color="text.secondary">Aguardando o host avançar.</Typography>
                      {isHost && <Button variant="contained" color="primary" onClick={sendQuizNext} sx={{ mt: 2 }} data-testid="quiz-next">Próxima etapa</Button>}
                    </Box>
                  )}
                  {session.phase === 'QUIZ_RANKING' && (
                    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', justifyContent: 'center' }}>
                      <QuizLeaderboard ranking={quizPayload?.roundRanking} title="Ranking da rodada" highlightId={myConnectionId ?? undefined} />
                      {isHost && <Button variant="contained" color="primary" onClick={sendQuizNext} data-testid="quiz-next">Próxima pergunta</Button>}
                    </Box>
                  )}
                  {session.phase === 'GAME_END' && quizPayload?.finalRanking && (
                    <QuizLeaderboard ranking={quizPayload.finalRanking} title="Ranking final" highlightId={myConnectionId ?? undefined} />
                  )}
                </>
              )}
              {session?.gameType === 'QUIZ_INCREMENTAL' && session.phase === 'MILLIONAIRE_QUESTION' && (
                <>
                  <MillionaireBoard
                    payload={millionairePayload}
                    onAnswer={sendMillionaireAnswer}
                    onLifeline50={sendLifeline50}
                    onLifelineUni={sendLifelineUni}
                    onLifelineSkip={sendLifelineSkip}
                    disabled={false}
                  />
                </>
              )}
              {actionAck.MILLIONAIRE_ANSWER && <Box data-testid="millionaire-answer-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
              {session?.gameType === 'QUIZ_INCREMENTAL' && session.phase === 'GAME_END' && (
                <Box sx={{ textAlign: 'center', p: 2 }}>
                  <Typography variant="h6" color={millionairePayload?.won ? 'primary' : 'text.secondary'}>
                    {millionairePayload?.won ? 'Parabéns! Você ganhou!' : 'Resposta errada!'}
                  </Typography>
                  <Typography variant="body1">Prêmio: R$ {(millionairePayload?.finalPrize ?? 0).toLocaleString('pt-BR')}</Typography>
                </Box>
              )}

              {session?.gameType === 'SURVIVAL' && (
                <Box sx={{ width: '100%', maxWidth: 560 }}>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 1 }} data-testid="survival-masked">
                    {survivalPayload?.maskedPhrase}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <TextField
                      fullWidth
                      size="small"
                      placeholder="Resposta"
                      value={genericTextAnswer}
                      onChange={(e) => setGenericTextAnswer(e.target.value)}
                      inputProps={{ 'data-testid': 'survival-answer-input' }}
                    />
                    <Button
                      variant="contained"
                      onClick={() => {
                        send({ type: 'SURVIVAL_ANSWER', answer: genericTextAnswer })
                        setGenericTextAnswer('')
                        setActionAck((s) => ({ ...s, SURVIVAL: Date.now() }))
                      }}
                      disabled={!genericTextAnswer.trim()}
                      data-testid="survival-submit"
                    >
                      Enviar
                    </Button>
                  </Box>
                  {actionAck.SURVIVAL && <Box data-testid="survival-action-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
                </Box>
              )}

              {session?.gameType === 'SEQUENCING' && (
                <Box sx={{ width: '100%', maxWidth: 640 }}>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 1 }}>
                    {sequencingPayload?.sequenceDescription}
                  </Typography>
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
                    {(sequencingPayload?.shuffledItems ?? []).map((it) => (
                      <Box
                        key={it.id}
                        sx={{ px: 1.2, py: 0.8, borderRadius: 2, bgcolor: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.08)' }}
                      >
                        <Typography variant="caption" sx={{ color: '#fff' }}>{it.content ?? it.id}</Typography>
                      </Box>
                    ))}
                  </Box>
                  <Button
                    variant="contained"
                    onClick={() => {
                      const orderedIds = (sequencingPayload?.shuffledItems ?? []).map((it) => it.id)
                      send({ type: 'SEQUENCING_SUBMIT', orderedIds })
                      setActionAck((s) => ({ ...s, SEQUENCING: Date.now() }))
                    }}
                    disabled={!sequencingPayload?.shuffledItems?.length}
                    data-testid="sequencing-submit"
                  >
                    Enviar ordem
                  </Button>
                  {actionAck.SEQUENCING && <Box data-testid="sequencing-action-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
                </Box>
              )}

              {session?.gameType === 'DETECTIVE' && (
                <Box sx={{ width: '100%', maxWidth: 640 }}>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 1 }}>
                    {detectivePayload?.mysteryDescription ?? detectivePayload?.category}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <TextField
                      fullWidth
                      size="small"
                      placeholder="Seu palpite"
                      value={genericTextAnswer}
                      onChange={(e) => setGenericTextAnswer(e.target.value)}
                      inputProps={{ 'data-testid': 'detective-guess-input' }}
                    />
                    <Button
                      variant="contained"
                      onClick={() => {
                        send({ type: 'DETECTIVE_GUESS', guess: genericTextAnswer })
                        setGenericTextAnswer('')
                        setActionAck((s) => ({ ...s, DETECTIVE: Date.now() }))
                      }}
                      disabled={!genericTextAnswer.trim()}
                      data-testid="detective-submit"
                    >
                      Enviar
                    </Button>
                  </Box>
                  {actionAck.DETECTIVE && <Box data-testid="detective-action-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
                </Box>
              )}

              {session?.gameType === 'BUZZER' && (
                <Box sx={{ width: '100%', maxWidth: 640 }}>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 1 }} data-testid="buzzer-question">
                    {buzzerPayload?.question}
                  </Typography>
                  <Button
                    variant="contained"
                    onClick={() => send({ type: 'BUZZER_BUZZ' })}
                    disabled={!!buzzerPayload?.currentBuzzWinner}
                    sx={{ mb: 2 }}
                    data-testid="buzzer-buzz"
                  >
                    Buzinar
                  </Button>
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                    {(buzzerPayload?.options ?? []).map((opt, idx) => (
                      <Button
                        key={idx}
                        variant="outlined"
                        onClick={() => {
                          send({ type: 'BUZZER_ANSWER', answerIndex: idx })
                          setActionAck((s) => ({ ...s, BUZZER: Date.now() }))
                        }}
                        disabled={buzzerPayload?.currentBuzzWinner !== myConnectionId}
                        data-testid={`buzzer-option-${idx}`}
                        sx={{ justifyContent: 'flex-start', textTransform: 'none', color: '#fff', borderColor: 'rgba(255,255,255,0.2)' }}
                      >
                        {opt}
                      </Button>
                    ))}
                  </Box>
                  {actionAck.BUZZER && <Box data-testid="buzzer-action-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
                </Box>
              )}

              {session?.gameType === 'SENSORY' && (
                <Box sx={{ width: '100%', maxWidth: 640 }}>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 1 }} data-testid="sensory-desc">
                    {sensoryPayload?.description}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <TextField
                      fullWidth
                      size="small"
                      placeholder="Seu palpite"
                      value={genericTextAnswer}
                      onChange={(e) => setGenericTextAnswer(e.target.value)}
                      inputProps={{ 'data-testid': 'sensory-guess-input' }}
                    />
                    <Button
                      variant="contained"
                      onClick={() => {
                        send({ type: 'SENSORY_GUESS', guess: genericTextAnswer })
                        setGenericTextAnswer('')
                        setActionAck((s) => ({ ...s, SENSORY: Date.now() }))
                      }}
                      disabled={!genericTextAnswer.trim()}
                      data-testid="sensory-submit"
                    >
                      Enviar
                    </Button>
                  </Box>
                  {actionAck.SENSORY && <Box data-testid="sensory-action-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
                </Box>
              )}

              {session?.gameType === 'BINARY_DECISION' && (
                <Box sx={{ width: '100%', maxWidth: 640, textAlign: 'center' }}>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 2 }} data-testid="binary-statement">
                    {binaryPayload?.statementText}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
                    <Button variant="contained" color="primary" onClick={() => { send({ type: 'BINARY_DECISION', decision: true }); setActionAck((s) => ({ ...s, BINARY_DECISION: Date.now() })) }} data-testid="binary-true">
                      Verdadeiro
                    </Button>
                    <Button variant="contained" color="secondary" onClick={() => { send({ type: 'BINARY_DECISION', decision: false }); setActionAck((s) => ({ ...s, BINARY_DECISION: Date.now() })) }} data-testid="binary-false">
                      Falso
                    </Button>
                  </Box>
                  {actionAck.BINARY_DECISION && <Box data-testid="binary-action-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
                </Box>
              )}

              {session?.gameType === 'COMBINATION' && (
                <Box sx={{ width: '100%', maxWidth: 640 }}>
                  <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 1 }} data-testid="combination-stage">
                    Estágio: {combinationPayload?.currentStageType}
                  </Typography>
                  <Button
                    variant="contained"
                    onClick={() => {
                      const stageType = combinationPayload?.currentStageType
                      const stagePayload = (combinationPayload as unknown as { stagePayload?: Record<string, unknown> })?.stagePayload

                      let action: Record<string, unknown> = {}
                      if (stageType === 'BINARY_DECISION') {
                        action = { decision: !!stagePayload?.isTrue }
                      } else if (stageType === 'SEQUENCING') {
                        const correctOrder = stagePayload?.correctOrder
                        action = { orderedIds: Array.isArray(correctOrder) ? correctOrder : [] }
                      } else if (stageType === 'SURVIVAL') {
                        action = { answer: stagePayload?.originalPhrase }
                      } else if (stageType === 'DETECTIVE') {
                        action = { guess: stagePayload?.answer }
                      } else if (stageType === 'SENSORY') {
                        action = { guess: stagePayload?.answer }
                      } else if (stageType === 'ROLETRANDO') {
                        action = { guess: stagePayload?.phrase }
                      } else {
                        action = { answerIndex: 0 }
                      }

                      send({ type: 'COMBINATION_ACTION', action })
                      setActionAck((s) => ({ ...s, COMBINATION: Date.now() }))
                    }}
                    data-testid="combination-action"
                  >
                    Executar ação
                  </Button>
                  {actionAck.COMBINATION && <Box data-testid="combination-action-ok" sx={{ position: 'absolute', width: 1, height: 1, opacity: 0 }} />}
                </Box>
              )}

              {session?.phase === 'LOBBY' && (
                <Button variant="contained" color="primary" onClick={startGame} data-testid="start-game">
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
