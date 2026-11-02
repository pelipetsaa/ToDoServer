package com.pelypets.todolist.server.adapter.in.web.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Error")
public record ApiError(ErrorCode code, String message) {}
