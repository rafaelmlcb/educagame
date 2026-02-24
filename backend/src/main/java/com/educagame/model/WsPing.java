package com.educagame.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsPing implements WsInbound {

    public static final String TYPE = "PING";

    @Override
    public String getType() {
        return TYPE;
    }
}
