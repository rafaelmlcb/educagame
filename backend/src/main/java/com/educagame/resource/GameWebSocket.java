package com.educagame.resource;

import com.educagame.model.*;
import com.educagame.service.GameEngine;
import com.educagame.service.RoomManager;
import com.educagame.service.RoletrandoEngine;
import com.educagame.service.RoletrandoBotScheduler;
import com.educagame.service.QuizEngine;
import com.educagame.service.MillionaireEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.UserData;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import org.jboss.logging.Logger;
import java.util.Set;

import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;

/**
 * Single WebSocket endpoint for game. Clients send JOIN with roomId; state is broadcast per room.
 */
@WebSocket(path = "/game")
public class GameWebSocket {

    private static final Logger LOG = Logger.getLogger(GameWebSocket.class);
    private static final UserData.TypedKey<String> KEY_CONNECTION_ID = UserData.TypedKey.forString("connectionId");
    private static final UserData.TypedKey<String> KEY_ROOM_ID = UserData.TypedKey.forString("roomId");

    @Inject
    WebSocketConnection connection;
    @Inject
    RoomManager roomManager;
    @Inject
    GameEngine gameEngine;
    @Inject
    RoletrandoEngine roletrandoEngine;
    @Inject
    RoletrandoBotScheduler botScheduler;
    @Inject
    QuizEngine quizEngine;
    @Inject
    MillionaireEngine millionaireEngine;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    GameBroadcaster broadcaster;

    @OnOpen
    public void onOpen() {
        String connectionId = UUID.randomUUID().toString();
        connection.userData().put(KEY_CONNECTION_ID, connectionId);
        LOG.debugf("WebSocket opened: %s", connectionId);
    }

    @OnTextMessage
    public void onMessage(String raw) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(raw, Map.class);
            String type = (String) map.get("type");
            if (type == null) return;

