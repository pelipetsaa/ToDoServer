package com.pelypets.todolist.server.adapter.out.persistence;

import com.pelypets.todolist.server.application.port.out.TodoRepository;
import com.pelypets.todolist.server.domain.exception.UnauthorizedException;
import com.pelypets.todolist.server.domain.model.Todo;
import com.pelypets.todolist.server.domain.model.TodoStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class TodoPersistenceAdapter implements TodoRepository {

    private static final String COLUMNS = "id, user_id, title, description, due_at, status, version, created_at, updated_at";

    private static final RowMapper<Todo> TODO = (row, number) -> new Todo(
            row.getObject("id", UUID.class),
            row.getObject("user_id", UUID.class),
            row.getString("title"),
            row.getString("description"),
            Timestamps.read(row, "due_at"),
            TodoStatus.valueOf(row.getString("status")),
            row.getLong("version"),
            Timestamps.read(row, "created_at"),
            Timestamps.read(row, "updated_at"));

    private final JdbcClient jdbc;

    TodoPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Todo> findDueBetween(UUID userId, Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT %s FROM todos
                        WHERE user_id = :userId AND due_at >= :from AND due_at < :to
                        ORDER BY due_at, created_at""".formatted(COLUMNS))
                .param("userId", userId)
                .param("from", Timestamps.of(from))
                .param("to", Timestamps.of(to))
                .query(TODO)
                .list();
    }

    @Override
    public Optional<Todo> findById(UUID userId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM todos WHERE id = :id AND user_id = :userId")
                .param("id", id)
                .param("userId", userId)
                .query(TODO)
                .optional();
    }

    @Override
    public long countForUser(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM todos WHERE user_id = :userId").param("userId", userId).query(Long.class).single();
    }

    @Override
    public Todo insert(Todo todo) {
        try {
            jdbc.sql("""
                            INSERT INTO todos (id, user_id, title, description, due_at, status, version, created_at, updated_at)
                            VALUES (:id, :userId, :title, :description, :dueAt, :status, :version, :createdAt, :updatedAt)""")
                    .param("id", todo.id())
                    .param("userId", todo.userId())
                    .param("title", todo.title())
                    .param("description", todo.description())
                    .param("dueAt", Timestamps.of(todo.dueAt()))
                    .param("status", todo.status().name())
                    .param("version", todo.version())
                    .param("createdAt", Timestamps.of(todo.createdAt()))
                    .param("updatedAt", Timestamps.of(todo.updatedAt()))
                    .update();
        } catch (DataIntegrityViolationException e) {
            // The foreign key failed: the account was deleted while its access token is still valid.
            throw new UnauthorizedException("User no longer exists");
        }
        return todo;
    }

    @Override
    public Optional<Todo> update(Todo todo) {
        return jdbc.sql("""
                        UPDATE todos
                        SET title = :title, description = :description, due_at = :dueAt, status = :status,
                            updated_at = :updatedAt, version = version + 1
                        WHERE id = :id AND user_id = :userId AND version = :version
                        RETURNING %s""".formatted(COLUMNS))
                .param("title", todo.title())
                .param("description", todo.description())
                .param("dueAt", Timestamps.of(todo.dueAt()))
                .param("status", todo.status().name())
                .param("updatedAt", Timestamps.of(todo.updatedAt()))
                .param("id", todo.id())
                .param("userId", todo.userId())
                .param("version", todo.version())
                .query(TODO)
                .optional();
    }

    @Override
    public boolean delete(UUID userId, UUID id) {
        return jdbc.sql("DELETE FROM todos WHERE id = :id AND user_id = :userId")
                .param("id", id)
                .param("userId", userId)
                .update() > 0;
    }
}
