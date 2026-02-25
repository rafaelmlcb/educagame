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
 * Acerte ou Caia - Fill-in-the-blanks survival game.
 * Players must complete phrases with some letters visible, wrong answers eliminate players.
 */
@ApplicationScoped
public class SurvivalEngine implements GameEngineInterface {

    private static final Logger LOG = Logger.getLogger(SurvivalEngine.class);
    private static final int DEFAULT_TIME_MS = 30000;
    private static final int BASE_POINTS = 500;
    private static final int LIVES_START = 3;

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.SURVIVAL || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        
        List<String> phrases = dataLoaderService.getPhrases(session.getTheme());
        if (phrases == null) phrases = List.of();

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("phrases", phrases);
        payload.put("phraseIndex", 0);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        payload.put("eliminatedPlayers", new HashSet<String>());
        payload.put("roundScores", new ArrayList<Map<String, Object>>());
        
        // Initialize player lives
        Map<String, Integer> playerLives = new HashMap<>();
        for (Player player : session.getPlayers()) {
            if (!player.isBot()) {
                playerLives.put(player.getId(), LIVES_START);
            }
        }
        payload.put("playerLives", playerLives);
        
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Survival game started in room %s with %d phrases", session.getRoomId(), phrases.size());
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.PLAYING);
        showChallenge(session, 0);
    }

    @SuppressWarnings("unchecked")
    private void showChallenge(GameSession session, int index) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        List<String> phrases = (List<String>) payload.get("phrases");
        if (phrases == null || index >= phrases.size()) {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildSurvivalRanking(session));
            gameHistoryService.recordGame(session);
            return;
        }
        
        payload.put("phraseIndex", index);
        payload.put("responses", new ConcurrentHashMap<String, Map<String, Object>>());
        
        String phrase = phrases.get(index).toUpperCase(Locale.ROOT);
        String maskedPhrase = createMaskedPhrase(phrase);
        
        payload.put("originalPhrase", phrase);
        payload.put("maskedPhrase", maskedPhrase);
        payload.put("revealedLetters", new HashSet<Character>());
        
        int timeMs = DEFAULT_TIME_MS;
        payload.put("timeLimitMs", timeMs);
        session.setRoundStartedAt(System.currentTimeMillis());
        
        LOG.infof("Survival challenge %d: %s -> %s", index, phrase, maskedPhrase);
    }

    /**
     * Create a masked version of the phrase with some letters visible.
     */
    private String createMaskedPhrase(String phrase) {
        Random random = new Random();
        StringBuilder masked = new StringBuilder();
        Set<Integer> revealedPositions = new HashSet<>();
        
        // Reveal 30-50% of letters randomly
        int totalLetters = (int) phrase.chars().filter(Character::isLetter).count();
        int toReveal = Math.max(1, totalLetters * (30 + random.nextInt(21)) / 100);
        
        List<Integer> letterPositions = new ArrayList<>();
        for (int i = 0; i < phrase.length(); i++) {
            if (Character.isLetter(phrase.charAt(i))) {
                letterPositions.add(i);
            }
        }
        
        Collections.shuffle(letterPositions);
        for (int i = 0; i < Math.min(toReveal, letterPositions.size()); i++) {
            revealedPositions.add(letterPositions.get(i));
        }
        
        for (int i = 0; i < phrase.length(); i++) {
            char c = phrase.charAt(i);
            if (Character.isLetter(c)) {
                if (revealedPositions.contains(i)) {
                    masked.append(c);
                } else {
                    masked.append('_');
                }
            } else {
                masked.append(c); // Keep spaces and punctuation
            }
        }
        
        return masked.toString();
    }

    /**
     * Reveal a hint letter for all players.
     */
    @SuppressWarnings("unchecked")
    public void revealHint(GameSession session) {
        if (session.getGameType() != GameType.SURVIVAL || session.getPhase() != GamePhase.PLAYING) return;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        
        String originalPhrase = (String) payload.get("originalPhrase");
        String maskedPhrase = (String) payload.get("maskedPhrase");
        Set<Character> revealedLetters = (Set<Character>) payload.get("revealedLetters");
        if (revealedLetters == null) revealedLetters = new HashSet<>();
        
        // Find a letter to reveal
        List<Character> unrevealed = new ArrayList<>();
        for (int i = 0; i < originalPhrase.length(); i++) {
            char c = originalPhrase.charAt(i);
            if (Character.isLetter(c) && !revealedLetters.contains(c) && maskedPhrase.charAt(i) == '_') {
                unrevealed.add(c);
            }
        }
        
        if (!unrevealed.isEmpty()) {
            Collections.shuffle(unrevealed);
            char hintLetter = unrevealed.get(0);
            revealedLetters.add(hintLetter);
            
            // Update masked phrase
            StringBuilder newMasked = new StringBuilder();
            for (int i = 0; i < originalPhrase.length(); i++) {
                char c = originalPhrase.charAt(i);
                if (Character.isLetter(c)) {
                    if (revealedLetters.contains(c)) {
                        newMasked.append(c);
                    } else {
                        newMasked.append('_');
                    }
                } else {
                    newMasked.append(c);
                }
            }
            
            payload.put("maskedPhrase", newMasked.toString());
            payload.put("revealedLetters", revealedLetters);
            
            LOG.infof("Revealed hint letter: %c", hintLetter);
        }
    }

    /**
     * Submit answer for survival challenge.
     */
    @SuppressWarnings("unchecked")
    public boolean submitAnswer(GameSession session, String connectionId, String answer) {
        if (session.getGameType() != GameType.SURVIVAL || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        if (responses == null) return false;
        if (responses.containsKey(connectionId)) return true; // already answered

        Set<String> eliminatedPlayers = (Set<String>) payload.get("eliminatedPlayers");
        if (eliminatedPlayers.contains(connectionId)) return false; // player already eliminated

        String originalPhrase = (String) payload.get("originalPhrase");
        String normalizedAnswer = answer.toUpperCase(Locale.ROOT).trim();
        
        boolean correct = normalizedAnswer.equals(originalPhrase);
        
        Map<String, Object> resp = new HashMap<>();
        resp.put("answer", answer);
        resp.put("correct", correct);
        resp.put("receivedAt", System.currentTimeMillis());
        responses.put(connectionId, resp);

        Map<String, Integer> playerLives = (Map<String, Integer>) payload.get("playerLives");
        if (playerLives == null) playerLives = new HashMap<>();

        Player player = session.getPlayers().stream().filter(p -> p.getId().equals(connectionId)).findFirst().orElse(null);
        if (player != null && !player.isBot()) {
            if (correct) {
                int points = BASE_POINTS;
                // Bonus for answering quickly
                long elapsed = System.currentTimeMillis() - session.getRoundStartedAt();
                int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
                int timeBonus = Math.max(0, (int) ((timeLimitMs - elapsed) * BASE_POINTS / timeLimitMs / 2));
                points += timeBonus;
                
                player.addScore(points);
                LOG.infof("Survival correct answer from %s: %d points", player.getName(), points);
            } else {
                // Lose a life
                int lives = playerLives.getOrDefault(connectionId, LIVES_START) - 1;
                playerLives.put(connectionId, lives);
                
                if (lives <= 0) {
                    eliminatedPlayers.add(connectionId);
                    LOG.infof("Player %s eliminated", player.getName());
                } else {
                    LOG.infof("Player %s lost a life, %d remaining", player.getName(), lives);
                }
            }
        }
        
        payload.put("playerLives", playerLives);
        payload.put("eliminatedPlayers", eliminatedPlayers);
        
        return true;
    }

    /**
     * Check if round should end (all active players answered or time up).
     */
    @SuppressWarnings("unchecked")
    public boolean shouldEndRound(GameSession session) {
        if (session.getGameType() != GameType.SURVIVAL || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        int timeLimitMs = payload.get("timeLimitMs") != null ? ((Number) payload.get("timeLimitMs")).intValue() : DEFAULT_TIME_MS;
        long elapsed = System.currentTimeMillis() - session.getRoundStartedAt();
        
        // End round if time is up
        if (elapsed >= timeLimitMs) return true;
        
        Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) payload.get("responses");
        Set<String> eliminatedPlayers = (Set<String>) payload.get("eliminatedPlayers");
        
        // Check if all non-eliminated players have answered
        long activePlayerCount = session.getPlayers().stream()
                .filter(p -> !p.isBot() && !eliminatedPlayers.contains(p.getId()))
                .count();
        
        return responses.size() >= activePlayerCount;
    }

    /**
     * Move to next challenge or end game.
     */
    @SuppressWarnings("unchecked")
    public void nextChallenge(GameSession session) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        
        int idx = payload.get("phraseIndex") != null ? ((Number) payload.get("phraseIndex")).intValue() : 0;
        List<String> phrases = (List<String>) payload.get("phrases");
        Set<String> eliminatedPlayers = (Set<String>) payload.get("eliminatedPlayers");
        
        // Check if only one player remains
        long activePlayerCount = session.getPlayers().stream()
                .filter(p -> !p.isBot() && !eliminatedPlayers.contains(p.getId()))
                .count();
        
        if (activePlayerCount <= 1 || (phrases != null && idx + 1 >= phrases.size())) {
            session.setPhase(GamePhase.GAME_END);
            payload.put("finalRanking", buildSurvivalRanking(session));
            gameHistoryService.recordGame(session);
        } else {
            showChallenge(session, idx + 1);
        }
    }

    private List<Map<String, Object>> buildSurvivalRanking(GameSession session) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        Set<String> eliminatedPlayers = payload != null ? (Set<String>) payload.get("eliminatedPlayers") : new HashSet<>();
        
        return session.getPlayers().stream()
                .filter(p -> !p.isBot())
                .sorted((a, b) -> {
                    boolean aEliminated = eliminatedPlayers.contains(a.getId());
                    boolean bEliminated = eliminatedPlayers.contains(b.getId());
                    if (aEliminated && !bEliminated) return 1;
                    if (!aEliminated && bEliminated) return -1;
                    return Integer.compare(b.getScore(), a.getScore());
                })
                .map(p -> {
                    Map<String, Object> playerData = new HashMap<>();
                    playerData.put("id", p.getId());
                    playerData.put("name", p.getName());
                    playerData.put("score", p.getScore());
                    playerData.put("eliminated", eliminatedPlayers.contains(p.getId()));
                    return playerData;
                })
                .toList();
    }

    @Override
    public boolean supports(GameType gameType) {
        return gameType == GameType.SURVIVAL;
    }

    @Override
    public GameType getSupportedGameType() {
        return GameType.SURVIVAL;
    }
}
