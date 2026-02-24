package com.educagame.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Represents a player in a game session.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Player {

    private String id;
    private String name;
    private int score;
    private boolean connected;
    private boolean host;
    private boolean bot;

    public Player() {
    }

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.score = 0;
        this.connected = true;
        this.host = false;
        this.bot = false;
    }

    public Player(String id, String name, boolean bot) {
        this.id = id;
        this.name = name;
        this.score = 0;
        this.connected = true;
        this.host = false;
        this.bot = bot;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void addScore(int delta) {
        this.score += delta;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isHost() {
        return host;
    }

    public void setHost(boolean host) {
        this.host = host;
    }

    public boolean isBot() {
        return bot;
    }

    public void setBot(boolean bot) {
        this.bot = bot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
