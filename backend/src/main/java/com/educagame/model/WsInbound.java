package com.educagame.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Client-to-server WebSocket message envelope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "JOIN", value = WsJoin.class),
        @JsonSubTypes.Type(name = "START", value = WsStart.class),
        @JsonSubTypes.Type(name = "WHEEL_SPIN", value = WsWheelSpin.class),
        @JsonSubTypes.Type(name = "ANSWER", value = WsAnswer.class),
        @JsonSubTypes.Type(name = "PING", value = WsPing.class)
})
public interface WsInbound {
    String getType();
}
