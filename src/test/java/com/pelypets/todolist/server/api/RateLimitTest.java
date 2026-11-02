package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pelypets.todolist.server.adapter.in.web.ApiPaths;
import com.pelypets.todolist.server.adapter.in.web.error.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.rate-limit.auth-requests-per-minute=3")
class RateLimitTest extends ApiTest {

    private static final Map<String, String> CREDENTIALS = Map.of("email", "user@example.com", "password", "password123");

    @Test
    void signInEndpointsAreRateLimitedWhileTokenRefreshAndTheRestOfTheApiAreNot() {
        TokenPair tokens = register();
        for (int i = 0; i < 2; i++) {
            assertThat(post(ApiPaths.AUTH + "/login", CREDENTIALS).status()).isEqualTo(HttpStatus.OK);
        }

        Response limited = post(ApiPaths.AUTH + "/login", CREDENTIALS);
        assertError(limited, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS);
        assertThat(limited.headers().getFirst("Retry-After")).isNotBlank();

        assertThat(get(ApiPaths.ME, tokens.accessToken()).status()).isEqualTo(HttpStatus.OK);
        String refreshToken = tokens.refreshToken();
        for (int i = 0; i < 5; i++) {
            Response refreshed = post(ApiPaths.AUTH + "/refresh", Map.of("refreshToken", refreshToken));
            assertThat(refreshed.status()).isEqualTo(HttpStatus.OK);
            refreshToken = read(refreshed, TokenPair.class).refreshToken();
        }
    }
}
