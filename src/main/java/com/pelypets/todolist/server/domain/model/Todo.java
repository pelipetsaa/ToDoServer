package com.pelypets.todolist.server.domain.model;

import java.time.Instant;
import java.util.UUID;

/** {@code version} grows by one with every update; clients send it back to detect concurrent edits. */
public record Todo(
        UUID id,
        UUID userId,
        String title,
        String description,
        Instant dueAt,
        TodoStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static Todo create(UUID userId, TodoDraft draft, Instant now) {
        return new Todo(UUID.randomUUID(), userId, draft.title(), draft.description(), draft.dueAt(), draft.status(), 0,
                now, now);
    }

    /** The edited todo; the repository stores it with the next version. */
    public Todo apply(TodoDraft draft, Instant now) {
        return new Todo(id, userId, draft.title(), draft.description(), draft.dueAt(), draft.status(), version, createdAt,
                now);
    }
}
