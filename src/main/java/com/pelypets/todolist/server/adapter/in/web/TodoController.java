package com.pelypets.todolist.server.adapter.in.web;

import com.pelypets.todolist.server.adapter.in.web.dto.TodoDtos.TodoInput;
import com.pelypets.todolist.server.adapter.in.web.dto.TodoDtos.TodoResponse;
import com.pelypets.todolist.server.adapter.in.web.security.CurrentUserId;
import com.pelypets.todolist.server.application.port.in.TodoUseCase;
import com.pelypets.todolist.server.domain.exception.NotFoundException;
import com.pelypets.todolist.server.domain.model.Todo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single-todo responses carry the todo's version as an {@code ETag}. A client that sends it back as {@code If-Match}
 * on update gets {@code 409 CONFLICT} instead of overwriting a change made from another device.
 */
@RestController
@RequestMapping(ApiPaths.TODOS)
@Tag(name = "todos", description = "The signed-in user's todos.")
@SecurityRequirement(name = "bearerAuth")
public class TodoController {

    private final TodoUseCase todos;

    public TodoController(TodoUseCase todos) {
        this.todos = todos;
    }

    @GetMapping
    @Operation(operationId = "listTodos", summary = "Todos due in a time range",
            description = "Todos with `dueAt` in `[from, to)`, sorted by due date. The range spans at most a year.")
    public List<TodoResponse> list(
            @CurrentUserId UUID userId,
            @Parameter(example = "2026-10-29T00:00:00Z") @RequestParam Instant from,
            @Parameter(example = "2026-10-30T00:00:00Z") @RequestParam Instant to) {
        return todos.list(userId, from, to).stream().map(TodoResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTodo", summary = "One todo", responses = @ApiResponse(responseCode = "200",
            description = "The todo", headers = @Header(name = HttpHeaders.ETAG, description = "The todo's version")))
    public ResponseEntity<TodoResponse> get(
            @CurrentUserId UUID userId, @PathVariable @Parameter(schema = @Schema(format = "uuid")) String id) {
        return withVersion(HttpStatus.OK, todos.get(userId, todoId(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTodo", summary = "Create a todo", responses = @ApiResponse(responseCode = "201",
            description = "The created todo", headers = @Header(name = HttpHeaders.ETAG, description = "The todo's version")))
    public ResponseEntity<TodoResponse> create(@CurrentUserId UUID userId, @Valid @RequestBody TodoInput body) {
        return withVersion(HttpStatus.CREATED, todos.create(userId, body.toDraft()));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateTodo", summary = "Replace a todo's editable fields", responses = @ApiResponse(
            responseCode = "200", description = "The updated todo",
            headers = @Header(name = HttpHeaders.ETAG, description = "The todo's new version")))
    public ResponseEntity<TodoResponse> update(
            @CurrentUserId UUID userId,
            @PathVariable @Parameter(schema = @Schema(format = "uuid")) String id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
            @Parameter(description = "The `ETag` the edit is based on; `409 CONFLICT` when the todo changed since")
            String ifMatch,
            @Valid @RequestBody TodoInput body) {
        return withVersion(HttpStatus.OK, todos.update(userId, todoId(id), body.toDraft(), expectedVersion(ifMatch)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteTodo", summary = "Delete a todo")
    public void delete(@CurrentUserId UUID userId, @PathVariable @Parameter(schema = @Schema(format = "uuid")) String id) {
        todos.delete(userId, todoId(id));
    }

    private static ResponseEntity<TodoResponse> withVersion(HttpStatus status, Todo todo) {
        return ResponseEntity.status(status).eTag(Long.toString(todo.version())).body(TodoResponse.from(todo));
    }

    /** {@code *} or no header matches any version; a value that is not one of our ETags matches none. */
    static OptionalLong expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) return OptionalLong.empty();
        String tag = ifMatch.strip();
        if (tag.startsWith("W/")) tag = tag.substring(2);
        if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) tag = tag.substring(1, tag.length() - 1);
        try {
            return OptionalLong.of(Long.parseLong(tag));
        } catch (NumberFormatException e) {
            return OptionalLong.of(-1);
        }
    }

    /** An id that is not a UUID cannot name a todo, so it is reported like any other missing todo. */
    private static UUID todoId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw NotFoundException.todo();
        }
    }
}
