package com.pelypets.todolist.server.application.port.in;

import com.pelypets.todolist.server.domain.model.Todo;
import com.pelypets.todolist.server.domain.model.TodoDraft;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/** A user's todos. A todo of another user behaves as if it did not exist. */
public interface TodoUseCase {

    /** Todos with {@code dueAt} in {@code [from, to)}, sorted by due date. The range is limited in length. */
    List<Todo> list(UUID userId, Instant from, Instant to);

    Todo get(UUID userId, UUID todoId);

    Todo create(UUID userId, TodoDraft draft);

    /**
     * @param expectedVersion the version the client edited, when it sent one
     * @throws com.pelypets.todolist.server.domain.exception.ConflictException when the todo no longer has that version,
     *     or another update came first
     */
    Todo update(UUID userId, UUID todoId, TodoDraft draft, OptionalLong expectedVersion);

    void delete(UUID userId, UUID todoId);
}
