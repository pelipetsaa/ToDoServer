package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pelypets.todolist.server.adapter.in.web.ApiPaths;
import com.pelypets.todolist.server.adapter.in.web.error.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.todos.max-per-user=2", "app.rate-limit.login-attempts-per-account-per-minute=2"})
class LimitsTest extends ApiTest {

    private static final Map<String, String> TODO = Map.of("title", "Todo", "dueAt", "2026-10-29T10:00:00Z");

    @Test
    void aUserCannotStoreMoreTodosThanTheLimit() {
        String token = register().accessToken();
        for (int i = 0; i < 2; i++) {
            assertThat(call(HttpMethod.POST, ApiPaths.TODOS, TODO, token).status()).isEqualTo(HttpStatus.CREATED);
        }

        assertError(call(HttpMethod.POST, ApiPaths.TODOS, TODO, token), HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void passwordGuessesAgainstOneAccountAreLimitedWhateverTheirSource() {
        register("target@example.com", "password123");
        Map<String, String> guess = Map.of("email", "Target@example.com", "password", "wrong-guess");
        for (int i = 0; i < 2; i++) {
            assertError(post(ApiPaths.AUTH + "/login", guess), HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }

        Response limited = post(ApiPaths.AUTH + "/login", Map.of("email", "target@example.com", "password", "password123"));

        assertError(limited, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS);
        assertThat(post(ApiPaths.AUTH + "/login", Map.of("email", "other@example.com", "password", "x".repeat(8))).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
