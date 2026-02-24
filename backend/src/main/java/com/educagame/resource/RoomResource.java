package com.educagame.resource;

import com.educagame.model.GameSession;
import com.educagame.model.GameType;
import com.educagame.model.Room;
import com.educagame.service.DataLoaderService;
import com.educagame.service.GameHistoryService;
import com.educagame.service.RoomManager;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * REST API for room creation, listing and theme discovery.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    private static final Logger LOG = Logger.getLogger(RoomResource.class);

    @Inject
    RoomManager roomManager;
    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;

    @GET
    @Path("/themes")
    public List<String> listThemes() {
        return dataLoaderService.discoverThemes();
    }

    @GET
    @Path("/themes/{theme}/wheel")
    public Response getWheelData(@PathParam("theme") String theme) {
        if (theme == null || theme.isBlank()) return Response.status(Response.Status.BAD_REQUEST).build();
        List<Map<String, Object>> data = dataLoaderService.getWheelSegments(theme);
        return Response.ok(data).build();
    }

    @GET
    @Path("/themes/{theme}/quiz")
    public Response getQuizData(@PathParam("theme") String theme) {
        if (theme == null || theme.isBlank()) return Response.status(Response.Status.BAD_REQUEST).build();
        List<Map<String, Object>> data = dataLoaderService.getQuizQuestions(theme);
        return Response.ok(data).build();
    }

    @GET
    @Path("/themes/{theme}/millionaire")
    public Response getMillionaireData(@PathParam("theme") String theme) {
        if (theme == null || theme.isBlank()) return Response.status(Response.Status.BAD_REQUEST).build();
        List<Map<String, Object>> data = dataLoaderService.getMillionaireQuestions(theme);
        return Response.ok(data).build();
    }

    @GET
    @Path("/rooms")
    public List<Room> listRooms() {
        return roomManager.listPublicRooms();
    }

    @POST
    @Path("/rooms")
    public Response createRoom(CreateRoomRequest request) {
        if (request == null || request.getGameType() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "gameType required")).build();
        }
        GameType type;
        try {
            type = GameType.valueOf(request.getGameType().toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Invalid gameType")).build();
        }
        String theme = request.getTheme() != null ? request.getTheme() : "default";
        boolean isPrivate = request.getPrivateRoom() != null && request.getPrivateRoom();
        GameSession session = roomManager.createRoom(theme, type, isPrivate);
        gameHistoryService.recordGameCreated(type);
        return Response.status(Response.Status.CREATED).entity(Map.of(
                "roomId", session.getRoomId(),
                "theme", session.getTheme(),
                "gameType", session.getGameType().name()
        )).build();
    }

    @GET
    @Path("/rooms/{roomId}")
    public Response getRoom(@PathParam("roomId") String roomId) {
        if (!ValidationUtil.isValidRoomId(roomId)) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return roomManager.getSession(roomId)
                .map(s -> Response.ok(new Room(
                        s.getRoomId(),
                        s.getTheme(),
                        s.getGameType(),
                        s.getPlayers().size(),
                        10,
                        false
                )).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    public static class CreateRoomRequest {
        private String theme;
        private String gameType;
        private Boolean privateRoom;

        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }
        public String getGameType() { return gameType; }
        public void setGameType(String gameType) { this.gameType = gameType; }
        public Boolean getPrivateRoom() { return privateRoom; }
        public void setPrivateRoom(Boolean privateRoom) { this.privateRoom = privateRoom; }
    }
}
