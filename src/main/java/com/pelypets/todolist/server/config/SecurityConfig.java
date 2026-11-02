package com.pelypets.todolist.server.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pelypets.todolist.server.adapter.in.web.ApiPaths;
import com.pelypets.todolist.server.adapter.in.web.error.ErrorResponseWriter;
import com.pelypets.todolist.server.adapter.in.web.security.JsonAuthenticationEntryPoint;
import com.pelypets.todolist.server.adapter.in.web.security.MetricsEndpointGuard;
import com.pelypets.todolist.server.adapter.out.security.JwtAccessTokenIssuer;
import com.pelypets.todolist.server.application.port.out.AccessTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Stateless JWT resource server. Two filter chains: actuator endpoints (no JWT; Prometheus has its own token) and
 * everything else.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    static final String PROMETHEUS_PATH = "/actuator/prometheus";

    private static final String[] PUBLIC_PATHS = {
        ApiPaths.HEALTH, ApiPaths.HEALTH_READY, "/docs", "/docs/**", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/error"
    };

    @Bean
    @Order(1)
    SecurityFilterChain actuatorChain(HttpSecurity http, AppProperties properties, ErrorResponseWriter writer) throws Exception {
        var metricsGuard = new MetricsEndpointGuard(
                PROMETHEUS_PATH, properties.metrics().bearerToken(), properties.isProduction(), writer);
        return stateless(http)
                .securityMatcher("/actuator/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(metricsGuard, AuthorizationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, AppProperties properties, ErrorResponseWriter writer) throws Exception {
        var entryPoint = new JsonAuthenticationEntryPoint(writer, properties.jwt().realm());
        return stateless(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, ApiPaths.AUTH + "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(ApiPaths.BASE + "/**").authenticated()
                        // Nothing else is mapped, so an unknown path gets a 404 rather than a misleading 401.
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .oauth2ResourceServer(server -> server
                        .bearerTokenResolver(publicPathsIgnoringResolver())
                        .authenticationEntryPoint(entryPoint)
                        .jwt(Customizer.withDefaults()))
                .build();
    }

    private static HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    /**
     * Clients may still attach an expired access token when they call refresh or login; on those endpoints the header
     * is ignored instead of failing the request, which would sign the user out.
     */
    private static BearerTokenResolver publicPathsIgnoringResolver() {
        var delegate = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith(ApiPaths.AUTH + "/") ? null : delegate.resolve(request);
    }

    @Bean
    JwtEncoder jwtEncoder(AppProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(AppProperties properties) {
        AppProperties.Jwt jwt = properties.jwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(jwt.audience()));
        OAuth2TokenValidator<Jwt> subject = new JwtClaimValidator<String>(JwtClaimNames.SUB, SecurityConfig::isUuid);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(jwt.issuer()), audience, subject));
        return decoder;
    }

    @Bean
    AccessTokenIssuer accessTokenIssuer(JwtEncoder encoder, AppProperties properties, Clock clock) {
        AppProperties.Jwt jwt = properties.jwt();
        return new JwtAccessTokenIssuer(encoder, jwt.issuer(), jwt.audience(), jwt.accessTokenTtl(), clock);
    }

    private static SecretKey signingKey(AppProperties properties) {
        return new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static boolean isUuid(String value) {
        if (value == null) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
