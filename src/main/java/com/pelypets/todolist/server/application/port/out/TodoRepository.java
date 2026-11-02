package com.pelypets.todolist.server.application.port.out;

import com.pelypets.todolist.server.domain.model.Todo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TodoRepository {

    List<Todo> findDueBetween(UUID userId, Instant from, Instant to);

    Optional<Todo> findById(UUID userId, UUID id);

    long countForUser(UUID userId);

    /** @throws com.pelypets.todolist.server.domain.exception.UnauthorizedException when the user no longer exists */
    Todo insert(Todo todo);

    /**
     * Stores the todo if it still has {@code todo.version()} and returns it with the next version. Empty when the todo
     * no longer exists or another update came first.
     */
    Optional<Todo> update(Todo todo);

    boolean delete(UUID userId, UUID id);
}
