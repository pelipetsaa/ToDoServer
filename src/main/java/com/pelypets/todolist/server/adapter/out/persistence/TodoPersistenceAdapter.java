package com.pelypets.todolist.server.adapter.out.persistence;

import com.pelypets.todolist.server.application.port.out.TodoRepository;
import com.pelypets.todolist.server.domain.model.Todo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class TodoPersistenceAdapter implements TodoRepository {

    private final TodoJpaRepository todos;

    TodoPersistenceAdapter(TodoJpaRepository todos) {
        this.todos = todos;
    }

    @Override
    public List<Todo> findDueBetween(UUID userId, Instant from, Instant to) {
        return todos.findDueBetween(userId, from, to).stream().map(TodoEntity::toDomain).toList();
    }

    @Override
    public Optional<Todo> findById(UUID userId, UUID id) {
        return todos.findByIdAndUserId(id, userId).map(TodoEntity::toDomain);
    }

    @Override
    public Todo insert(Todo todo) {
        return todos.save(TodoEntity.from(todo)).toDomain();
    }

    /** Changes are written by dirty checking when the surrounding transaction commits. */
    @Override
    public Optional<Todo> update(Todo todo) {
        return todos.findByIdAndUserId(todo.id(), todo.userId()).map(entity -> {
            entity.apply(todo);
            return entity.toDomain();
        });
    }

    @Override
    public boolean delete(UUID userId, UUID id) {
        return todos.deleteByIdAndUserId(id, userId) > 0;
    }
}
