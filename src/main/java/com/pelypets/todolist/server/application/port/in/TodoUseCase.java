package com.pelypets.todolist.server.application.port.in;

import com.pelypets.todolist.server.domain.model.Todo;
import com.pelypets.todolist.server.domain.model.TodoDraft;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A user's todos. A todo of another user behaves as if it did not exist. */
public interface TodoUseCase {

    /** Todos with {@code dueAt} in {@code [from, to)}, sorted by due date. */
    List<Todo> list(UUID userId, Instant from, Instant to);

    Todo get(UUID userId, UUID todoId);

    Todo create(UUID userId, TodoDraft draft);

    Todo update(UUID userId, UUID todoId, TodoDraft draft);

    void delete(UUID userId, UUID todoId);
}
