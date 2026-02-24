package com.educagame.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsStart implements WsInbound {

    public static final String TYPE = "START";

    @Override
    public String getType() {
        return TYPE;
    }
}
