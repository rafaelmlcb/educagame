package com.educagame.resource;

import com.educagame.model.GameResult;
import com.educagame.model.GameType;
import com.educagame.service.GameHistoryService;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/api/stats")
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

    @Inject
    GameHistoryService gameHistoryService;

    @GET
    @Path("/summary")
    public Response summary() {
        long uptimeMs = gameHistoryService.getUptimeMs();
        long totalGames = gameHistoryService.getTotalGamesCreated();
        Map<GameType, Long> byType = gameHistoryService.getGamesCreatedByType();
        return Response.ok(Map.of(
                "uptimeMs", uptimeMs,
                "totalGamesCreated", totalGames,
                "gamesByType", byType
        )).build();
    }

    @GET
    @Path("/leaderboard")
    public Response leaderboard(@QueryParam("mode") String mode, @QueryParam("limit") @DefaultValue("10") int limit) {
        GameType type;
        try {
            type = mode == null || mode.isBlank() ? GameType.ROLETRANDO : GameType.valueOf(mode.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Invalid mode")).build();
        }
        List<Map<String, Object>> list = gameHistoryService.getLeaderboard(type, limit);
        return Response.ok(list).build();
    }

    @GET
    @Path("/history")
    public Response history(@QueryParam("limit") @DefaultValue("20") int limit) {
        List<GameResult> list = gameHistoryService.getRecentResults(limit);
        return Response.ok(list).build();
    }
}
