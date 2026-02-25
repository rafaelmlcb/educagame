package com.educagame.model;

/**
 * Supported game types in EducaGame ecosystem.
 */
public enum GameType {
    ROLETRANDO,
    QUIZ_SPEED,      // Kahoot-style speed quiz
    QUIZ_INCREMENTAL, // Show do Milhão-style incremental difficulty
    SURVIVAL,        // Acerte ou Caia - fill-in-the-blanks
    SEQUENCING,      // Ordenação - logical ordering
    DETECTIVE,       // Dedução progressiva
    BUZZER,          // Passa ou Repassa - first-to-buzzer
    SENSORY,         // Sensorial - sound/image identification
    BINARY_DECISION,  // Fato ou Fake - true/false judgment
    COMBINATION      // Multi-stage combination game
}
