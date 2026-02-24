package com.educagame.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class RoomResourceTest {

    @Test
    void listThemes() {
        given()
                .when().get("/api/themes")
                .then()
                .statusCode(200)
                .body("$", notNullValue())
                .body("$", hasItem("default"));
    }

    @Test
    void createRoom() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"theme\":\"default\",\"gameType\":\"ROLETRANDO\",\"privateRoom\":false}")
                .when().post("/api/rooms")
                .then()
                .statusCode(201)
                .body("roomId", notNullValue())
                .body("theme", equalTo("default"))
                .body("gameType", equalTo("ROLETRANDO"));
    }

    @Test
    void listRooms() {
        given()
                .when().get("/api/rooms")
                .then()
                .statusCode(200)
                .body("$", notNullValue());
    }
}
