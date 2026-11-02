package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pelypets.todolist.server.adapter.in.web.ApiPaths;
import com.pelypets.todolist.server.adapter.in.web.error.ErrorCode;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** Ported from the Ktor server's TodoApiTest. */
class TodoApiTest extends ApiTest {

    record Todo(String id, String title, String description, Instant dueAt, String status, Instant createdAt, Instant updatedAt) {}

    @Test
    void createdTodoCanBeFetchedById() {
        String token = register().accessToken();
        Todo created = createTodo(token, "  Buy milk  ", "2026-10-29T10:00:00Z", "IN_PROGRESS");

        assertThat(created.title()).isEqualTo("Buy milk");
        assertThat(created.status()).isEqualTo("IN_PROGRESS");

        Response fetched = get(todo(created.id()), token);
        assertThat(fetched.status()).isEqualTo(HttpStatus.OK);
        assertThat(read(fetched, Todo.class)).isEqualTo(created);
    }

    @Test
    void optionalFieldsDefaultLikeTheKotlinClientExpects() {
        String token = register().accessToken();

        Response response = call(HttpMethod.POST, ApiPaths.TODOS, Map.of("title", "Minimal", "dueAt", "2026-10-29T10:00:00Z"), token);

        assertThat(response.status()).isEqualTo(HttpStatus.CREATED);
        Todo todo = read(response, Todo.class);
        assertThat(todo.description()).isEmpty();
        assertThat(todo.status()).isEqualTo("TODO");
    }

    @Test
    void listReturnsTodosInTheHalfOpenRangeSortedByDueDate() {
        String token = register().accessToken();
        createTodo(token, "later", "2026-10-29T18:00:00Z", "TODO");
        createTodo(token, "at from", "2026-10-29T00:00:00Z", "TODO");
        createTodo(token, "at to (excluded)", "2026-10-30T00:00:00Z", "TODO");
        createTodo(token, "yesterday", "2026-10-28T23:59:59Z", "TODO");
        createTodo(token, "morning", "2026-10-29T09:30:00Z", "TODO");

        Response response = get(ApiPaths.TODOS + "?from=2026-10-29T00:00:00Z&to=2026-10-30T00:00:00Z", token);

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(Arrays.stream(read(response, Todo[].class)).map(Todo::title)).containsExactly("at from", "morning", "later");
    }

