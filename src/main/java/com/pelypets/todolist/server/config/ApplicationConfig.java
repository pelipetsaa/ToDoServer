package com.pelypets.todolist.server.config;

import com.pelypets.todolist.server.adapter.out.security.BcryptPasswordHasher;
import com.pelypets.todolist.server.adapter.out.security.GoogleApiTokenVerifier;
import com.pelypets.todolist.server.adapter.out.security.SecureRefreshTokenGenerator;
import com.pelypets.todolist.server.application.port.out.GoogleTokenVerifier;
import com.pelypets.todolist.server.application.port.out.PasswordHasher;
import com.pelypets.todolist.server.application.port.out.RefreshTokenGenerator;
import com.pelypets.todolist.server.application.service.AuthSettings;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Wires the outbound adapters that need configuration values. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ApplicationConfig {

    /**
     * PostgreSQL stores timestamps with microsecond precision, while the JVM clock has nanoseconds. Ticking in
     * microseconds keeps the value returned to clients identical to the value read back later.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }

    @Bean
    AuthSettings authSettings(AppProperties properties) {
        return new AuthSettings(properties.jwt().refreshTokenTtl());
    }

    @Bean
    PasswordHasher passwordHasher(AppProperties properties) {
        return new BcryptPasswordHasher(properties.password().bcryptCost());
    }

    @Bean
    RefreshTokenGenerator refreshTokenGenerator() {
        return new SecureRefreshTokenGenerator();
    }

    @Bean
    GoogleTokenVerifier googleTokenVerifier(AppProperties properties) {
        return new GoogleApiTokenVerifier(properties.google().clientIds());
    }
}
