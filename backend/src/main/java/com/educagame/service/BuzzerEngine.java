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
 * Buzzer game (Passa ou Repassa style) - First to buzz gets to answer.
 * Fast-paced competitive quiz with pressure elements.
 */
@ApplicationScoped
public class BuzzerEngine implements GameEngineInterface {

    private static final Logger LOG = Logger.getLogger(BuzzerEngine.class);
    private static final int DEFAULT_TIME_MS = 15000;
    private static final int BASE_POINTS = 600;
    private static final int BUZZER_BONUS = 300;
    private static final int WRONG_ANSWER_PENALTY = 200;

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.BUZZER || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        
        List<Map<String, Object>> questions = dataLoaderService.getQuizQuestions(session.getTheme());
        if (questions == null) questions = createDefaultBuzzerQuestions();

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("questions", questions);
        payload.put("questionIndex", 0);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        payload.put("roundScores", new ArrayList<Map<String, Object>>());
        payload.put("buzzOrder", new ArrayList<String>());
        payload.put("passCount", 0);
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Buzzer game started in room %s with %d questions", session.getRoomId(), questions.size());
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.PLAYING);
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
        payload.put("buzzOrder", new ArrayList<String>());
        payload.remove("currentBuzzWinner");
        payload.put("passCount", 0);
        
        Map<String, Object> q = questions.get(index);
        payload.put("question", q.get("question"));
        payload.put("options", q.get("options"));
        payload.put("correctIndex", q.get("correctIndex"));
        
        int timeMs = q.get("timeLimitMs") != null ? ((Number) q.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        payload.put("timeLimitMs", timeMs);
        session.setRoundStartedAt(System.currentTimeMillis());
        
        LOG.infof("Buzzer question %d: %s", index, q.get("question"));
    }

    /**
     * Handle player buzz - first to buzz wins the right to answer.
     */
    @SuppressWarnings("unchecked")
    public boolean playerBuzz(GameSession session, String connectionId) {
        if (session.getGameType() != GameType.BUZZER || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        String currentBuzzWinner = (String) payload.get("currentBuzzWinner");
        if (currentBuzzWinner != null) return false; // Someone already buzzed
        
        List<String> buzzOrder = (List<String>) payload.get("buzzOrder");
        if (buzzOrder == null) buzzOrder = new ArrayList<>();
        
        // First player to buzz wins
        buzzOrder.add(connectionId);
        payload.put("buzzOrder", buzzOrder);
        payload.put("currentBuzzWinner", connectionId);
        
        LOG.infof("Player %s buzzed first!", connectionId);
        return true;
    }

    /**
     * Submit answer from buzz winner.
     */
    @SuppressWarnings("unchecked")
    public boolean submitAnswer(GameSession session, String connectionId, int answerIndex) {
        if (session.getGameType() != GameType.BUZZER || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        String currentBuzzWinner = (String) payload.get("currentBuzzWinner");
        if (!connectionId.equals(currentBuzzWinner)) return false; // Only buzz winner can answer
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        if (responses.containsKey(connectionId)) return true; // already answered

        Integer correctIdx = payload.get("correctIndex") != null ? ((Number) payload.get("correctIndex")).intValue() : null;
        long buzzTime = System.currentTimeMillis() - session.getRoundStartedAt();
        
        boolean correct = correctIdx != null && answerIndex == correctIdx;
        
        // Calculate points
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        int timeBonus = Math.max(0, (int) ((timeLimitMs - buzzTime) * BUZZER_BONUS / timeLimitMs));
        
        int points;
        if (correct) {
            points = BASE_POINTS + timeBonus + BUZZER_BONUS;
        } else {
            points = -WRONG_ANSWER_PENALTY; // Penalty for wrong answer
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("answerIndex", answerIndex);
        resp.put("correct", correct);
        resp.put("points", points);
        resp.put("buzzTime", buzzTime);
        resp.put("receivedAt", System.currentTimeMillis());
        responses.put(connectionId, resp);

        Player player = session.getPlayers().stream().filter(p -> p.getId().equals(connectionId)).findFirst().orElse(null);
        if (player != null && !player.isBot()) {
            player.addScore(points);
            LOG.infof("Buzzer answer from %s: %s (%d points, buzz time: %dms)", 
                    player.getName(), correct ? "CORRECT" : "WRONG", points, buzzTime);
        }
        
        return true;
    }

    /**
     * Pass the question to next player in buzz order.
     */
    @SuppressWarnings("unchecked")
    public boolean passQuestion(GameSession session, String connectionId) {
        if (session.getGameType() != GameType.BUZZER || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        String currentBuzzWinner = (String) payload.get("currentBuzzWinner");
        if (!connectionId.equals(currentBuzzWinner)) return false; // Only buzz winner can pass
        
        List<String> buzzOrder = (List<String>) payload.get("buzzOrder");
        if (buzzOrder == null || buzzOrder.size() <= 1) return false; // No one else to pass to
        
        // Find next player in buzz order
        int currentIndex = buzzOrder.indexOf(connectionId);
        if (currentIndex < buzzOrder.size() - 1) {
            String nextPlayer = buzzOrder.get(currentIndex + 1);
            payload.put("currentBuzzWinner", nextPlayer);
            
            int passCount = payload.get("passCount") != null ? ((Number) payload.get("passCount")).intValue() : 0;
            payload.put("passCount", passCount + 1);
            
            LOG.infof("Question passed to %s (pass #%d)", nextPlayer, passCount + 1);
            return true;
        }
        
        return false;
    }

    /**
     * Check if round should end.
     */
    @SuppressWarnings("unchecked")
    public boolean shouldEndRound(GameSession session) {
        if (session.getGameType() != GameType.BUZZER || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        long elapsed = System.currentTimeMillis() - session.getRoundStartedAt();
        
        // End round if time is up
        if (elapsed >= timeLimitMs) return true;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        
        // Check if someone answered correctly
        boolean correctAnswer = responses.values().stream()
                .anyMatch(resp -> Boolean.TRUE.equals(resp.get("correct")));
        
        if (correctAnswer) return true;
        
        // Check if all buzz order players have passed or answered
        List<String> buzzOrder = (List<String>) payload.get("buzzOrder");
        int passCount = payload.get("passCount") != null ? ((Number) payload.get("passCount")).intValue() : 0;
        
        return passCount >= buzzOrder.size() - 1;
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
        } else {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
        }
    }

    /**
     * Create default buzzer questions if none provided.
     */
    private List<Map<String, Object>> createDefaultBuzzerQuestions() {
        List<Map<String, Object>> questions = new ArrayList<>();
        
        Map<String, Object> q1 = new HashMap<>();
        q1.put("question", "What is the capital of Brazil?");
        q1.put("options", Arrays.asList("São Paulo", "Rio de Janeiro", "Brasília", "Salvador"));
        q1.put("correctIndex", 2);
        q1.put("timeLimitMs", 10000);
        questions.add(q1);
        
        Map<String, Object> q2 = new HashMap<>();
        q2.put("question", "Which planet is known as the Red Planet?");
        q2.put("options", Arrays.asList("Venus", "Mars", "Jupiter", "Saturn"));
        q2.put("correctIndex", 1);
        q2.put("timeLimitMs", 8000);
        questions.add(q2);
        
        Map<String, Object> q3 = new HashMap<>();
        q3.put("question", "What is 2 + 2 × 2?");
        q3.put("options", Arrays.asList("6", "8", "4", "10"));
        q3.put("correctIndex", 0);
        q3.put("timeLimitMs", 5000);
        questions.add(q3);
        
        return questions;
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
        return gameType == GameType.BUZZER;
    }

    @Override
    public GameType getSupportedGameType() {
        return GameType.BUZZER;
    }
}
