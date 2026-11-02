package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pelypets.todolist.server.adapter.in.web.ApiPaths;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OpsEndpointsTest extends ApiTest {

    static final String PROMETHEUS = "/actuator/prometheus";

    @Test
    void readinessChecksTheDatabase() {
        Response response = get(ApiPaths.HEALTH_READY, null);

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void kubernetesProbesAreExposed() {
        assertThat(get("/actuator/health/liveness", null).status()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health/readiness", null).status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void metricsAreOpenInDevelopmentAndCountRequestsAndBusinessEvents() {
        register();
        get(ApiPaths.HEALTH, null);

        Response response = get(PROMETHEUS, null);

        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).contains("http_server_requests").contains("todo_users_registered");
    }

    @Test
    void unknownPathsAreNotServed() {
        assertThat(get("/nope", null).status()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        assertThat(get(ApiPaths.BASE + "/nope", null).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void swaggerUiAndSpecArePublic() {
        assertThat(get("/v3/api-docs", null).status()).isEqualTo(HttpStatus.OK);
        assertThat(get("/docs", null).status()).isIn(HttpStatus.OK, HttpStatus.FOUND);
    }
}