    @Test
    void listValidatesTheRange() {
        String token = register().accessToken();

        assertError(get(ApiPaths.TODOS, token), HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertError(get(ApiPaths.TODOS + "?from=yesterday&to=2026-10-30T00:00:00Z", token),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertError(get(ApiPaths.TODOS + "?from=2026-10-30T00:00:00Z&to=2026-10-29T00:00:00Z", token),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void updateChangesAllEditableFields() {
        String token = register().accessToken();
        Todo created = createTodo(token, "Draft", "2026-10-29T10:00:00Z", "TODO");

        Response response = call(HttpMethod.PUT, todo(created.id()),
                input("Final", "Details", "2026-11-01T08:15:00Z", "DONE"), token);

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        Todo updated = read(response, Todo.class);
        assertThat(updated.title()).isEqualTo("Final");
        assertThat(updated.description()).isEqualTo("Details");
        assertThat(updated.dueAt()).isEqualTo(Instant.parse("2026-11-01T08:15:00Z"));
        assertThat(updated.status()).isEqualTo("DONE");
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
        assertThat(read(get(todo(created.id()), token), Todo.class)).isEqualTo(updated);
    }

    @Test
    void singleTodoResponsesCarryTheVersionAsETag() {
        String token = register().accessToken();
        Response created = call(HttpMethod.POST, ApiPaths.TODOS, input("Versioned", "", "2026-10-29T10:00:00Z", "TODO"), token);
        String id = read(created, Todo.class).id();

        assertThat(created.headers().getETag()).isEqualTo("\"0\"");
        assertThat(get(todo(id), token).headers().getETag()).isEqualTo("\"0\"");
        Response updated = call(HttpMethod.PUT, todo(id), input("Versioned", "v1", "2026-10-29T10:00:00Z", "TODO"), token);
        assertThat(updated.headers().getETag()).isEqualTo("\"1\"");
    }

    @Test
    void anUpdateBasedOnAStaleVersionIsRejectedInsteadOfOverwriting() {
        String token = register().accessToken();
        Todo created = createTodo(token, "Shared", "2026-10-29T10:00:00Z", "TODO");
        Response phone = putIfMatch(token, created.id(), "\"0\"", "Edited on phone");
        assertThat(phone.status()).isEqualTo(HttpStatus.OK);

        assertError(putIfMatch(token, created.id(), "\"0\"", "Edited on laptop"), HttpStatus.CONFLICT, ErrorCode.CONFLICT);
        assertError(putIfMatch(token, created.id(), "garbage", "Edited on laptop"), HttpStatus.CONFLICT, ErrorCode.CONFLICT);

        assertThat(read(get(todo(created.id()), token), Todo.class).title()).isEqualTo("Edited on phone");
        assertThat(putIfMatch(token, created.id(), phone.headers().getETag(), "Merged").status()).isEqualTo(HttpStatus.OK);
        assertThat(putIfMatch(token, created.id(), "*", "Forced").status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listRejectsRangesLongerThanAYear() {
        String token = register().accessToken();

        assertError(get(ApiPaths.TODOS + "?from=2026-01-01T00:00:00Z&to=2027-01-03T00:00:00Z", token),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertThat(get(ApiPaths.TODOS + "?from=2026-01-01T00:00:00Z&to=2027-01-01T00:00:00Z", token).status())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteRemovesTheTodo() {
        String token = register().accessToken();
        Todo created = createTodo(token, "Temp", "2026-10-29T10:00:00Z", "TODO");

        assertThat(call(HttpMethod.DELETE, todo(created.id()), null, token).status()).isEqualTo(HttpStatus.NO_CONTENT);

        assertError(get(todo(created.id()), token), HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        assertError(call(HttpMethod.DELETE, todo(created.id()), null, token), HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    @Test
    void usersCannotSeeOrModifyEachOthersTodos() {
        String owner = register("owner@example.com", "password123").accessToken();
        String intruder = register("intruder@example.com", "password123").accessToken();
        Todo todo = createTodo(owner, "Private", "2026-10-29T10:00:00Z", "TODO");

        assertError(get(todo(todo.id()), intruder), HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        assertError(call(HttpMethod.PUT, todo(todo.id()), input("Hacked", "", "2026-10-29T10:00:00Z", "TODO"), intruder),
                HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        assertError(call(HttpMethod.DELETE, todo(todo.id()), null, intruder), HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);

        Response intruderList = get(ApiPaths.TODOS + "?from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z", intruder);
        assertThat(read(intruderList, Todo[].class)).isEmpty();

        assertThat(read(get(todo(todo.id()), owner), Todo.class).title()).isEqualTo("Private");
    }

    @Test
    void todoInputIsValidated() {
        String token = register().accessToken();

        assertError(call(HttpMethod.POST, ApiPaths.TODOS, input("   ", "", "2026-10-29T10:00:00Z", "TODO"), token),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertError(call(HttpMethod.POST, ApiPaths.TODOS, input("x".repeat(201), "", "2026-10-29T10:00:00Z", "TODO"), token),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertError(call(HttpMethod.POST, ApiPaths.TODOS, Map.of("title", "No due date"), token),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertError(call(HttpMethod.POST, ApiPaths.TODOS, input("Bad status", "", "2026-10-29T10:00:00Z", "LATER"), token),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
        assertError(get(todo("not-a-uuid"), token), HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    private Response putIfMatch(String token, String id, String ifMatch, String title) {
        return client().put().uri(todo(id))
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.setIfMatch(ifMatch);
                })
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(input(title, "", "2026-10-29T10:00:00Z", "TODO"))
                .exchange((request, response) -> new Response(HttpStatus.valueOf(response.getStatusCode().value()),
                        new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                        response.getHeaders()));
    }

    private Todo createTodo(String token, String title, String dueAt, String status) {
        Response response = call(HttpMethod.POST, ApiPaths.TODOS, input(title, "", dueAt, status), token);
        assertThat(response.status()).as(response.body()).isEqualTo(HttpStatus.CREATED);
        return read(response, Todo.class);
    }

    private static Map<String, Object> input(String title, String description, String dueAt, String status) {
        var body = new HashMap<String, Object>();
        body.put("title", title);
        body.put("description", description);
        body.put("dueAt", dueAt);
        body.put("status", status);
        return body;
    }

    private static String todo(String id) {
        return ApiPaths.TODOS + "/" + id;
    }
}
