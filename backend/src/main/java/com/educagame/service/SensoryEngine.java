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
 * Sensory game - Identify sounds, voices, or distorted images.
 * Tests auditory and visual recognition skills.
 */
@ApplicationScoped
public class SensoryEngine implements GameEngineInterface {

    private static final Logger LOG = Logger.getLogger(SensoryEngine.class);
    private static final int DEFAULT_TIME_MS = 20000;
    private static final int BASE_POINTS = 700;
    private static final int DIFFICULTY_BONUS = 300;

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.SENSORY || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        
        List<Map<String, Object>> sensoryItems = dataLoaderService.getSensoryItems(session.getTheme());
        if (sensoryItems == null) sensoryItems = createDefaultSensoryItems();

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("sensoryItems", sensoryItems);
        payload.put("itemIndex", 0);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        payload.put("roundScores", new ArrayList<Map<String, Object>>());
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Sensory game started in room %s with %d items", session.getRoomId(), sensoryItems.size());
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.PLAYING);
        showSensoryItem(session, 0);
    }

    @SuppressWarnings("unchecked")
    private void showSensoryItem(GameSession session, int index) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        List<Map<String, Object>> sensoryItems = (List<Map<String, Object>>) payload.get("sensoryItems");
        if (sensoryItems == null || index >= sensoryItems.size()) {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
            return;
        }
        
        payload.put("itemIndex", index);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        
        Map<String, Object> item = sensoryItems.get(index);
        String sensoryType = (String) item.get("type"); // "sound", "image", "voice"
        String answer = (String) item.get("answer");
        int difficulty = item.get("difficulty") != null ? ((Number) item.get("difficulty")).intValue() : 1;
        
        payload.put("sensoryType", sensoryType);
        payload.put("answer", answer);
        payload.put("difficulty", difficulty);
        payload.put("mediaUrl", item.get("mediaUrl"));
        payload.put("description", item.get("description"));
        
        // Apply distortion based on difficulty
        Map<String, Object> distortion = applyDistortion(item, difficulty);
        payload.put("distortedMedia", distortion.get("media"));
        payload.put("distortionLevel", distortion.get("level"));
        
        int timeMs = item.get("timeLimitMs") != null ? ((Number) item.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        payload.put("timeLimitMs", timeMs);
        session.setRoundStartedAt(System.currentTimeMillis());
        
        LOG.infof("Sensory item %d: %s (%s, difficulty %d)", index, answer, sensoryType, difficulty);
    }

    /**
     * Apply distortion to media based on difficulty level.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyDistortion(Map<String, Object> item, int difficulty) {
        Map<String, Object> result = new HashMap<>();
        String type = (String) item.get("type");
        
        // In a real implementation, this would apply actual audio/image processing
        // For now, we simulate with metadata
        if ("sound".equals(type) || "voice".equals(type)) {
            // Audio distortion: noise, speed change, pitch shift
            String[] audioDistortions = {"light_noise", "medium_noise", "heavy_noise", "speed_change", "pitch_shift"};
            String distortion = audioDistortions[Math.min(difficulty - 1, audioDistortions.length - 1)];
            result.put("media", Map.of("type", "audio", "distortion", distortion, "originalUrl", item.get("mediaUrl")));
            result.put("level", difficulty);
        } else if ("image".equals(type)) {
            // Image distortion: blur, pixelation, noise, zoom
            String[] imageDistortions = {"light_blur", "medium_blur", "heavy_blur", "pixelated", "zoomed"};
            String distortion = imageDistortions[Math.min(difficulty - 1, imageDistortions.length - 1)];
            result.put("media", Map.of("type", "image", "distortion", distortion, "originalUrl", item.get("mediaUrl")));
            result.put("level", difficulty);
        }
        
        return result;
    }

    /**
     * Submit sensory identification answer.
     */
    @SuppressWarnings("unchecked")
    public boolean submitAnswer(GameSession session, String connectionId, String guess) {
        if (session.getGameType() != GameType.SENSORY || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        if (responses.containsKey(connectionId)) return true; // already answered

        String answer = (String) payload.get("answer");
        int difficulty = payload.get("difficulty") != null ? ((Number) payload.get("difficulty")).intValue() : 1;
        String sensoryType = (String) payload.get("sensoryType");
        
        boolean correct = guess.trim().equalsIgnoreCase(answer.trim());
        
        // Calculate points based on difficulty and time
        long elapsed = System.currentTimeMillis() - session.getRoundStartedAt();
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        long timeRemaining = Math.max(0, timeLimitMs - elapsed);
        int timeBonus = (int) (timeRemaining * BASE_POINTS / timeLimitMs / 2);
        
        int difficultyBonus = difficulty * DIFFICULTY_BONUS;
        int basePoints = correct ? BASE_POINTS : 0;
        int totalPoints = basePoints + timeBonus + difficultyBonus;

        Map<String, Object> resp = new HashMap<>();
        resp.put("guess", guess);
        resp.put("correct", correct);
        resp.put("sensoryType", sensoryType);
        resp.put("difficulty", difficulty);
        resp.put("points", totalPoints);
        resp.put("receivedAt", System.currentTimeMillis());
        responses.put(connectionId, resp);

        Player player = session.getPlayers().stream().filter(p -> p.getId().equals(connectionId)).findFirst().orElse(null);
        if (player != null && !player.isBot()) {
            player.addScore(totalPoints);
            LOG.infof("Sensory guess from %s: '%s' -> %s (%s, difficulty %d, %d points)", 
                    player.getName(), guess, correct ? "CORRECT" : "WRONG", sensoryType, difficulty, totalPoints);
        }
        
        return true;
    }

    /**
     * Check if round should end (all players answered or time up).
     */
    @SuppressWarnings("unchecked")
    public boolean shouldEndRound(GameSession session) {
        if (session.getGameType() != GameType.SENSORY || session.getPhase() != GamePhase.PLAYING) return false;
        
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
     * Move to next sensory item or end game.
     */
    @SuppressWarnings("unchecked")
    public void nextSensoryItem(GameSession session) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        
        int idx = payload.get("itemIndex") != null ? ((Number) payload.get("itemIndex")).intValue() : 0;
        List<Map<String, Object>> sensoryItems = (List<Map<String, Object>>) payload.get("sensoryItems");
        
        if (sensoryItems != null && idx + 1 < sensoryItems.size()) {
            showSensoryItem(session, idx + 1);
        } else {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildRanking(session));
            gameHistoryService.recordGame(session);
        }
    }

    /**
     * Create default sensory items if none provided.
     */
    private List<Map<String, Object>> createDefaultSensoryItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        
        // Sound items
        Map<String, Object> sound1 = new HashMap<>();
        sound1.put("type", "sound");
        sound1.put("answer", "Dog Barking");
        sound1.put("difficulty", 1);
        sound1.put("mediaUrl", "/media/sounds/dog_bark.mp3");
        sound1.put("description", "Identify this animal sound");
        sound1.put("timeLimitMs", 15000);
        items.add(sound1);
        
        Map<String, Object> sound2 = new HashMap<>();
        sound2.put("type", "sound");
        sound2.put("answer", "Thunder");
        sound2.put("difficulty", 2);
        sound2.put("mediaUrl", "/media/sounds/thunder.mp3");
        sound2.put("description", "Identify this weather sound");
        sound2.put("timeLimitMs", 12000);
        items.add(sound2);
        
        // Voice items
        Map<String, Object> voice1 = new HashMap<>();
        voice1.put("type", "voice");
        voice1.put("answer", "Albert Einstein");
        voice1.put("difficulty", 3);
        voice1.put("mediaUrl", "/media/voices/einstein_quote.mp3");
        voice1.put("description", "Identify this famous person by voice");
        voice1.put("timeLimitMs", 20000);
        items.add(voice1);
        
        // Image items
        Map<String, Object> image1 = new HashMap<>();
        image1.put("type", "image");
        image1.put("answer", "Eiffel Tower");
        image1.put("difficulty", 1);
        image1.put("mediaUrl", "/media/images/eiffel_tower.jpg");
        image1.put("description", "Identify this landmark");
        image1.put("timeLimitMs", 10000);
        items.add(image1);
        
        Map<String, Object> image2 = new HashMap<>();
        image2.put("type", "image");
        image2.put("answer", "Mona Lisa");
        image2.put("difficulty", 2);
        image2.put("mediaUrl", "/media/images/mona_lisa.jpg");
        image2.put("description", "Identify this famous painting");
        image2.put("timeLimitMs", 15000);
        items.add(image2);
        
        return items;
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
        return gameType == GameType.SENSORY;
    }

    @Override
    public GameType getSupportedGameType() {
        return GameType.SENSORY;
    }
}