            String connectionId = connection.userData().get(KEY_CONNECTION_ID);
            switch (type) {
                case "JOIN" -> handleJoin(connectionId, map);
                case "START" -> handleStart(connectionId);
                case "WHEEL_SPIN" -> handleWheelSpin(connectionId, map);
                case "GUESS" -> handleGuess(connectionId, map);
                case "SOLVE" -> handleSolve(connectionId, map);
                case "QUIZ_ANSWER" -> handleQuizAnswer(connectionId, map);
                case "QUIZ_NEXT" -> handleQuizNext(connectionId);
                case "MILLIONAIRE_ANSWER" -> handleMillionaireAnswer(connectionId, map);
                case "LIFELINE_50_50" -> handleLifeline5050(connectionId);
                case "LIFELINE_UNI" -> handleLifelineUni(connectionId);
                case "LIFELINE_SKIP" -> handleLifelineSkip(connectionId);
                case "ANSWER" -> handleAnswer(connectionId, map);
                case "PING" -> connection.sendText(toJson(WsOutbound.pong())).subscribe().asCompletionStage();
                default -> connection.sendText(toJson(WsOutbound.error("Unknown type: " + type))).subscribe().asCompletionStage();
            }
        } catch (Exception e) {
            LOG.warnf("Message handling failed: %s", e.getMessage());
            connection.sendText(toJson(WsOutbound.error("Invalid message"))).subscribe().asCompletionStage();
        }
    }

    private void handleJoin(String connectionId, Map<String, Object> map) {
        String roomId = (String) map.get("roomId");
        String playerName = (String) map.get("playerName");
        if (!ValidationUtil.isValidRoomId(roomId) || !ValidationUtil.isValidPlayerName(playerName)) {
            connection.sendText(toJson(WsOutbound.error("Invalid room or player name"))).subscribe().asCompletionStage();
            return;
        }
        if (!roomManager.joinRoom(roomId, connectionId, playerName)) {
            connection.sendText(toJson(WsOutbound.error("Could not join room"))).subscribe().asCompletionStage();
            return;
        }
        connection.userData().put(KEY_ROOM_ID, roomId);
        roomManager.getSession(roomId).ifPresent(session -> {
            connection.sendText(toJson(WsOutbound.event("JOIN_OK", Map.of("connectionId", connectionId)))).subscribe().asCompletionStage();
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
        });
    }

    private void handleStart(String connectionId) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        roomManager.getSession(roomId).ifPresent(session -> {
            if (!connectionId.equals(session.getHostConnectionId())) return;
            gameEngine.startGame(session);
            if (session.getGameType() == GameType.ROLETRANDO || session.getGameType() == GameType.QUIZ || session.getGameType() == GameType.SHOW_DO_MILHAO) {
                gameEngine.transitionToPlaying(session);
            }
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
            if (session.getGameType() == GameType.ROLETRANDO) {
                botScheduler.scheduleBotTurnIfNeeded(roomId);
            }
        });
    }

    private void handleWheelSpin(String connectionId, Map<String, Object> map) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.ROLETRANDO) return;
            java.util.Map<String, Object> spinResult = roletrandoEngine.spinWheel(session, connectionId);
            if (spinResult == null) {
                connection.sendText(toJson(WsOutbound.error("Not your turn or invalid state"))).subscribe().asCompletionStage();
                return;
            }
            broadcaster.broadcastToRoom(roomId, WsOutbound.event("WHEEL_SPUN", spinResult));
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
            botScheduler.scheduleBotTurnIfNeeded(roomId);
        });
    }

    private void handleGuess(String connectionId, Map<String, Object> map) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        Object letterObj = map.get("letter");
        String letterStr = letterObj != null ? String.valueOf(letterObj).trim() : "";
        if (!ValidationUtil.isValidGuessLetter(letterStr)) {
            connection.sendText(toJson(WsOutbound.error("Invalid letter"))).subscribe().asCompletionStage();
            return;
        }
        char letter = letterStr.toUpperCase(java.util.Locale.ROOT).charAt(0);
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.ROLETRANDO) return;
            boolean applied = roletrandoEngine.processGuess(session, connectionId, letter);
            if (!applied) {
                connection.sendText(toJson(WsOutbound.error("Invalid guess or not your turn"))).subscribe().asCompletionStage();
                return;
            }
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
            botScheduler.scheduleBotTurnIfNeeded(roomId);
        });
    }

    private void handleSolve(String connectionId, Map<String, Object> map) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        Object attemptObj = map.get("phrase");
        String attempt = attemptObj != null ? String.valueOf(attemptObj).trim() : "";
        if (attempt.isEmpty()) {
            connection.sendText(toJson(WsOutbound.error("Phrase required"))).subscribe().asCompletionStage();
            return;
        }
        if (!ValidationUtil.isValidSolvePhrase(attempt)) {
            connection.sendText(toJson(WsOutbound.error("Phrase invalid or too long"))).subscribe().asCompletionStage();
            return;
        }
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.ROLETRANDO) return;
            boolean applied = roletrandoEngine.processSolve(session, connectionId, attempt);
            if (!applied) {
                connection.sendText(toJson(WsOutbound.error("Invalid solve or not your turn"))).subscribe().asCompletionStage();
                return;
            }
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
            botScheduler.scheduleBotTurnIfNeeded(roomId);
        });
    }

    private void handleQuizAnswer(String connectionId, Map<String, Object> map) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        Object idxObj = map.get("answerIndex");
        if (idxObj == null) {
            connection.sendText(toJson(WsOutbound.error("answerIndex required"))).subscribe().asCompletionStage();
            return;
        }
        int answerIndex = ((Number) idxObj).intValue();
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.QUIZ) return;
            quizEngine.submitAnswer(session, connectionId, answerIndex);
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
        });
    }

    private void handleQuizNext(String connectionId) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.QUIZ) return;
            quizEngine.hostNextStage(session, connectionId);
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
        });
    }

    private void handleMillionaireAnswer(String connectionId, Map<String, Object> map) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        Object idxObj = map.get("answerIndex");
        if (idxObj == null) {
            connection.sendText(toJson(WsOutbound.error("answerIndex required"))).subscribe().asCompletionStage();
            return;
        }
        int answerIndex = ((Number) idxObj).intValue();
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.SHOW_DO_MILHAO) return;
            millionaireEngine.submitAnswer(session, connectionId, answerIndex);
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
        });
    }

    private void handleLifeline5050(String connectionId) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.SHOW_DO_MILHAO) return;
            millionaireEngine.lifeline50_50(session, connectionId);
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
        });
    }

    private void handleLifelineUni(String connectionId) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.SHOW_DO_MILHAO) return;
            millionaireEngine.lifelineUni(session, connectionId);
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
        });
    }

    private void handleLifelineSkip(String connectionId) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        roomManager.getSession(roomId).ifPresent(session -> {
            if (session.getGameType() != GameType.SHOW_DO_MILHAO) return;
            millionaireEngine.lifelineSkip(session, connectionId);
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
        });
    }

    private void handleAnswer(String connectionId, Map<String, Object> map) {
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId == null) return;
        roomManager.getSession(roomId).ifPresent(session -> {
            Integer answerIndex = map.get("answerIndex") != null ? ((Number) map.get("answerIndex")).intValue() : null;
            if (answerIndex != null) {
                if (session.getGameType() == GameType.QUIZ) {
                    quizEngine.submitAnswer(session, connectionId, answerIndex);
                } else if (session.getGameType() == GameType.SHOW_DO_MILHAO) {
                    millionaireEngine.submitAnswer(session, connectionId, answerIndex);
                }
            }
            broadcaster.broadcastToRoom(roomId, WsOutbound.state(session));
        });
    }

    @OnClose
    public void onClose() {
        String connectionId = connection.userData().get(KEY_CONNECTION_ID);
        String roomId = connection.userData().get(KEY_ROOM_ID);
        if (roomId != null) {
            roomManager.leaveRoom(connectionId);
            roomManager.getSession(roomId).ifPresent(session -> broadcaster.broadcastToRoom(roomId, WsOutbound.state(session)));
        }
        LOG.debugf("WebSocket closed: %s", connectionId);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
