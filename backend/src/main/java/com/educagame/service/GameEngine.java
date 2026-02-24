package com.educagame.service;

import com.educagame.model.GamePhase;
import com.educagame.model.GameSession;
import com.educagame.model.GameType;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Orchestrates game start; delegates to game-specific engines.
 */
@ApplicationScoped
public class GameEngine {

    private static final Logger LOG = Logger.getLogger(GameEngine.class);

    @Inject
    RoletrandoEngine roletrandoEngine;
    @Inject
    QuizEngine quizEngine;
    @Inject
    MillionaireEngine millionaireEngine;

    public void startGame(GameSession session) {
        if (session.getPhase() != GamePhase.LOBBY) return;
        if (session.getGameType() == GameType.ROLETRANDO) {
            roletrandoEngine.startGame(session);
        } else if (session.getGameType() == GameType.QUIZ) {
            quizEngine.startGame(session);
        } else if (session.getGameType() == GameType.SHOW_DO_MILHAO) {
            millionaireEngine.startGame(session);
        } else {
            session.setPhase(GamePhase.COUNTDOWN);
            session.setCurrentTurnIndex(0);
            session.setRoundStartedAt(System.currentTimeMillis());
            LOG.infof("Game started in room %s", session.getRoomId());
        }
    }

    public void transitionToPlaying(GameSession session) {
        if (session.getGameType() == GameType.ROLETRANDO) {
            roletrandoEngine.transitionToPlaying(session);
        } else if (session.getGameType() == GameType.QUIZ) {
            quizEngine.transitionToPlaying(session);
        } else if (session.getGameType() == GameType.SHOW_DO_MILHAO) {
            millionaireEngine.transitionToPlaying(session);
        } else {
            session.setPhase(GamePhase.PLAYING);
            session.setRoundStartedAt(System.currentTimeMillis());
        }
    }
}
