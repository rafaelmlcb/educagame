package com.educagame.service;

import com.educagame.model.GameSession;
import com.educagame.model.GamePhase;
import com.educagame.model.GameType;
import com.educagame.model.Player;
import com.educagame.model.WsOutbound;
import com.educagame.resource.GameBroadcaster;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules bot moves for Roletrando with human-like delay.
 */
@ApplicationScoped
public class RoletrandoBotScheduler {

    private static final Logger LOG = Logger.getLogger(RoletrandoBotScheduler.class);
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "roletrando-bot");
        t.setDaemon(true);
        return t;
    });
    private static final Random random = new Random();

    @Inject
    RoomManager roomManager;
    @Inject
    RoletrandoEngine roletrandoEngine;
    @Inject
    GameBroadcaster broadcaster;

    public void scheduleBotTurnIfNeeded(String roomId) {
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.ROLETRANDO) return;
            List<Player> players = session.getPlayers();
            if (players.isEmpty()) return;
            int idx = session.getCurrentTurnIndex() % players.size();
            Player current = players.get(idx);
            if (!current.isBot()) return;

            int delayMs = 1500 + random.nextInt(2500);
            scheduler.schedule(() -> executeBotTurn(roomId), delayMs, TimeUnit.MILLISECONDS);
        });
    }

    @SuppressWarnings("unchecked")
    private void executeBotTurn(String roomId) {
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.ROLETRANDO) return;
            List<Player> players = session.getPlayers();
            int idx = session.getCurrentTurnIndex() % players.size();
            Player bot = players.get(idx);
            if (!bot.isBot()) return;

            String botId = bot.getId();
            GamePhase phase = session.getPhase();

            if (phase == GamePhase.PLAYING) {
                Map<String, Object> spinResult = roletrandoEngine.spinWheel(session, botId);
                if (spinResult != null) {
                    broadcaster.broadcastToRoom(roomId, WsOutbound.event("WHEEL_SPUN", spinResult));
                    broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
                    scheduleBotTurnIfNeeded(roomId);
                }
                return;
            }

            if (phase == GamePhase.GUESSING) {
                Object payload = session.getGamePayload();
                if (!(payload instanceof Map)) return;
                Map<String, Object> map = (Map<String, Object>) payload;
                String phrase = (String) map.get("phrase");
                if (phrase == null) return;

                Object rev = map.get("revealed");
                Collection<String> revealed = rev instanceof Collection ? (Collection<String>) rev : List.of();
                String revealedStr = String.join("", revealed);

                String letters = "AEIOURSTNM";
                for (int i = 0; i < letters.length(); i++) {
                    char c = letters.charAt(i);
                    if (!revealedStr.contains(String.valueOf(c)) && phrase.indexOf(c) >= 0) {
                        roletrandoEngine.processGuess(session, botId, c);
                        broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
                        if (session.getPhase() == GamePhase.GAME_END) return;
                        scheduleBotTurnIfNeeded(roomId);
                        return;
                    }
                }
                for (char c = 'A'; c <= 'Z'; c++) {
                    if (!revealedStr.contains(String.valueOf(c)) && phrase.indexOf(c) >= 0) {
                        roletrandoEngine.processGuess(session, botId, c);
                        broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
                        if (session.getPhase() == GamePhase.GAME_END) return;
                        scheduleBotTurnIfNeeded(roomId);
                        return;
                    }
                }
                if (random.nextBoolean() && phrase.length() <= 12) {
                    roletrandoEngine.processSolve(session, botId, phrase);
                    broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
                } else {
                    char randomLetter = (char) ('A' + random.nextInt(26));
                    roletrandoEngine.processGuess(session, botId, randomLetter);
                    broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
                    if (session.getPhase() != GamePhase.GAME_END) scheduleBotTurnIfNeeded(roomId);
                }
            }
        });
    }
}
