package com.educagame.resource;

import com.educagame.model.WsOutbound;
import com.educagame.service.RoomManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.UserData;
import io.quarkus.websockets.next.WebSocketConnection;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;

/**
 * Sends WebSocket messages to all connections in a given room.
 */
@ApplicationScoped
public class GameBroadcaster {

    private static final Logger LOG = Logger.getLogger(GameBroadcaster.class);
    private static final UserData.TypedKey<String> KEY_ROOM_ID = UserData.TypedKey.forString("roomId");

    @Inject
    OpenConnections openConnections;
    @Inject
    RoomManager roomManager;
    @Inject
    ObjectMapper objectMapper;

    public void broadcastToRoom(String roomId, WsOutbound message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            LOG.warnf("Serialize error: %s", e.getMessage());
            return;
        }
        Set<String> connectionIds = roomManager.getConnectionIdsInRoom(roomId);
        for (WebSocketConnection conn : openConnections.listAll()) {
            String connRoom = conn.userData().get(KEY_ROOM_ID);
            if (roomId.equals(connRoom)) {
                conn.sendText(json).subscribe().asCompletionStage();
            }
        }
    }
}
