package com.pelypets.todolist.server.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.pelypets.todolist.server.domain.event.RefreshTokenReused;
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
import org.springframework.transaction.support.TransactionOperations;

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
                googleTokenVerifier, events, TransactionOperations.withoutTransaction(),
                new AuthSettings(Duration.ofDays(30), 10), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aRefreshTokenUsedConcurrentlyIsRejectedForTheLoser() {
        when(refreshTokenGenerator.hash("token")).thenReturn("hash");
        when(refreshTokens.findByHash("hash")).thenReturn(Optional.of(token(NOW.plusSeconds(60))));
        when(refreshTokens.consume("hash")).thenReturn(false);

        assertThatThrownBy(() -> service.refresh("token")).isInstanceOf(UnauthorizedException.class);
        verify(accessTokenIssuer, never()).issue(any());
    }

    @Test
    void anExpiredRefreshTokenIsConsumedAndRejected() {
        when(refreshTokenGenerator.hash("token")).thenReturn("hash");
        when(refreshTokens.findByHash("hash")).thenReturn(Optional.of(token(NOW)));
        when(refreshTokens.consume("hash")).thenReturn(true);

        assertThatThrownBy(() -> service.refresh("token")).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokens).consume("hash");
        verify(accessTokenIssuer, never()).issue(any());
    }

    @Test
    void aRotatedRefreshTokenPresentedAgainRevokesItsSession() {
        UUID family = UUID.randomUUID();
        when(refreshTokenGenerator.hash("stolen")).thenReturn("hash");
        when(refreshTokens.findByHash("hash")).thenReturn(Optional.empty());
        when(refreshTokens.findUsedFamily("hash")).thenReturn(Optional.of(family));

        assertThatThrownBy(() -> service.refresh("stolen")).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokens).deleteFamily(family);
        verify(events).publishEvent(new RefreshTokenReused(family));
    }

    @Test
    void refreshKeepsTheSessionFamily() {
        RefreshToken stored = token(NOW.plusSeconds(60));
        when(refreshTokenGenerator.hash("token")).thenReturn("hash");
        when(refreshTokens.findByHash("hash")).thenReturn(Optional.of(stored));
        when(refreshTokens.consume("hash")).thenReturn(true);
        when(refreshTokenGenerator.generate()).thenReturn("next");
        when(refreshTokenGenerator.hash("next")).thenReturn("next-hash");

        service.refresh("token");

        verify(refreshTokens).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.familyId().equals(stored.familyId()) && saved.tokenHash().equals("next-hash")));
        verify(refreshTokens).keepNewestForUser(stored.userId(), 10);
    }

    @Test
    void signInWithAnUnknownEmailStillPaysForAPasswordCheck() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody@example.com", "password123")).isInstanceOf(UnauthorizedException.class);
        verify(passwordHasher).verify(anyString(), isNull());
    }

    @Test
    void googleSignInRacingWithItselfReturnsTheUserCreatedByTheWinner() {
        User winner = User.withGoogle("google@example.com", "g-1", NOW);
        when(googleTokenVerifier.verify("id-token"))
                .thenReturn(Optional.of(new GoogleIdentity("g-1", "google@example.com", true)));
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
        when(googleTokenVerifier.verify("id-token"))
                .thenReturn(Optional.of(new GoogleIdentity("g-1", "Victim@example.com", true)));
        when(users.findByGoogleSubject("g-1")).thenReturn(Optional.empty());
        when(users.findByEmail("victim@example.com")).thenReturn(Optional.of(squatter));
        when(refreshTokenGenerator.generate()).thenReturn("refresh");
        when(refreshTokenGenerator.hash("refresh")).thenReturn("refresh-hash");

        service.loginWithGoogle("id-token");

        verify(users).linkGoogleSubject(squatter.id(), "g-1");
        verify(refreshTokens).deleteAllForUser(squatter.id());
    }

    @Test
    void googleCannotTakeOverAnAccountWhoseMailboxItDoesNotManage() {
        User owner = User.withPassword("owner@company.example", "hash", NOW);
        when(googleTokenVerifier.verify("id-token"))
                .thenReturn(Optional.of(new GoogleIdentity("g-1", "owner@company.example", false)));
        when(users.findByGoogleSubject("g-1")).thenReturn(Optional.empty());
        when(users.findByEmail("owner@company.example")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.loginWithGoogle("id-token")).isInstanceOf(UnauthorizedException.class);
        verify(users, never()).linkGoogleSubject(any(), any());
        verify(refreshTokens, never()).deleteAllForUser(any());
    }

    private static RefreshToken token(Instant expiresAt) {
        return new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "hash", NOW.minusSeconds(60), expiresAt);
    }
}
