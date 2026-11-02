package com.pelypets.todolist.server.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Health")
public record HealthResponse(@Schema(allowableValues = {"UP", "DOWN"}) String status) {

    public static final HealthResponse UP = new HealthResponse("UP");
    public static final HealthResponse DOWN = new HealthResponse("DOWN");
}
