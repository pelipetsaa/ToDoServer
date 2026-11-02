package com.pelypets.todolist.server.adapter.in.web.dto;

import com.pelypets.todolist.server.domain.model.Todo;
import com.pelypets.todolist.server.domain.model.TodoDraft;
import com.pelypets.todolist.server.domain.model.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class TodoDtos {

    private TodoDtos() {}

    /** Kotlin clients omit fields that have their default value, so description and status are optional. */
    @Schema(name = "TodoInput")
    public record TodoInput(
            @Schema(description = "Trimmed; must not be blank", maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "title is required") String title,
            @Schema(maxLength = 2000, defaultValue = "") String description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "dueAt is required") Instant dueAt,
            @Schema(defaultValue = "TODO") TodoStatus status) {

        public TodoDraft toDraft() {
            return new TodoDraft(title, description, dueAt, status);
        }
    }

    @Schema(name = "Todo")
    public record TodoResponse(
            @Schema(format = "uuid") String id,
            String title,
            String description,
            Instant dueAt,
            TodoStatus status,
            Instant createdAt,
            Instant updatedAt) {

        public static TodoResponse from(Todo todo) {
            return new TodoResponse(todo.id().toString(), todo.title(), todo.description(), todo.dueAt(), todo.status(),
                    todo.createdAt(), todo.updatedAt());
        }
    }
}
