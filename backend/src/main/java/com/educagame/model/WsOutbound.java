package com.educagame.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Server-to-client WebSocket message. type + payload drive frontend state.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsOutbound {

    private String type;
    private Object payload;

    public WsOutbound() {
    }

    public WsOutbound(String type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public static WsOutbound state(GameSession session) {
        return new WsOutbound("STATE", session);
    }

    public static WsOutbound event(String eventType, Object data) {
        return new WsOutbound(eventType, data);
    }

    public static WsOutbound error(String message) {
        return new WsOutbound("ERROR", new ErrorPayload(message));
    }

    public static WsOutbound pong() {
        return new WsOutbound("PONG", null);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public static class ErrorPayload {
        private String message;

        public ErrorPayload(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
