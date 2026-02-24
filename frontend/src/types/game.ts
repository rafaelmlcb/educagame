export type GameType = 'ROLETRANDO' | 'QUIZ' | 'SHOW_DO_MILHAO'
export type GamePhase =
  | 'LOBBY'
  | 'COUNTDOWN'
  | 'PLAYING'
  | 'ROUND_END'
  | 'GAME_END'
  | 'SPINNING'
  | 'GUESSING'
  | 'QUIZ_QUESTION'
  | 'QUIZ_FEEDBACK'
  | 'QUIZ_RANKING'
  | 'MILLIONAIRE_QUESTION'

export interface Player {
  id: string
  name: string
  score: number
  connected: boolean
  host: boolean
  bot?: boolean
}

export interface RoletrandoPayload {
  phrase?: string
  revealed?: string[]
  segmentIndex?: number
  segment?: { label?: string; type?: string; value?: number }
  segmentType?: string
  segmentValue?: number
  solvedBy?: string
}

export interface QuizPayload {
  question?: string
  options?: string[]
  correctIndex?: number
  timeLimitMs?: number
  questionIndex?: number
  responses?: Record<string, { answerIndex: number; points: number; correct?: boolean }>
  roundRanking?: { id: string; name: string; score: number }[]
  finalRanking?: { id: string; name: string; score: number }[]
}

export interface MillionairePayload {
  level?: number
  question?: string
  options?: string[]
  correctIndex?: number
  value?: number
  guaranteedPrize?: number
  lifeline50Used?: boolean
  lifelineUniUsed?: boolean
  lifelineSkipUsed?: boolean
  removedOptions?: number[]
  audiencePercents?: number[]
  won?: boolean
  finalPrize?: number
}

export interface GameSession {
  roomId: string
  theme: string
  gameType: GameType
  hostConnectionId: string | null
  players: Player[]
  phase: GamePhase
  currentTurnIndex: number
  gamePayload?: unknown
  roundStartedAt?: number
}

export interface Room {
  roomId: string
  theme: string
  gameType: GameType
  playerCount: number
  maxPlayers: number
  isPrivate: boolean
}

export interface WsOutbound {
  type: string
  payload?: unknown
}

export interface WheelSegment {
  label: string
  value: number
  color?: string
  type?: string
}
