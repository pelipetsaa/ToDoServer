package com.pelypets.todolist.server.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Todo(
        UUID id,
        UUID userId,
        String title,
        String description,
        Instant dueAt,
        TodoStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static Todo create(UUID userId, TodoDraft draft, Instant now) {
        return new Todo(UUID.randomUUID(), userId, draft.title(), draft.description(), draft.dueAt(), draft.status(), now, now);
    }

    public Todo apply(TodoDraft draft, Instant now) {
        return new Todo(id, userId, draft.title(), draft.description(), draft.dueAt(), draft.status(), createdAt, now);
    }
}
