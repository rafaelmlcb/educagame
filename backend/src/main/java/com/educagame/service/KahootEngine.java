package com.educagame.service;

import com.educagame.model.GamePhase;
import com.educagame.model.GameSession;
import com.educagame.model.GameType;
import com.educagame.model.Player;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kahoot-style speed quiz game with buzzer mechanics and real-time scoring.
 * Focuses on fast responses and competitive multiplayer experience.
 */
@ApplicationScoped
public class KahootEngine implements GameEngineInterface {

    private static final Logger LOG = Logger.getLogger(KahootEngine.class);
    private static final int DEFAULT_TIME_MS = 20000; // Shorter than regular quiz for more urgency
    private static final int BASE_POINTS = 1000;
    private static final int SPEED_BONUS_MULTIPLIER = 2;

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.QUIZ_SPEED || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        
        List<Map<String, Object>> questions = dataLoaderService.getQuizQuestions(session.getTheme());
        if (questions == null) questions = List.of();

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("questions", questions);
        payload.put("questionIndex", 0);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        payload.put("roundScores", new ArrayList<Map<String, Object>>());
        payload.put("buzzOrder", new ArrayList<String>()); // Track buzz order
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Kahoot started in room %s with %d questions", session.getRoomId(), questions.size());
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.QUIZ_QUESTION);
        showQuestion(session, 0);
    }

    @SuppressWarnings("unchecked")
    private void showQuestion(GameSession session, int index) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        List<Map<String, Object>> questions = (List<Map<String, Object>>) payload.get("questions");
        if (questions == null || index >= questions.size()) {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
            return;
        }
        payload.put("questionIndex", index);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        payload.put("buzzOrder", new ArrayList<String>()); // Reset buzz order for new question
        Map<String, Object> q = questions.get(index);
        payload.put("question", q.get("question"));
        payload.put("options", q.get("options"));
        payload.put("correctIndex", q.get("correctIndex"));
        int timeMs = q.get("timeLimitMs") != null ? ((Number) q.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        payload.put("timeLimitMs", timeMs);
        session.setRoundStartedAt(System.currentTimeMillis());
    }

    /**
     * Handle player buzz - first to buzz gets priority in answering.
     */
    @SuppressWarnings("unchecked")
    public boolean playerBuzz(GameSession session, String connectionId) {
        if (session.getGameType() != GameType.QUIZ_SPEED || session.getPhase() != GamePhase.QUIZ_QUESTION) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        List<String> buzzOrder = (List<String>) payload.get("buzzOrder");
        if (buzzOrder == null) buzzOrder = new ArrayList<>();
        
        // Check if player already buzzed
        if (buzzOrder.contains(connectionId)) return false;
        
        // Add player to buzz order
        buzzOrder.add(connectionId);
        payload.put("buzzOrder", buzzOrder);
        
        LOG.infof("Player %s buzzed, position: %d", connectionId, buzzOrder.size());
        return true;
    }

    /**
     * Submit answer with speed-based scoring and buzz order consideration.
     */
    @SuppressWarnings("unchecked")
    public boolean submitAnswer(GameSession session, String connectionId, int answerIndex) {
        if (session.getGameType() != GameType.QUIZ_SPEED || session.getPhase() != GamePhase.QUIZ_QUESTION) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        if (responses.containsKey(connectionId)) return true; // already answered

        Integer correctIdx = payload.get("correctIndex") != null ? ((Number) payload.get("correctIndex")).intValue() : null;
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        long started = session.getRoundStartedAt() != null ? session.getRoundStartedAt() : System.currentTimeMillis();
        long now = System.currentTimeMillis();
        long elapsed = Math.min(now - started, timeLimitMs);
        long timeRemaining = Math.max(0, timeLimitMs - elapsed);

        // Calculate buzz order bonus
        List<String> buzzOrder = (List<String>) payload.get("buzzOrder");
        int buzzPosition = buzzOrder != null ? buzzOrder.indexOf(connectionId) : -1;
        int buzzBonus = buzzPosition >= 0 ? Math.max(0, (5 - buzzPosition) * 100) : 0;

        int points = 0;
        if (correctIdx != null && answerIndex == correctIdx) {
            // Speed-based scoring with buzz bonus
            int speedPoints = (int) (BASE_POINTS * timeRemaining / timeLimitMs);
            points = speedPoints + buzzBonus;
            
            // Bonus for being first or second to buzz
            if (buzzPosition == 0) points *= SPEED_BONUS_MULTIPLIER;
            else if (buzzPosition == 1) points = (int) (points * 1.5);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("answerIndex", answerIndex);
        resp.put("receivedAt", now);
        resp.put("points", points);
        resp.put("correct", correctIdx != null && answerIndex == correctIdx);
        resp.put("buzzPosition", buzzPosition);
        resp.put("speedBonus", buzzBonus);
        responses.put(connectionId, resp);

        Player p = session.getPlayers().stream().filter(pl -> pl.getId().equals(connectionId)).findFirst().orElse(null);
        if (p != null) p.addScore(points);
        
        LOG.infof("Kahoot answer room=%s player=%s points=%d buzzPos=%d", session.getRoomId(), connectionId, points, buzzPosition);
        return true;
    }

    /**
     * Check if all players have answered or time is up.
     */
    @SuppressWarnings("unchecked")
    public boolean shouldEndQuestion(GameSession session) {
        if (session.getGameType() != GameType.QUIZ_SPEED || session.getPhase() != GamePhase.QUIZ_QUESTION) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        long started = session.getRoundStartedAt() != null ? session.getRoundStartedAt() : System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - started;
        
        // End question if time is up or all players answered
        boolean timeUp = elapsed >= timeLimitMs;
        if (timeUp) return true;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        
        // Check if all non-bot players have answered
        long nonBotCount = session.getPlayers().stream().filter(p -> !p.isBot()).count();
        return responses.size() >= nonBotCount;
    }

    /**
     * Move to next question or end game.
     */
    @SuppressWarnings("unchecked")
    public void nextQuestion(GameSession session) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        
        int idx = payload.get("questionIndex") != null ? ((Number) payload.get("questionIndex")).intValue() : 0;
        List<Map<String, Object>> questions = (List<Map<String, Object>>) payload.get("questions");
        
        if (questions != null && idx + 1 < questions.size()) {
            showQuestion(session, idx + 1);
            session.setPhase(GamePhase.QUIZ_QUESTION);
        } else {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
        }
    }

    private List<Map<String, Object>> buildRanking(GameSession session) {
        return session.getPlayers().stream()
                .filter(p -> !p.isBot())
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .map(p -> Map.<String, Object>of("id", p.getId(), "name", p.getName(), "score", p.getScore()))
                .toList();
    }

    @Override
    public boolean supports(GameType gameType) {
        return gameType == GameType.QUIZ_SPEED;
    }

    @Override
    public GameType getSupportedGameType() {
        return GameType.QUIZ_SPEED;
    }
}
