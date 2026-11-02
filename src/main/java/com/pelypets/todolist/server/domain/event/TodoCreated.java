package com.pelypets.todolist.server.domain.event;

import java.util.UUID;

public record TodoCreated(UUID userId, UUID todoId) {}
