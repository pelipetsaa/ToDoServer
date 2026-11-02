package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pelypets.todolist.server.adapter.in.web.ApiPaths;
import com.pelypets.todolist.server.application.port.in.MaintenanceUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class OpsEndpointsTest extends ApiTest {

    static final String PROMETHEUS = "/actuator/prometheus";

    @Autowired
    private MaintenanceUseCase maintenance;

    @Test
    void thePurgeRemovesExpiredTokensAndUsedTokenRecords() {
        register();
        jdbc.sql("""
                        INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at)
                        SELECT gen_random_uuid(), id, 'expired', now() - interval '1 day' FROM users""").update();
        jdbc.sql("""
                        INSERT INTO used_refresh_tokens (token_hash, user_id, family_id, expires_at)
                        SELECT 'used-expired', id, gen_random_uuid(), now() - interval '1 day' FROM users""").update();

        assertThat(maintenance.purgeExpiredRefreshTokens()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM refresh_tokens").query(Integer.class).single()).isEqualTo(1);
    }

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
        assertThat(get("/nope", null).status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(ApiPaths.BASE + "/nope", null).status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void swaggerUiAndSpecArePublic() {
        assertThat(get("/v3/api-docs", null).status()).isEqualTo(HttpStatus.OK);
        assertThat(get("/docs", null).status()).isIn(HttpStatus.OK, HttpStatus.FOUND);
    }
}
