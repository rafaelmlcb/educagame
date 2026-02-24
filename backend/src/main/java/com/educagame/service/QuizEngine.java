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
 * Quiz multiplayer: synchronous questions, speed-based scoring, host advances stages.
 */
@ApplicationScoped
public class QuizEngine {

    private static final Logger LOG = Logger.getLogger(QuizEngine.class);
    private static final int DEFAULT_TIME_MS = 15000;
    private static final int BASE_POINTS = 1000;

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.QUIZ || session.getPhase() != com.educagame.model.GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        List<Map<String, Object>> questions = dataLoaderService.getQuizQuestions(session.getTheme());
        if (questions == null) questions = List.of();

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("questions", questions);
        payload.put("questionIndex", 0);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        payload.put("roundScores", new ArrayList<Map<String, Object>>());
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Quiz started in room %s with %d questions", session.getRoomId(), questions.size());
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
        Map<String, Object> q = questions.get(index);
        payload.put("question", q.get("question"));
        payload.put("options", q.get("options"));
        payload.put("correctIndex", q.get("correctIndex"));
        int timeMs = q.get("timeLimitMs") != null ? ((Number) q.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        payload.put("timeLimitMs", timeMs);
        session.setRoundStartedAt(System.currentTimeMillis());
    }

    /** Returns true if registered (first answer only). */
    @SuppressWarnings("unchecked")
    public boolean submitAnswer(GameSession session, String connectionId, int answerIndex) {
        if (session.getGameType() != GameType.QUIZ || session.getPhase() != GamePhase.QUIZ_QUESTION) return false;
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

        int points = 0;
        if (correctIdx != null && answerIndex == correctIdx) {
            points = (int) (BASE_POINTS * timeRemaining / timeLimitMs);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("answerIndex", answerIndex);
        resp.put("receivedAt", now);
        resp.put("points", points);
        resp.put("correct", correctIdx != null && answerIndex == correctIdx);
        responses.put(connectionId, resp);

        Player p = session.getPlayers().stream().filter(pl -> pl.getId().equals(connectionId)).findFirst().orElse(null);
        if (p != null) p.addScore(points);
        LOG.debugf("Quiz answer room=%s player=%s points=%d", session.getRoomId(), connectionId, points);
        return true;
    }

    /** Host advances: QUESTION -> FEEDBACK -> RANKING -> next QUESTION or GAME_END. */
    @SuppressWarnings("unchecked")
    public void hostNextStage(GameSession session, String connectionId) {
        if (session.getGameType() != GameType.QUIZ) return;
        if (!connectionId.equals(session.getHostConnectionId())) return;

        GamePhase phase = session.getPhase();
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;

        if (phase == GamePhase.QUIZ_QUESTION) {
            session.setPhase(GamePhase.QUIZ_FEEDBACK);
            return;
        }
        if (phase == GamePhase.QUIZ_FEEDBACK) {
            session.setPhase(GamePhase.QUIZ_RANKING);
            payload.put("roundRanking", buildRanking(session));
            return;
        }
        if (phase == GamePhase.QUIZ_RANKING) {
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
    }

    private List<Map<String, Object>> buildRanking(GameSession session) {
        return session.getPlayers().stream()
                .filter(p -> !p.isBot())
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .map(p -> Map.<String, Object>of("id", p.getId(), "name", p.getName(), "score", p.getScore()))
                .toList();
    }
}
