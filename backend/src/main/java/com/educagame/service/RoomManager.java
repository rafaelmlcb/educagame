package com.educagame.service;

import com.educagame.model.GameSession;
import com.educagame.model.GameType;
import com.educagame.model.Player;
import com.educagame.model.Room;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe manager of game rooms. Single source of truth for session lifecycle.
 */
@ApplicationScoped
public class RoomManager {

    private static final Logger LOG = Logger.getLogger(RoomManager.class);
    private static final int MAX_PLAYERS = 10;
    private static final int ROOM_ID_LENGTH = 8;

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roomConnections = new ConcurrentHashMap<>(); // roomId -> connectionIds

    public GameSession createRoom(String theme, GameType gameType, boolean isPrivate) {
        String roomId = isPrivate ? generateRoomId() : UUID.randomUUID().toString().substring(0, ROOM_ID_LENGTH);
        GameSession session = new GameSession(roomId, theme != null ? theme : "default", gameType, null);
        sessions.put(roomId, session);
        roomConnections.put(roomId, ConcurrentHashMap.newKeySet());
        LOG.infof("Room created: %s theme=%s gameType=%s", roomId, session.getTheme(), gameType);
        return session;
    }

    public Optional<GameSession> getSession(String roomId) {
        return Optional.ofNullable(sessions.get(roomId));
    }

    public boolean joinRoom(String roomId, String connectionId, String playerName) {
        GameSession session = sessions.get(roomId);
        if (session == null) {
            LOG.warnf("Join failed: room %s not found", roomId);
            return false;
        }
        if (session.getPlayers().size() >= MAX_PLAYERS) {
            LOG.warnf("Join failed: room %s full", roomId);
            return false;
        }
        Player player = new Player(connectionId, playerName);
        boolean isFirst = session.getPlayers().isEmpty();
        if (isFirst) session.setHostConnectionId(connectionId);
        player.setHost(isFirst);
        session.addPlayer(player);
        roomConnections.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(connectionId);
        LOG.infof("Player %s joined room %s", playerName, roomId);
        return true;
    }

    public void leaveRoom(String connectionId) {
        for (Map.Entry<String, Set<String>> e : roomConnections.entrySet()) {
            if (e.getValue().remove(connectionId)) {
                GameSession session = sessions.get(e.getKey());
                if (session != null) {
                    session.setPlayerConnected(connectionId, false);
                    session.removePlayer(connectionId);
                    if (session.getPlayers().isEmpty()) {
                        sessions.remove(e.getKey());
                        roomConnections.remove(e.getKey());
                        LOG.infof("Room %s removed (empty)", e.getKey());
                    }
                }
                return;
            }
        }
    }

    public Set<String> getConnectionIdsInRoom(String roomId) {
        Set<String> set = roomConnections.get(roomId);
        return set == null ? Set.of() : new HashSet<>(set);
    }

    public List<Room> listPublicRooms() {
        return sessions.values().stream()
                .filter(s -> !isPrivateRoom(s.getRoomId()))
                .map(s -> new Room(
                        s.getRoomId(),
                        s.getTheme(),
                        s.getGameType(),
                        s.getPlayers().size(),
                        MAX_PLAYERS,
                        false
                ))
                .collect(Collectors.toList());
    }

    private boolean isPrivateRoom(String roomId) {
        return roomId != null && roomId.length() == ROOM_ID_LENGTH && roomId.chars().allMatch(Character::isLetterOrDigit);
    }

    private String generateRoomId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(ROOM_ID_LENGTH);
        for (int i = 0; i < ROOM_ID_LENGTH; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        String id = sb.toString();
        return sessions.containsKey(id) ? generateRoomId() : id;
    }
}
