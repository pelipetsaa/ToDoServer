package com.pelypets.todolist.server.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pelypets.todolist.server.application.port.out.AccessTokenIssuer;
import com.pelypets.todolist.server.application.port.out.GoogleTokenVerifier;
import com.pelypets.todolist.server.application.port.out.PasswordHasher;
import com.pelypets.todolist.server.application.port.out.RefreshTokenGenerator;
import com.pelypets.todolist.server.application.port.out.RefreshTokenRepository;
import com.pelypets.todolist.server.application.port.out.UserRepository;
import com.pelypets.todolist.server.application.service.AuthService;
import com.pelypets.todolist.server.application.service.AuthSettings;
import com.pelypets.todolist.server.domain.exception.EmailTakenException;
import com.pelypets.todolist.server.domain.exception.UnauthorizedException;
import com.pelypets.todolist.server.domain.model.GoogleIdentity;
import com.pelypets.todolist.server.domain.model.RefreshToken;
import com.pelypets.todolist.server.domain.model.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordHasher passwordHasher;
    @Mock AccessTokenIssuer accessTokenIssuer;
    @Mock RefreshTokenGenerator refreshTokenGenerator;
    @Mock GoogleTokenVerifier googleTokenVerifier;
    @Mock ApplicationEventPublisher events;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, refreshTokens, passwordHasher, accessTokenIssuer, refreshTokenGenerator,
                googleTokenVerifier, events, new AuthSettings(Duration.ofDays(30)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aRefreshTokenUsedConcurrentlyIsRejectedForTheLoser() {
        when(refreshTokenGenerator.hash("token")).thenReturn("hash");
        when(refreshTokens.findByHash("hash"))
                .thenReturn(Optional.of(new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash", NOW.plusSeconds(60))));
        when(refreshTokens.deleteByHash("hash")).thenReturn(false);

        assertThatThrownBy(() -> service.refresh("token")).isInstanceOf(UnauthorizedException.class);
        verify(accessTokenIssuer, never()).issue(any());
    }

    @Test
    void anExpiredRefreshTokenIsRevokedAndRejected() {
        when(refreshTokenGenerator.hash("token")).thenReturn("hash");
        when(refreshTokens.findByHash("hash"))
                .thenReturn(Optional.of(new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash", NOW)));
        when(refreshTokens.deleteByHash("hash")).thenReturn(true);

        assertThatThrownBy(() -> service.refresh("token")).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokens).deleteByHash("hash");
    }

    @Test
    void googleSignInRacingWithItselfReturnsTheUserCreatedByTheWinner() {
        User winner = User.withGoogle("google@example.com", "g-1", NOW);
        when(googleTokenVerifier.verify("id-token")).thenReturn(Optional.of(new GoogleIdentity("g-1", "google@example.com")));
        when(users.findByGoogleSubject("g-1")).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        when(users.findByEmail("google@example.com")).thenReturn(Optional.empty());
        when(users.create(any())).thenThrow(new EmailTakenException());
        when(refreshTokenGenerator.generate()).thenReturn("refresh");
        when(refreshTokenGenerator.hash("refresh")).thenReturn("refresh-hash");
        when(accessTokenIssuer.issue(winner.id())).thenReturn("access");

        assertThat(service.loginWithGoogle("id-token").accessToken()).isEqualTo("access");
    }

    @Test
    void linkingGoogleToAnEmailAccountRevokesItsSessions() {
        User squatter = User.withPassword("victim@example.com", "hash", NOW);
        when(googleTokenVerifier.verify("id-token")).thenReturn(Optional.of(new GoogleIdentity("g-1", "Victim@example.com")));
        when(users.findByGoogleSubject("g-1")).thenReturn(Optional.empty());
        when(users.findByEmail("victim@example.com")).thenReturn(Optional.of(squatter));
        when(refreshTokenGenerator.generate()).thenReturn("refresh");
        when(refreshTokenGenerator.hash("refresh")).thenReturn("refresh-hash");

        service.loginWithGoogle("id-token");

        verify(users).linkGoogleSubject(squatter.id(), "g-1");
        verify(refreshTokens).deleteAllForUser(squatter.id());
    }
}
