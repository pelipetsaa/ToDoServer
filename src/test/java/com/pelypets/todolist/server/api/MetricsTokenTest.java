package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.metrics.token=scrape-token")
class MetricsTokenTest extends ApiTest {

    @Test
    void metricsRequireTheTokenWhenOneIsConfigured() {
        assertThat(get(OpsEndpointsTest.PROMETHEUS, null).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(OpsEndpointsTest.PROMETHEUS, "wrong").status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(OpsEndpointsTest.PROMETHEUS, "scrape-token").status()).isEqualTo(HttpStatus.OK);
    }
}
