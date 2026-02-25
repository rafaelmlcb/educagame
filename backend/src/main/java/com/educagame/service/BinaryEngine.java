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
 * Binary Decision game (Fato ou Fake style) - Judge if statements are true or false.
 * Tests critical thinking and fact-checking skills.
 */
@ApplicationScoped
public class BinaryEngine implements GameEngineInterface {

    private static final Logger LOG = Logger.getLogger(BinaryEngine.class);
    private static final int DEFAULT_TIME_MS = 8000; // Quick decisions
    private static final int BASE_POINTS = 400;
    private static final int STREAK_BONUS = 100;
    private static final int WRONG_ANSWER_PENALTY = 150;

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.BINARY_DECISION || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        
        List<Map<String, Object>> statements = dataLoaderService.getStatements(session.getTheme());
        if (statements == null) statements = createDefaultStatements();

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("statements", statements);
        payload.put("statementIndex", 0);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        payload.put("roundScores", new ArrayList<Map<String, Object>>());
        payload.put("playerStreaks", new HashMap<String, Integer>());
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Binary decision game started in room %s with %d statements", session.getRoomId(), statements.size());
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.PLAYING);
        showStatement(session, 0);
    }

    @SuppressWarnings("unchecked")
    private void showStatement(GameSession session, int index) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        List<Map<String, Object>> statements = (List<Map<String, Object>>) payload.get("statements");
        if (statements == null || index >= statements.size()) {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
            return;
        }
        
        payload.put("statementIndex", index);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        
        Map<String, Object> statement = statements.get(index);
        payload.put("statementText", statement.get("text"));
        payload.put("isTrue", statement.get("isTrue"));
        payload.put("explanation", statement.get("explanation"));
        payload.put("category", statement.get("category"));
        payload.put("difficulty", statement.get("difficulty"));
        
        int timeMs = statement.get("timeLimitMs") != null ? ((Number) statement.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        payload.put("timeLimitMs", timeMs);
        session.setRoundStartedAt(System.currentTimeMillis());
        
        LOG.infof("Binary statement %d: %s (Truth: %s)", index, statement.get("text"), statement.get("isTrue"));
    }

    /**
     * Submit binary decision (true/false).
     */
    @SuppressWarnings("unchecked")
    public boolean submitDecision(GameSession session, String connectionId, boolean decision) {
        if (session.getGameType() != GameType.BINARY_DECISION || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        if (responses.containsKey(connectionId)) return true; // already answered

        Boolean isTrue = (Boolean) payload.get("isTrue");
        long responseTime = System.currentTimeMillis() - session.getRoundStartedAt();
        
        boolean correct = decision == isTrue;
        
        // Calculate points
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        int timeBonus = Math.max(0, (int) ((timeLimitMs - responseTime) * BASE_POINTS / timeLimitMs / 2));
        
        // Handle streaks
        Map<String, Integer> playerStreaks = (Map<String, Integer>) payload.get("playerStreaks");
        if (playerStreaks == null) playerStreaks = new HashMap<>();
        
        int currentStreak = playerStreaks.getOrDefault(connectionId, 0);
        int streakBonus = 0;
        
        if (correct) {
            currentStreak++;
            streakBonus = currentStreak * STREAK_BONUS;
            playerStreaks.put(connectionId, currentStreak);
        } else {
            currentStreak = 0;
            playerStreaks.put(connectionId, currentStreak);
        }
        
        int basePoints = correct ? BASE_POINTS : -WRONG_ANSWER_PENALTY;
        int totalPoints = basePoints + timeBonus + streakBonus;

        Map<String, Object> resp = new HashMap<>();
        resp.put("decision", decision);
        resp.put("correct", correct);
        resp.put("responseTime", responseTime);
        resp.put("streak", currentStreak);
        resp.put("points", totalPoints);
        resp.put("receivedAt", System.currentTimeMillis());
        responses.put(connectionId, resp);

        Player player = session.getPlayers().stream().filter(p -> p.getId().equals(connectionId)).findFirst().orElse(null);
        if (player != null && !player.isBot()) {
            player.addScore(totalPoints);
            LOG.infof("Binary decision from %s: %s -> %s (streak: %d, %d points, %dms)", 
                    player.getName(), decision, correct ? "CORRECT" : "WRONG", currentStreak, totalPoints, responseTime);
        }
        
        return true;
    }

    /**
     * Check if round should end (all players answered or time up).
     */
    @SuppressWarnings("unchecked")
    public boolean shouldEndRound(GameSession session) {
        if (session.getGameType() != GameType.BINARY_DECISION || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        long elapsed = System.currentTimeMillis() - session.getRoundStartedAt();
        
        // End round if time is up
        if (elapsed >= timeLimitMs) return true;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        
        // Check if all non-bot players have answered
        long nonBotCount = session.getPlayers().stream().filter(p -> !p.isBot()).count();
        return responses.size() >= nonBotCount;
    }

    /**
     * Move to next statement or end game.
     */
    @SuppressWarnings("unchecked")
    public void nextStatement(GameSession session) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        
        int idx = payload.get("statementIndex") != null ? ((Number) payload.get("statementIndex")).intValue() : 0;
        List<Map<String, Object>> statements = (List<Map<String, Object>>) payload.get("statements");
        
        if (statements != null && idx + 1 < statements.size()) {
            showStatement(session, idx + 1);
        } else {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
        }
    }

    /**
     * Create default statements if none provided.
     */
    private List<Map<String, Object>> createDefaultStatements() {
        List<Map<String, Object>> statements = new ArrayList<>();
        
        // Science statements
        Map<String, Object> s1 = new HashMap<>();
        s1.put("text", "The Earth is flat");
        s1.put("isTrue", false);
        s1.put("explanation", "The Earth is spherical, not flat. This was proven by ancient Greek astronomers and confirmed during circumnavigation.");
        s1.put("category", "Science");
        s1.put("difficulty", 1);
        s1.put("timeLimitMs", 5000);
        statements.add(s1);
        
        Map<String, Object> s2 = new HashMap<>();
        s2.put("text", "Water boils at 100°C at sea level");
        s2.put("isTrue", true);
        s2.put("explanation", "At standard atmospheric pressure (1 atm), water boils at exactly 100°C (212°F).");
        s2.put("category", "Science");
        s2.put("difficulty", 1);
        s2.put("timeLimitMs", 4000);
        statements.add(s2);
        
        // History statements
        Map<String, Object> h1 = new HashMap<>();
        h1.put("text", "Napoleon Bonaparte won the Battle of Waterloo");
        h1.put("isTrue", false);
        h1.put("explanation", "Napoleon was decisively defeated at the Battle of Waterloo in 1815, ending his rule.");
        h1.put("category", "History");
        h1.put("difficulty", 2);
        h1.put("timeLimitMs", 6000);
        statements.add(h1);
        
        Map<String, Object> h2 = new HashMap<>();
        h2.put("text", "The Berlin Wall fell in 1989");
        h2.put("isTrue", true);
        h2.put("explanation", "The Berlin Wall fell on November 9, 1989, marking the beginning of German reunification.");
        h2.put("category", "History");
        h2.put("difficulty", 2);
        h2.put("timeLimitMs", 5000);
        statements.add(h2);
        
        // Geography statements
        Map<String, Object> g1 = new HashMap<>();
        g1.put("text", "The Amazon River is the longest river in the world");
        g1.put("isTrue", true);
        g1.put("explanation", "The Amazon River is approximately 6,400 km long, making it the longest river in the world.");
        s1.put("category", "Geography");
        s1.put("difficulty", 2);
        s1.put("timeLimitMs", 7000);
        statements.add(g1);
        
        Map<String, Object> g2 = new HashMap<>();
        g2.put("text", "Australia is the smallest continent");
        g2.put("isTrue", true);
        g2.put("explanation", "Australia is indeed the smallest continent, often referred to as an island continent.");
        g2.put("category", "Geography");
        g2.put("difficulty", 1);
        g2.put("timeLimitMs", 4000);
        statements.add(g2);
        
        return statements;
    }

    private List<Map<String, Object>> buildRanking(GameSession session) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        Map<String, Integer> playerStreaks = payload != null ? 
                (Map<String, Integer>) payload.get("playerStreaks") : new HashMap<>();
        
        return session.getPlayers().stream()
                .filter(p -> !p.isBot())
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .map(p -> {
                    Map<String, Object> playerData = new HashMap<>();
                    playerData.put("id", p.getId());
                    playerData.put("name", p.getName());
                    playerData.put("score", p.getScore());
                    playerData.put("bestStreak", playerStreaks.getOrDefault(p.getId(), 0));
                    return playerData;
                })
                .toList();
    }

    @Override
    public boolean supports(GameType gameType) {
        return gameType == GameType.BINARY_DECISION;
    }

    @Override
    public GameType getSupportedGameType() {
        return GameType.BINARY_DECISION;
    }
}
