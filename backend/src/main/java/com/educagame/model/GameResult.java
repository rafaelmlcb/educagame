package com.educagame.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameResult {

    private String gameId;
    private GameType gameType;
    private String theme;
    private long finishedAt;
    private String winnerId;
    private String winnerName;
    private int winnerScore;
    private List<Map<String, Object>> ranking;

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public GameType getGameType() { return gameType; }
    public void setGameType(GameType gameType) { this.gameType = gameType; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public long getFinishedAt() { return finishedAt; }
    public void setFinishedAt(long finishedAt) { this.finishedAt = finishedAt; }
    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
    public String getWinnerName() { return winnerName; }
    public void setWinnerName(String winnerName) { this.winnerName = winnerName; }
    public int getWinnerScore() { return winnerScore; }
    public void setWinnerScore(int winnerScore) { this.winnerScore = winnerScore; }
    public List<Map<String, Object>> getRanking() { return ranking; }
    public void setRanking(List<Map<String, Object>> ranking) { this.ranking = ranking; }
}
