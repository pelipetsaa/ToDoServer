package com.pelypets.todolist.server.application.port.out;

import com.pelypets.todolist.server.domain.model.Todo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TodoRepository {

    List<Todo> findDueBetween(UUID userId, Instant from, Instant to);

    Optional<Todo> findById(UUID userId, UUID id);

    Todo insert(Todo todo);

    /** Empty when the todo no longer exists. */
    Optional<Todo> update(Todo todo);

    boolean delete(UUID userId, UUID id);
}
