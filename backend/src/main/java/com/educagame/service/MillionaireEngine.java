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
 * Show do Milhão: 10 levels, lifelines 50:50, Universitários, Skip, prize ladder.
 */
@ApplicationScoped
public class MillionaireEngine implements GameEngineInterface {

    private static final Logger LOG = Logger.getLogger(MillionaireEngine.class);
    private static final int MAX_LEVEL = 10;
    private static final int[] PRIZES = { 0, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 250000, 500000, 1000000 };
    private final Random random = new Random();

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.QUIZ_INCREMENTAL || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("level", 1);
        payload.put("guaranteedPrize", 0);
        payload.put("lifeline50Used", false);
        payload.put("lifelineUniUsed", false);
        payload.put("lifelineSkipUsed", false);
        payload.put("audiencePercents", List.of());
        payload.put("removedOptions", List.of());
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Millionaire started in room %s", session.getRoomId());
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.MILLIONAIRE_QUESTION);
        loadQuestionForLevel(session, 1);
    }

    @SuppressWarnings("unchecked")
    private void loadQuestionForLevel(GameSession session, int level) {
        List<Map<String, Object>> all = dataLoaderService.getMillionaireQuestions(session.getTheme());
        if (all == null) all = List.of();
        List<Map<String, Object>> forLevel = all.stream()
                .filter(m -> (m.get("level") != null ? ((Number) m.get("level")).intValue() : 1) == level)
                .toList();
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        payload.put("level", level);
        payload.put("removedOptions", List.of());
        payload.put("audiencePercents", List.of());
        if (forLevel.isEmpty()) {
            payload.put("question", "Pergunta nível " + level);
            payload.put("options", List.of("A", "B", "C", "D"));
            payload.put("correctIndex", 0);
            payload.put("value", PRIZES[Math.min(level, PRIZES.length - 1)]);
        } else {
            Map<String, Object> q = forLevel.get(random.nextInt(forLevel.size()));
            payload.put("question", q.get("question"));
            payload.put("options", q.get("options"));
            payload.put("correctIndex", q.get("correctIndex"));
            payload.put("value", q.get("value") != null ? ((Number) q.get("value")).intValue() : PRIZES[Math.min(level, PRIZES.length - 1)]);
        }
        session.setRoundStartedAt(System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    public boolean submitAnswer(GameSession session, String connectionId, int answerIndex) {
        if (session.getGameType() != GameType.QUIZ_INCREMENTAL || session.getPhase() != GamePhase.MILLIONAIRE_QUESTION) return false;
        List<Player> players = session.getPlayers();
        if (players.isEmpty()) return false;
        if (!players.get(0).getId().equals(connectionId)) return false; // single player game, first is the one playing

        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        Integer correctIndex = payload.get("correctIndex") != null ? ((Number) payload.get("correctIndex")).intValue() : null;
        int level = payload.get("level") != null ? ((Number) payload.get("level")).intValue() : 1;
        int value = payload.get("value") != null ? ((Number) payload.get("value")).intValue() : PRIZES[level];
        int guaranteed = payload.get("guaranteedPrize") != null ? ((Number) payload.get("guaranteedPrize")).intValue() : 0;

        if (correctIndex != null && answerIndex == correctIndex) {
            Player p = players.get(0);
            p.setScore(p.getScore() + value);
            if (level >= MAX_LEVEL) {
                session.setPhase(GamePhase.GAME_END);
                payload.put("won", true);
                payload.put("finalPrize", value);
                gameHistoryService.recordGame(session);
                LOG.infof("Millionaire won at level %d prize=%d", level, value);
            } else {
                payload.put("guaranteedPrize", value);
                loadQuestionForLevel(session, level + 1);
            }
        } else {
            session.setPhase(GamePhase.GAME_END);
            payload.put("won", false);
            payload.put("finalPrize", guaranteed);
            players.get(0).setScore(players.get(0).getScore() + guaranteed);
            gameHistoryService.recordGame(session);
            LOG.infof("Millionaire wrong answer level=%d guaranteed=%d", level, guaranteed);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public void lifeline50_50(GameSession session, String connectionId) {
        if (session.getGameType() != GameType.QUIZ_INCREMENTAL) return;
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null || Boolean.TRUE.equals(payload.get("lifeline50Used"))) return;
        List<String> options = (List<String>) payload.get("options");
        Integer correctIndex = payload.get("correctIndex") != null ? ((Number) payload.get("correctIndex")).intValue() : 0;
        if (options == null || options.size() < 3) return;

        List<Integer> toRemove = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            if (i != correctIndex) toRemove.add(i);
        }
        Collections.shuffle(toRemove);
        toRemove = toRemove.subList(0, Math.min(2, toRemove.size()));
        payload.put("lifeline50Used", true);
        payload.put("removedOptions", toRemove);
    }

    @SuppressWarnings("unchecked")
    public void lifelineUni(GameSession session, String connectionId) {
        if (session.getGameType() != GameType.QUIZ_INCREMENTAL) return;
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null || Boolean.TRUE.equals(payload.get("lifelineUniUsed"))) return;
        List<String> options = (List<String>) payload.get("options");
        Integer correctIndex = payload.get("correctIndex") != null ? ((Number) payload.get("correctIndex")).intValue() : 0;
        if (options == null || options.size() == 0) return;

        int[] percents = new int[options.size()];
        int correctShare = 40 + random.nextInt(35);
        int rest = 100 - correctShare;
        for (int i = 0; i < options.size(); i++) {
            if (i == correctIndex) percents[i] = correctShare;
            else percents[i] = rest / Math.max(1, options.size() - 1);
        }
        int remainder = 100 - (correctShare + (options.size() - 1) * (rest / Math.max(1, options.size() - 1)));
        if (correctIndex >= 0 && correctIndex < percents.length) percents[correctIndex] += remainder;
        payload.put("lifelineUniUsed", true);
        payload.put("audiencePercents", Arrays.stream(percents).boxed().toList());
    }

    @SuppressWarnings("unchecked")
    public void lifelineSkip(GameSession session, String connectionId) {
        if (session.getGameType() != GameType.QUIZ_INCREMENTAL) return;
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null || Boolean.TRUE.equals(payload.get("lifelineSkipUsed"))) return;
        int level = payload.get("level") != null ? ((Number) payload.get("level")).intValue() : 1;
        payload.put("lifelineSkipUsed", true);
        loadQuestionForLevel(session, level);
    }

    @Override
    public boolean supports(GameType gameType) {
        return gameType == GameType.QUIZ_INCREMENTAL;
    }

    @Override
    public GameType getSupportedGameType() {
        return GameType.QUIZ_INCREMENTAL;
    }
}
