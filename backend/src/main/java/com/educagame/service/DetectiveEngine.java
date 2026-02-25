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
 * Detetive (Detective) game - Progressive deduction with minimum clues.
 * Players identify persons, places, or objects using gradually revealed clues.
 */
@ApplicationScoped
public class DetectiveEngine implements GameEngineInterface {

    private static final Logger LOG = Logger.getLogger(DetectiveEngine.class);
    private static final int DEFAULT_TIME_MS = 45000; // Longer for thinking
    private static final int BASE_POINTS = 1000;
    private static final int CLUE_REVEAL_INTERVAL_MS = 8000;
    private static final int EARLY_SOLVE_BONUS = 500;

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.DETECTIVE || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        
        List<Map<String, Object>> mysteries = dataLoaderService.getMysteries(session.getTheme());
        if (mysteries == null) mysteries = createDefaultMysteries();

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("mysteries", mysteries);
        payload.put("mysteryIndex", 0);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        payload.put("roundScores", new ArrayList<Map<String, Object>>());
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Detective game started in room %s with %d mysteries", session.getRoomId(), mysteries.size());
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.PLAYING);
        showMystery(session, 0);
    }

    @SuppressWarnings("unchecked")
    private void showMystery(GameSession session, int index) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        List<Map<String, Object>> mysteries = (List<Map<String, Object>>) payload.get("mysteries");
        if (mysteries == null || index >= mysteries.size()) {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
            return;
        }
        
        payload.put("mysteryIndex", index);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        
        Map<String, Object> mystery = mysteries.get(index);
        String answer = (String) mystery.get("answer");
        List<String> clues = (List<String>) mystery.get("clues");
        String category = (String) mystery.get("category");
        
        payload.put("answer", answer);
        payload.put("allClues", clues);
        payload.put("revealedClues", new ArrayList<String>());
        payload.put("category", category);
        payload.put("mysteryDescription", mystery.get("description"));
        
        int timeMs = mystery.get("timeLimitMs") != null ? ((Number) mystery.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        payload.put("timeLimitMs", timeMs);
        payload.put("lastClueReveal", System.currentTimeMillis());
        session.setRoundStartedAt(System.currentTimeMillis());
        
        LOG.infof("Detective mystery %d: %s (%d clues)", index, category, clues.size());
    }

    /**
     * Reveal next clue progressively.
     */
    @SuppressWarnings("unchecked")
    public boolean revealNextClue(GameSession session) {
        if (session.getGameType() != GameType.DETECTIVE || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        List<String> allClues = (List<String>) payload.get("allClues");
        List<String> revealedClues = (List<String>) payload.get("revealedClues");
        if (revealedClues == null) revealedClues = new ArrayList<>();
        
        if (revealedClues.size() >= allClues.size()) return false; // All clues revealed
        
        String nextClue = allClues.get(revealedClues.size());
        revealedClues.add(nextClue);
        payload.put("revealedClues", revealedClues);
        payload.put("lastClueReveal", System.currentTimeMillis());
        
        LOG.infof("Revealed clue %d/%d: %s", revealedClues.size(), allClues.size(), nextClue);
        return true;
    }

    /**
     * Check if it's time to auto-reveal next clue.
     */
    @SuppressWarnings("unchecked")
    public boolean shouldAutoRevealClue(GameSession session) {
        if (session.getGameType() != GameType.DETECTIVE || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        List<String> allClues = (List<String>) payload.get("allClues");
        List<String> revealedClues = (List<String>) payload.get("revealedClues");
        if (revealedClues == null) revealedClues = new ArrayList<>();
        
        if (revealedClues.size() >= allClues.size()) return false;
        
        long lastReveal = payload.get("lastClueReveal") != null ? 
                ((Number) payload.get("lastClueReveal")).longValue() : session.getRoundStartedAt();
        
        return (System.currentTimeMillis() - lastReveal) >= CLUE_REVEAL_INTERVAL_MS;
    }

    /**
     * Submit detective answer.
     */
    @SuppressWarnings("unchecked")
    public boolean submitAnswer(GameSession session, String connectionId, String guess) {
        if (session.getGameType() != GameType.DETECTIVE || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        if (responses.containsKey(connectionId)) return true; // already answered

        String answer = (String) payload.get("answer");
        List<String> revealedClues = (List<String>) payload.get("revealedClues");
        if (revealedClues == null) revealedClues = new ArrayList<>();
        
        boolean correct = guess.trim().equalsIgnoreCase(answer.trim());
        
        // Calculate points based on clues used and time
        int cluesUsed = revealedClues.size();
        int cluePenalty = cluesUsed * 100; // Penalty for each clue revealed
        
        long elapsed = System.currentTimeMillis() - session.getRoundStartedAt();
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        long timeRemaining = Math.max(0, timeLimitMs - elapsed);
        int timeBonus = (int) (timeRemaining * BASE_POINTS / timeLimitMs / 2);
        
        int basePoints = correct ? BASE_POINTS : 0;
        int totalPoints = Math.max(0, basePoints + timeBonus - cluePenalty + EARLY_SOLVE_BONUS);

        Map<String, Object> resp = new HashMap<>();
        resp.put("guess", guess);
        resp.put("correct", correct);
        resp.put("cluesUsed", cluesUsed);
        resp.put("points", totalPoints);
        resp.put("receivedAt", System.currentTimeMillis());
        responses.put(connectionId, resp);

        Player player = session.getPlayers().stream().filter(p -> p.getId().equals(connectionId)).findFirst().orElse(null);
        if (player != null && !player.isBot()) {
            player.addScore(totalPoints);
            LOG.infof("Detective guess from %s: '%s' -> %s (%d points, %d clues)", 
                    player.getName(), guess, correct ? "CORRECT" : "WRONG", totalPoints, cluesUsed);
        }
        
        return true;
    }

    /**
     * Check if round should end (all players answered or time up).
     */
    @SuppressWarnings("unchecked")
    public boolean shouldEndRound(GameSession session) {
        if (session.getGameType() != GameType.DETECTIVE || session.getPhase() != GamePhase.PLAYING) return false;
        
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
     * Move to next mystery or end game.
     */
    @SuppressWarnings("unchecked")
    public void nextMystery(GameSession session) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        
        int idx = payload.get("mysteryIndex") != null ? ((Number) payload.get("mysteryIndex")).intValue() : 0;
        List<Map<String, Object>> mysteries = (List<Map<String, Object>>) payload.get("mysteries");
        
        if (mysteries != null && idx + 1 < mysteries.size()) {
            showMystery(session, idx + 1);
        } else {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
        }
    }

    /**
     * Create default mysteries if none provided.
     */
    private List<Map<String, Object>> createDefaultMysteries() {
        List<Map<String, Object>> mysteries = new ArrayList<>();
        
        // Person mystery
        Map<String, Object> personMystery = new HashMap<>();
        personMystery.put("category", "Person");
        personMystery.put("description", "Guess the historical figure");
        personMystery.put("answer", "Albert Einstein");
        List<String> personClues = Arrays.asList(
            "I was born in Germany",
            "I developed the theory of relativity",
            "I won the Nobel Prize in Physics",
            "My famous equation is E=mc²",
            "I'm known for my wild hair"
        );
        personMystery.put("clues", personClues);
        mysteries.add(personMystery);
        
        // Place mystery
        Map<String, Object> placeMystery = new HashMap<>();
        placeMystery.put("category", "Place");
        placeMystery.put("description", "Guess the famous landmark");
        placeMystery.put("answer", "Eiffel Tower");
        List<String> placeClues = Arrays.asList(
            "I'm located in Europe",
            "I was built for a World's Fair",
            "I'm made of iron",
            "I'm over 300 meters tall",
            "I'm the most visited paid monument in the world"
        );
        placeMystery.put("clues", placeClues);
        mysteries.add(placeMystery);
        
        // Object mystery
        Map<String, Object> objectMystery = new HashMap<>();
        objectMystery.put("category", "Object");
        objectMystery.put("description", "Guess the technological device");
        objectMystery.put("answer", "Smartphone");
        List<String> objectClues = Arrays.asList(
            "I fit in your pocket",
            "I have a touchscreen",
            "I can connect to the internet",
            "I have a camera",
            "I can make phone calls"
        );
        objectMystery.put("clues", objectClues);
        mysteries.add(objectMystery);
        
        return mysteries;
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
        return gameType == GameType.DETECTIVE;
    }

    @Override
    public GameType getSupportedGameType() {
        return GameType.DETECTIVE;
    }
}
