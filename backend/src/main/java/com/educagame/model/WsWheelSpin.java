package com.educagame.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsWheelSpin implements WsInbound {

    public static final String TYPE = "WHEEL_SPIN";

    private Integer segmentIndex; // optional; server can draw if null

    @Override
    public String getType() {
        return TYPE;
    }

    public Integer getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(Integer segmentIndex) {
        this.segmentIndex = segmentIndex;
    }
}
