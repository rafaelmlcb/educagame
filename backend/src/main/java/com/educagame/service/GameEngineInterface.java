package com.educagame.service;

import com.educagame.model.GameSession;

/**
 * Interface for all game engines in EducaGame ecosystem.
 * Provides contract for game lifecycle management.
 */
public interface GameEngineInterface {
    
    /**
     * Initialize and start a new game session.
     * @param session The game session to start
     */
    void startGame(GameSession session);
    
    /**
     * Transition from countdown to playing state.
     * @param session The game session to transition
     */
    void transitionToPlaying(GameSession session);
    
    /**
     * Check if this engine supports the given game type.
     * @param gameType The game type to check
     * @return true if supported, false otherwise
     */
    boolean supports(com.educagame.model.GameType gameType);
    
    /**
     * Get the game type this engine handles.
     * @return The supported game type
     */
    com.educagame.model.GameType getSupportedGameType();
}
