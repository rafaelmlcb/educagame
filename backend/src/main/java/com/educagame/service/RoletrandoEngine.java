package com.educagame.service;

import com.educagame.model.GamePhase;
import com.educagame.model.GameSession;
import com.educagame.model.GameType;
import com.educagame.model.Player;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Roletrando (Roda a Roda) game logic: wheel, GUESS, SOLVE, Lucky Spin, turn validation.
 */
@ApplicationScoped
public class RoletrandoEngine {

    private static final Logger LOG = Logger.getLogger(RoletrandoEngine.class);
    private static final int MAX_BOTS = 3;
    private static final String SEGMENT_TYPE = "type";
    private static final String NORMAL = "NORMAL";
    private static final String LOSE_TURN = "LOSE_TURN";
    private static final String LOSE_ALL = "LOSE_ALL";
    private static final String BONUS = "BONUS";

    private final Random random = new Random();

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.ROLETRANDO || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        session.setRoundStartedAt(System.currentTimeMillis());

        List<String> phrases = dataLoaderService.getPhrases(session.getTheme());
        String phrase = phrases.isEmpty() ? "BRASIL" : phrases.get(random.nextInt(phrases.size())).toUpperCase(Locale.ROOT);
        phrase = normalize(phrase);

        int humanCount = (int) session.getPlayers().stream().filter(p -> !p.isBot()).count();
        if (humanCount < 3) {
            int toAdd = Math.min(MAX_BOTS - humanCount, 3 - session.getPlayers().size());
            for (int i = 0; i < toAdd; i++) {
                Player bot = new Player("bot-" + UUID.randomUUID().toString().substring(0, 6), "Bot " + (i + 1), true);
                session.addPlayer(bot);
            }
        }

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("phrase", phrase);
        payload.put("revealed", new HashSet<String>());
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Roletrando started in room %s phrase=%s", session.getRoomId(), phrase);
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.PLAYING);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = session.getGamePayload() != null ? (Map<String, Object>) session.getGamePayload() : new HashMap<>();
        payload.remove("segmentIndex");
        payload.remove("segment");
        payload.remove("segmentType");
        payload.remove("segmentValue");
        session.setGamePayload(payload);
    }

    /** Returns map with segmentIndex and segment for broadcast; null if invalid. */
    public Map<String, Object> spinWheel(GameSession session, String connectionId) {
        if (session.getGameType() != GameType.ROLETRANDO) return null;
        List<Player> players = session.getPlayers();
        if (players.isEmpty()) return null;
        int turnIdx = session.getCurrentTurnIndex() % players.size();
        if (!players.get(turnIdx).getId().equals(connectionId)) return null;
        if (session.getPhase() != GamePhase.PLAYING) return null;

        List<Map<String, Object>> segments = dataLoaderService.getWheelSegments(session.getTheme());
        if (segments == null || segments.isEmpty()) return null;
        int index = random.nextInt(segments.size());
        Map<String, Object> segment = segments.get(index);
        String type = segment.get(SEGMENT_TYPE) != null ? String.valueOf(segment.get(SEGMENT_TYPE)) : NORMAL;
        int value = segment.get("value") != null ? ((Number) segment.get("value")).intValue() : 0;

        session.setPhase(GamePhase.SPINNING);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = session.getGamePayload() != null ? (Map<String, Object>) session.getGamePayload() : new HashMap<>();
        payload.put("segmentIndex", index);
        payload.put("segment", segment);
        payload.put("segmentType", type);
        payload.put("segmentValue", value);
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());

        Map<String, Object> result = Map.of("segmentIndex", index, "segment", segment);

        if (LOSE_TURN.equals(type) || LOSE_ALL.equals(type)) {
            applyLuckySpinEffect(session, type);
            return result;
        }
        session.setPhase(GamePhase.GUESSING);
        LOG.infof("Wheel spun room %s segment %d type=%s", session.getRoomId(), index, type);
        return result;
    }

    private void applyLuckySpinEffect(GameSession session, String type) {
        List<Player> players = session.getPlayers();
        int idx = session.getCurrentTurnIndex() % players.size();
        if (LOSE_ALL.equals(type)) {
            players.get(idx).setScore(0);
            LOG.infof("Player %s lost all points", players.get(idx).getName());
        }
        session.setCurrentTurnIndex((idx + 1) % players.size());
        session.setPhase(GamePhase.PLAYING);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = session.getGamePayload() != null ? (Map<String, Object>) session.getGamePayload() : new HashMap<>();
        payload.remove("segmentIndex");
        payload.remove("segment");
        payload.remove("segmentType");
        payload.remove("segmentValue");
        session.setGamePayload(payload);
    }

    /** Returns true if guess was valid and applied. */
    public boolean processGuess(GameSession session, String connectionId, char letter) {
        if (session.getGameType() != GameType.ROLETRANDO || session.getPhase() != GamePhase.GUESSING) return false;
        List<Player> players = session.getPlayers();
        int turnIdx = session.getCurrentTurnIndex() % players.size();
        if (!players.get(turnIdx).getId().equals(connectionId)) return false;

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        String phrase = (String) payload.get("phrase");
        @SuppressWarnings("unchecked")
        Set<String> revealedSet = (Set<String>) payload.get("revealed");
        if (revealedSet == null) revealedSet = new HashSet<>();
        int segmentValue = payload.get("segmentValue") != null ? ((Number) payload.get("segmentValue")).intValue() : 0;

        char upper = Character.toUpperCase(letter);
        if (!Character.isLetter(upper)) return false;
        String letterStr = String.valueOf(upper);
        if (revealedSet.contains(letterStr)) return false; // already guessed

        int count = 0;
        for (int i = 0; i < phrase.length(); i++) {
            if (phrase.charAt(i) == upper) count++;
        }
        if (count > 0) {
            revealedSet.add(letterStr);
            payload.put("revealed", revealedSet);
            int points = segmentValue * count;
            players.get(turnIdx).addScore(points);
            if (allRevealed(phrase, revealedSet)) {
                session.setPhase(GamePhase.GAME_END);
                payload.put("solvedBy", players.get(turnIdx).getName());
                gameHistoryService.recordGame(session);
            }
            LOG.infof("Guess %c count=%d points=%d", upper, count, points);
        } else {
            session.setCurrentTurnIndex((turnIdx + 1) % players.size());
            session.setPhase(GamePhase.PLAYING);
            payload.remove("segmentIndex");
            payload.remove("segment");
            payload.remove("segmentType");
            payload.remove("segmentValue");
        }
        return true;
    }

    /** Returns true if solve was valid and applied. */
    public boolean processSolve(GameSession session, String connectionId, String attempt) {
        if (session.getGameType() != GameType.ROLETRANDO || session.getPhase() != GamePhase.GUESSING) return false;
        List<Player> players = session.getPlayers();
        int turnIdx = session.getCurrentTurnIndex() % players.size();
        if (!players.get(turnIdx).getId().equals(connectionId)) return false;

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        String phrase = (String) payload.get("phrase");
        @SuppressWarnings("unchecked")
        Set<String> revealedSet = (Set<String>) payload.get("revealed");
        if (revealedSet == null) revealedSet = new HashSet<>();

        String normalized = normalize(attempt.trim().toUpperCase(Locale.ROOT));
        if (normalized.equals(phrase)) {
            int hidden = 0;
            for (int i = 0; i < phrase.length(); i++) {
                if (!revealedSet.contains(String.valueOf(phrase.charAt(i)))) hidden++;
            }
            int bonus = 1000 * Math.max(0, hidden);
            players.get(turnIdx).addScore(bonus);
            session.setPhase(GamePhase.GAME_END);
            payload.put("solvedBy", players.get(turnIdx).getName());
            gameHistoryService.recordGame(session);
            LOG.infof("Solve correct bonus=%d", bonus);
        } else {
            players.get(turnIdx).setScore(0);
            session.setCurrentTurnIndex((turnIdx + 1) % players.size());
            session.setPhase(GamePhase.PLAYING);
            payload.remove("segmentIndex");
            payload.remove("segment");
            payload.remove("segmentType");
            payload.remove("segmentValue");
            LOG.infof("Solve wrong, player score zeroed");
        }
        return true;
    }

    private boolean allRevealed(String phrase, Set<String> revealed) {
        for (int i = 0; i < phrase.length(); i++) {
            if (phrase.charAt(i) != ' ' && !revealed.contains(String.valueOf(phrase.charAt(i)))) return false;
        }
        return true;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) sb.append(c);
        }
        return sb.toString().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    public boolean isCurrentTurn(GameSession session, String connectionId) {
        List<Player> players = session.getPlayers();
        if (players.isEmpty()) return false;
        int idx = session.getCurrentTurnIndex() % players.size();
        return players.get(idx).getId().equals(connectionId);
    }
}
