package com.educagame.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for room listing (lobby). Contains minimal info without full game state.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Room {

    private String roomId;
    private String theme;
    private GameType gameType;
    private int playerCount;
    private int maxPlayers;
    private boolean isPrivate;

    public Room() {
    }

    public Room(String roomId, String theme, GameType gameType, int playerCount, int maxPlayers, boolean isPrivate) {
        this.roomId = roomId;
        this.theme = theme;
        this.gameType = gameType;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.isPrivate = isPrivate;
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

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }
}
