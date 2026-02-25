package com.educagame.service;

import com.educagame.model.GamePhase;
import com.educagame.model.GameSession;
import com.educagame.model.GameType;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Orchestrates game start; delegates to game-specific engines via registry.
 * Legacy compatibility layer - use GameEngineRegistry directly for new features.
 */
@ApplicationScoped
public class GameEngine {

    private static final Logger LOG = Logger.getLogger(GameEngine.class);

    @Inject
    GameEngineRegistry registry;

    public void startGame(GameSession session) {
        if (session.getPhase() != GamePhase.LOBBY) return;
        
        boolean started = registry.startGame(session);
        if (!started) {
            // Fallback for unsupported game types
            session.setPhase(GamePhase.COUNTDOWN);
            session.setCurrentTurnIndex(0);
            session.setRoundStartedAt(System.currentTimeMillis());
            LOG.infof("Game started in room %s (fallback mode)", session.getRoomId());
        }
    }

    public void transitionToPlaying(GameSession session) {
        boolean transitioned = registry.transitionToPlaying(session);
        if (!transitioned) {
            // Fallback for unsupported game types
            session.setPhase(GamePhase.PLAYING);
            session.setRoundStartedAt(System.currentTimeMillis());
            LOG.infof("Game transitioned to playing in room %s (fallback mode)", session.getRoomId());
        }
    }
}
