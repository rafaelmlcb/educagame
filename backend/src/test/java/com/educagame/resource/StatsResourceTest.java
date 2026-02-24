package com.educagame.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class StatsResourceTest {

    @Test
    void summary() {
        given()
                .when().get("/api/stats/summary")
                .then()
                .statusCode(200)
                .body("uptimeMs", notNullValue())
                .body("totalGamesCreated", notNullValue())
                .body("gamesByType", notNullValue());
    }

    @Test
    void leaderboard() {
        given()
                .when().get("/api/stats/leaderboard?mode=ROLETRANDO&limit=5")
                .then()
                .statusCode(200)
                .body("$", notNullValue());
    }
}
