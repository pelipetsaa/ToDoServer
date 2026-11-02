package com.pelypets.todolist.server.adapter.out.persistence;

import com.pelypets.todolist.server.domain.model.Todo;
import com.pelypets.todolist.server.domain.model.TodoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "todos")
class TodoEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TodoStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TodoEntity() {}

    static TodoEntity from(Todo todo) {
        TodoEntity entity = new TodoEntity();
        entity.id = todo.id();
        entity.userId = todo.userId();
        entity.createdAt = todo.createdAt();
        entity.apply(todo);
        return entity;
    }

    void apply(Todo todo) {
        title = todo.title();
        description = todo.description();
        dueAt = todo.dueAt();
        status = todo.status();
        updatedAt = todo.updatedAt();
    }

    Todo toDomain() {
        return new Todo(id, userId, title, description, dueAt, status, createdAt, updatedAt);
    }

    @Override
    public UUID getId() {
        return id;
    }
}
