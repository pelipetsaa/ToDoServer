package com.pelypets.todolist.server.domain.event;

import java.util.UUID;

public record UserRegistered(UUID userId, Method method) {

    public enum Method {
        PASSWORD,
        GOOGLE
    }
}
