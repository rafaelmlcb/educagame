package com.educagame.service;

import com.educagame.model.GameResult;
import com.educagame.model.GameSession;
import com.educagame.model.GameType;
import com.educagame.model.Player;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory store of finished game results for history and leaderboards.
 */
@ApplicationScoped
public class GameHistoryService {

    private static final Logger LOG = Logger.getLogger(GameHistoryService.class);
    private static final int MAX_RESULTS = 500;
    private static final int MAX_LEADERBOARD = 100;

    private final List<GameResult> results = new CopyOnWriteArrayList<>();
    private final Map<GameType, Long> gamesCreatedByType = new ConcurrentHashMap<>();
    private final long startedAt = System.currentTimeMillis();

    public void recordGame(GameSession session) {
        if (session == null) return;
        GameResult r = new GameResult();
        r.setGameId(session.getRoomId());
        r.setGameType(session.getGameType());
        r.setTheme(session.getTheme());
        r.setFinishedAt(System.currentTimeMillis());

        List<Player> players = session.getPlayers().stream().filter(p -> !p.isBot()).toList();
        if (!players.isEmpty()) {
            Player top = players.stream().max(Comparator.comparingInt(Player::getScore)).orElse(null);
            if (top != null) {
                r.setWinnerId(top.getId());
                r.setWinnerName(top.getName());
                r.setWinnerScore(top.getScore());
            }
            r.setRanking(players.stream()
                    .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                    .map(p -> Map.<String, Object>of("id", p.getId(), "name", p.getName(), "score", p.getScore()))
                    .collect(Collectors.toList()));
        }
        results.add(0, r);
        while (results.size() > MAX_RESULTS) results.remove(results.size() - 1);
        LOG.debugf("Recorded game %s type=%s", session.getRoomId(), session.getGameType());
    }

    public void recordGameCreated(GameType type) {
        gamesCreatedByType.merge(type, 1L, Long::sum);
    }

    public List<GameResult> getRecentResults(int limit) {
        return results.stream().limit(Math.max(1, limit)).toList();
    }

    public List<Map<String, Object>> getLeaderboard(GameType mode, int limit) {
        int n = Math.min(MAX_LEADERBOARD, Math.max(1, limit));
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (GameResult r : results) {
            if (r.getGameType() != mode || r.getWinnerName() == null) continue;
            String key = r.getWinnerName().trim().toLowerCase();
            Map<String, Object> entry = byName.get(key);
            if (entry == null) {
                entry = new HashMap<>();
                entry.put("name", r.getWinnerName());
                entry.put("score", r.getWinnerScore());
                entry.put("games", 1);
                byName.put(key, entry);
            } else {
                entry.put("score", (Integer) entry.get("score") + r.getWinnerScore());
                entry.put("games", (Integer) entry.get("games") + 1);
            }
        }
        return byName.values().stream()
                .sorted((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")))
                .limit(n)
                .toList();
    }

    public long getUptimeMs() {
        return System.currentTimeMillis() - startedAt;
    }

    public long getTotalGamesCreated() {
        return gamesCreatedByType.values().stream().mapToLong(Long::longValue).sum();
    }

    public Map<GameType, Long> getGamesCreatedByType() {
        return new HashMap<>(gamesCreatedByType);
    }
}
