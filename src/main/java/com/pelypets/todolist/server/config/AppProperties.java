package com.pelypets.todolist.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Everything under {@code app.*}; bound once at startup and validated, so a bad deployment fails fast. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull AppEnvironment environment,
        /* Trust X-Forwarded-For; enable only behind a reverse proxy that sets it. */
        boolean trustProxy,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Password password,
        @Valid @NotNull Google google,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Metrics metrics) {

    /** Signing key used only when no JWT_SECRET is set during local development. Same as the Ktor server's. */
    static final String DEV_JWT_SECRET = "dev-only-secret-change-me-please-32b";

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_JWT_SECRET_LENGTH = 32;

    public AppProperties {
        if (jwt != null && environment != null) jwt = jwt.withSecret(resolveJwtSecret(jwt.secret(), environment));
    }

    public boolean isProduction() {
        return environment == AppEnvironment.PRODUCTION;
    }

    /** Production must never fall back to the development key: anyone who has read this repository could sign tokens. */
    static String resolveJwtSecret(String value, AppEnvironment environment) {
        if (value == null || value.isBlank()) {
            if (environment == AppEnvironment.PRODUCTION) {
                throw new IllegalStateException("JWT_SECRET must be set when APP_ENV=production");
            }
            return DEV_JWT_SECRET;
        }
        if (value.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_JWT_SECRET_LENGTH + " characters");
        }
        if (environment == AppEnvironment.PRODUCTION && value.equals(DEV_JWT_SECRET)) {
            throw new IllegalStateException("JWT_SECRET must not be the development key when APP_ENV=production");
        }
        return value;
    }

    public record Jwt(
            String secret,
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotBlank String realm,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {

        Jwt withSecret(String resolved) {
            return new Jwt(resolved, issuer, audience, realm, accessTokenTtl, refreshTokenTtl);
        }

        @Override
        public String toString() {
            return "Jwt[issuer=" + issuer + ", audience=" + audience + ", secret=****]";
        }
    }

    /** bcrypt work factor; tests lower it to keep sign-ups fast. */
    public record Password(@Min(4) @Max(31) int bcryptCost) {}

    /** OAuth client IDs (web, android, ios) accepted as the audience of Google ID tokens. */
    public record Google(List<String> clientIds) {

        public Google {
            clientIds = clientIds == null
                    ? List.of()
                    : clientIds.stream().map(String::strip).filter(id -> !id.isEmpty()).toList();
        }
    }

    /** Requests per client IP per minute across register, login and Google sign-in. */
    public record RateLimit(@Positive int authRequestsPerMinute) {}

    /** Bearer token Prometheus sends; without it production does not expose metrics, development does. */
    public record Metrics(String token) {

        public Optional<String> bearerToken() {
            return Optional.ofNullable(token).filter(it -> !it.isBlank());
        }

        @Override
        public String toString() {
            return "Metrics[token=" + (bearerToken().isPresent() ? "****" : "none") + "]";
        }
    }
}
