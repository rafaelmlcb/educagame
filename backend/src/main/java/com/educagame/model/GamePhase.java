package com.educagame.model;

/**
 * Lifecycle phase of a game session.
 * Roletrando: LOBBY -> COUNTDOWN -> SPINNING -> GUESSING -> (repeat or ROUND_END) -> GAME_END
 * Quiz: LOBBY -> COUNTDOWN -> QUIZ_QUESTION -> QUIZ_FEEDBACK -> QUIZ_RANKING -> (next or GAME_END)
 * Millionaire: LOBBY -> COUNTDOWN -> MILLIONAIRE_QUESTION -> (next level or GAME_END)
 */
public enum GamePhase {
    LOBBY,
    COUNTDOWN,
    /** Roletrando: wheel spinning */
    SPINNING,
    /** Roletrando: current player may GUESS or SOLVE */
    GUESSING,
    PLAYING,
    ROUND_END,
    GAME_END,
    /** Quiz: showing question, waiting answers */
    QUIZ_QUESTION,
    /** Quiz: showing correct answer and who scored */
    QUIZ_FEEDBACK,
    /** Quiz: showing round/acumulated ranking */
    QUIZ_RANKING,
    /** Show do Milhão: waiting answer */
    MILLIONAIRE_QUESTION
}
