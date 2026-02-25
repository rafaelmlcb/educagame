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
import java.util.stream.Collectors;

/**
 * Ordenação (Sequencing) game - Players must arrange items in logical order.
 * Supports time, size, value, and other sequential arrangements.
 */
@ApplicationScoped
public class SequencingEngine implements GameEngineInterface {

    private static final Logger LOG = Logger.getLogger(SequencingEngine.class);
    private static final int DEFAULT_TIME_MS = 25000;
    private static final int BASE_POINTS = 800;
    private static final int PER_ITEM_BONUS = 200;

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.SEQUENCING || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        
        List<Map<String, Object>> sequences = dataLoaderService.getSequences(session.getTheme());
        if (sequences == null || sequences.isEmpty()) sequences = createDefaultSequences();

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("sequences", sequences);
        payload.put("sequenceIndex", 0);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        payload.put("roundScores", new ArrayList<Map<String, Object>>());
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Sequencing game started in room %s with %d sequences", session.getRoomId(), sequences.size());
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.PLAYING);
        showSequence(session, 0);
    }

    @SuppressWarnings("unchecked")
    private void showSequence(GameSession session, int index) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        List<Map<String, Object>> sequences = (List<Map<String, Object>>) payload.get("sequences");
        if (sequences == null || index >= sequences.size()) {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
            return;
        }
        
        payload.put("sequenceIndex", index);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        
        Map<String, Object> sequence = sequences.get(index);
        List<Map<String, Object>> items = (List<Map<String, Object>>) sequence.get("items");
        String sequenceType = (String) sequence.get("type");
        
        // Shuffle items for players to arrange
        List<Map<String, Object>> shuffledItems = new ArrayList<>(items);
        Collections.shuffle(shuffledItems);
        
        payload.put("originalItems", items);
        payload.put("shuffledItems", shuffledItems);
        payload.put("sequenceType", sequenceType);
        payload.put("sequenceDescription", sequence.get("description"));
        
        int timeMs = sequence.get("timeLimitMs") != null ? ((Number) sequence.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        payload.put("timeLimitMs", timeMs);
        session.setRoundStartedAt(System.currentTimeMillis());
        
        LOG.infof("Sequencing challenge %d: %s (%d items)", index, sequenceType, items.size());
    }

    /**
     * Submit player's ordered sequence.
     */
    @SuppressWarnings("unchecked")
    public boolean submitSequence(GameSession session, String connectionId, List<String> orderedIds) {
        if (session.getGameType() != GameType.SEQUENCING || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        if (responses.containsKey(connectionId)) return true; // already answered

        List<Map<String, Object>> originalItems = (List<Map<String, Object>>) payload.get("originalItems");
        if (originalItems == null) return false;

        // Calculate score based on correct positions
        int correctPositions = 0;
        List<String> correctOrder = originalItems.stream()
                .map(item -> (String) item.get("id"))
                .collect(Collectors.toList());
        
        for (int i = 0; i < Math.min(orderedIds.size(), correctOrder.size()); i++) {
            if (orderedIds.get(i).equals(correctOrder.get(i))) {
                correctPositions++;
            }
        }
        
        // Calculate points
        int totalItems = correctOrder.size();
        int accuracy = totalItems > 0 ? (correctPositions * 100 / totalItems) : 0;
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        long elapsed = System.currentTimeMillis() - session.getRoundStartedAt();
        long timeRemaining = Math.max(0, timeLimitMs - elapsed);
        
        int basePoints = (BASE_POINTS * correctPositions / totalItems);
        int timeBonus = (int) (timeRemaining * BASE_POINTS / timeLimitMs / 2);
        int totalPoints = basePoints + timeBonus + (correctPositions * PER_ITEM_BONUS);

        Map<String, Object> resp = new HashMap<>();
        resp.put("orderedIds", orderedIds);
        resp.put("correctPositions", correctPositions);
        resp.put("totalItems", totalItems);
        resp.put("accuracy", accuracy);
        resp.put("points", totalPoints);
        resp.put("receivedAt", System.currentTimeMillis());
        responses.put(connectionId, resp);

        Player player = session.getPlayers().stream().filter(p -> p.getId().equals(connectionId)).findFirst().orElse(null);
        if (player != null && !player.isBot()) {
            player.addScore(totalPoints);
            LOG.infof("Sequencing answer from %s: %d/%d correct, %d points", 
                    player.getName(), correctPositions, totalItems, totalPoints);
        }
        
        return true;
    }

    /**
     * Check if round should end (all players answered or time up).
     */
    @SuppressWarnings("unchecked")
    public boolean shouldEndRound(GameSession session) {
        if (session.getGameType() != GameType.SEQUENCING || session.getPhase() != GamePhase.PLAYING) return false;
        
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
     * Move to next sequence or end game.
     */
    @SuppressWarnings("unchecked")
    public void nextSequence(GameSession session) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        
        int idx = payload.get("sequenceIndex") != null ? ((Number) payload.get("sequenceIndex")).intValue() : 0;
        List<Map<String, Object>> sequences = (List<Map<String, Object>>) payload.get("sequences");
        
        if (sequences != null && idx + 1 < sequences.size()) {
            showSequence(session, idx + 1);
        } else {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
        }
    }

    /**
     * Create default sequences if none provided.
     */
    private List<Map<String, Object>> createDefaultSequences() {
        List<Map<String, Object>> sequences = new ArrayList<>();
        
        // Time sequence
        Map<String, Object> timeSequence = new HashMap<>();
        timeSequence.put("type", "time");
        timeSequence.put("description", "Arrange historical events in chronological order");
        List<Map<String, Object>> timeItems = new ArrayList<>();
        timeItems.add(Map.of("id", "1", "content", "Discovery of Brazil", "year", 1500));
        timeItems.add(Map.of("id", "2", "content", "Independence of Brazil", "year", 1822));
        timeItems.add(Map.of("id", "3", "content", "Proclamation of Republic", "year", 1889));
        timeItems.add(Map.of("id", "4", "content", "World War II", "year", 1939));
        timeSequence.put("items", timeItems);
        sequences.add(timeSequence);
        
        // Size sequence
        Map<String, Object> sizeSequence = new HashMap<>();
        sizeSequence.put("type", "size");
        sizeSequence.put("description", "Arrange from smallest to largest");
        List<Map<String, Object>> sizeItems = new ArrayList<>();
        sizeItems.add(Map.of("id", "a", "content", "Atom", "size", 0.1));
        sizeItems.add(Map.of("id", "b", "content", "Molecule", "size", 1));
        sizeItems.add(Map.of("id", "c", "content", "Cell", "size", 10));
        sizeItems.add(Map.of("id", "d", "content", "Organism", "size", 1000));
        sizeSequence.put("items", sizeItems);
        sequences.add(sizeSequence);
        
        // Value sequence
        Map<String, Object> valueSequence = new HashMap<>();
        valueSequence.put("type", "value");
        valueSequence.put("description", "Arrange from lowest to highest value");
        List<Map<String, Object>> valueItems = new ArrayList<>();
        valueItems.add(Map.of("id", "x", "content", "1 cent", "value", 0.01));
        valueItems.add(Map.of("id", "y", "content", "10 cents", "value", 0.10));
        valueItems.add(Map.of("id", "z", "content", "1 real", "value", 1.00));
        valueItems.add(Map.of("id", "w", "content", "10 reais", "value", 10.00));
        valueSequence.put("items", valueItems);
        sequences.add(valueSequence);
        
        return sequences;
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
        return gameType == GameType.SEQUENCING;
    }

    @Override
    public GameType getSupportedGameType() {
        return GameType.SEQUENCING;
    }
}
