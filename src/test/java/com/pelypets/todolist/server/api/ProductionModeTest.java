package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.environment=production", "app.jwt.secret=production-secret-production-secret-42"})
class ProductionModeTest extends ApiTest {

    @Test
    void productionWithoutATokenDoesNotExposeMetrics() {
        assertThat(get(OpsEndpointsTest.PROMETHEUS, null).status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void tokensAreSignedWithTheConfiguredSecret() {
        assertThat(get("/api/v1/me", register().accessToken()).status()).isEqualTo(HttpStatus.OK);
    }
}
