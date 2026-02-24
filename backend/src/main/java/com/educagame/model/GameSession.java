package com.educagame.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Source of truth for a single game room. All state, scores, turn and timers live here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GameSession {

    private String roomId;
    private String theme;
    private GameType gameType;
    private String hostConnectionId;
    private final List<Player> players = new CopyOnWriteArrayList<>();
    private GamePhase phase = GamePhase.LOBBY;
    private int currentTurnIndex;
    private Object gamePayload; // wheel segment index, current question, etc.
    private Long roundStartedAt; // for timers

    public GameSession() {
    }

    public GameSession(String roomId, String theme, GameType gameType, String hostConnectionId) {
        this.roomId = roomId;
        this.theme = theme;
        this.gameType = gameType;
        this.hostConnectionId = hostConnectionId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public String getHostConnectionId() {
        return hostConnectionId;
    }

    public void setHostConnectionId(String hostConnectionId) {
        this.hostConnectionId = hostConnectionId;
    }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    public void addPlayer(Player player) {
        if (players.stream().noneMatch(p -> p.getId().equals(player.getId()))) {
            players.add(player);
        }
    }

    public void removePlayer(String connectionId) {
        players.removeIf(p -> p.getId().equals(connectionId));
        players.forEach(p -> p.setConnected(!p.getId().equals(connectionId)));
    }

    public void setPlayerConnected(String connectionId, boolean connected) {
        players.stream()
                .filter(p -> p.getId().equals(connectionId))
                .findFirst()
                .ifPresent(p -> p.setConnected(connected));
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public int getCurrentTurnIndex() {
        return currentTurnIndex;
    }

    public void setCurrentTurnIndex(int currentTurnIndex) {
        this.currentTurnIndex = currentTurnIndex;
    }

    public Object getGamePayload() {
        return gamePayload;
    }

    public void setGamePayload(Object gamePayload) {
        this.gamePayload = gamePayload;
    }

    public Long getRoundStartedAt() {
        return roundStartedAt;
    }

    public void setRoundStartedAt(Long roundStartedAt) {
        this.roundStartedAt = roundStartedAt;
    }
}
