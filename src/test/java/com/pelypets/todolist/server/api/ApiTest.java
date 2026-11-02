package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pelypets.todolist.server.TestcontainersConfiguration;
import com.pelypets.todolist.server.adapter.in.web.ApiPaths;
import com.pelypets.todolist.server.adapter.in.web.error.ApiError;
import com.pelypets.todolist.server.adapter.in.web.error.ErrorCode;
import com.pelypets.todolist.server.application.port.out.GoogleTokenVerifier;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Base of the black-box API tests: a real server on a random port, real PostgreSQL, a plain HTTP client. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ApiTest.FakeGoogleConfig.class})
public abstract class ApiTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeGoogleConfig {

        @Bean
        @Primary
        FakeGoogleTokenVerifier fakeGoogleTokenVerifier() {
            return new FakeGoogleTokenVerifier();
        }
    }

    public record Response(HttpStatus status, String body, HttpHeaders headers) {}

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected FakeGoogleTokenVerifier google;

    @Autowired
    private GoogleTokenVerifier activeGoogleVerifier;

    private RestClient client;

    @BeforeEach
    void resetState() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
        jdbc.sql("TRUNCATE users CASCADE").update();
        google.identities.clear();
        assertThat(activeGoogleVerifier).isSameAs(google);
    }

    protected RestClient client() {
        return client;
    }

    protected Response call(HttpMethod method, String path, Object body, String token) {
        var request = client.method(method).uri(path);
        if (token != null) request.headers(headers -> headers.setBearerAuth(token));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
        var entity = request.retrieve().toEntity(String.class);
        return new Response(
                HttpStatus.valueOf(entity.getStatusCode().value()),
                entity.getBody(),
                entity.getHeaders());
    }

    protected Response get(String path, String token) {
        return call(HttpMethod.GET, path, null, token);
    }

    protected Response post(String path, Object body) {
        return call(HttpMethod.POST, path, body, null);
    }

    protected <T> T read(Response response, Class<T> type) {
        return json.readValue(response.body(), type);
    }

    protected TokenPair register(String email, String password) {
        Response response = post(ApiPaths.AUTH + "/register", Map.of("email", email, "password", password));
        assertThat(response.status()).isEqualTo(HttpStatus.CREATED);
        return read(response, TokenPair.class);
    }

    protected TokenPair register() {
        return register("user@example.com", "password123");
    }

    protected void assertError(Response response, HttpStatus status, ErrorCode code) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        assertThat(read(response, ApiError.class).code()).isEqualTo(code);
    }
}
