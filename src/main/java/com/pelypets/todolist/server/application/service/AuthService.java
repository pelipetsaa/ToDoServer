package com.pelypets.todolist.server.application.service;

import com.pelypets.todolist.server.application.port.in.AuthUseCase;
import com.pelypets.todolist.server.application.port.out.AccessTokenIssuer;
import com.pelypets.todolist.server.application.port.out.GoogleTokenVerifier;
import com.pelypets.todolist.server.application.port.out.PasswordHasher;
import com.pelypets.todolist.server.application.port.out.RefreshTokenGenerator;
import com.pelypets.todolist.server.application.port.out.RefreshTokenRepository;
import com.pelypets.todolist.server.application.port.out.UserRepository;
import com.pelypets.todolist.server.domain.event.UserRegistered;
import com.pelypets.todolist.server.domain.exception.EmailTakenException;
import com.pelypets.todolist.server.domain.exception.UnauthorizedException;
import com.pelypets.todolist.server.domain.model.DomainRules;
import com.pelypets.todolist.server.domain.model.GoogleIdentity;
import com.pelypets.todolist.server.domain.model.RefreshToken;
import com.pelypets.todolist.server.domain.model.TokenPair;
import com.pelypets.todolist.server.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService implements AuthUseCase {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final ApplicationEventPublisher events;
    private final AuthSettings settings;
    private final Clock clock;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordHasher passwordHasher,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenGenerator refreshTokenGenerator,
            GoogleTokenVerifier googleTokenVerifier,
            ApplicationEventPublisher events,
            AuthSettings settings,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.googleTokenVerifier = googleTokenVerifier;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public TokenPair register(String email, String password) {
        String normalized = DomainRules.normalizeEmail(email);
        DomainRules.require(DomainRules.emailError(normalized));
        DomainRules.require(DomainRules.passwordError(password));
        if (users.findByEmail(normalized).isPresent()) throw new EmailTakenException();

        User user = users.create(User.withPassword(normalized, passwordHasher.hash(password), clock.instant()));
        events.publishEvent(new UserRegistered(user.id(), UserRegistered.Method.PASSWORD));
        return issueTokens(user.id());
    }

    @Override
    public TokenPair login(String email, String password) {
        User user = users.findByEmail(DomainRules.normalizeEmail(email)).orElseThrow(AuthService::invalidCredentials);
        String hash = user.password().orElseThrow(AuthService::invalidCredentials);
        if (!passwordHasher.verify(password, hash)) throw invalidCredentials();
        return issueTokens(user.id());
    }

    @Override
    public TokenPair loginWithGoogle(String idToken) {
        GoogleIdentity identity = googleTokenVerifier.verify(idToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid Google token"));
        String email = DomainRules.normalizeEmail(identity.email());

        User user = users.findByGoogleSubject(identity.subject())
                .or(() -> users.findByEmail(email).map(existing -> linkGoogle(existing, identity.subject())))
                .orElseGet(() -> createGoogleUser(email, identity.subject()));
        return issueTokens(user.id());
    }

    /**
     * Email sign-up does not verify the address, so whoever set the password may not own the mailbox. Google has
     * verified it: the password and every session opened with it are revoked, and only Google signs in from now on.
     */
    private User linkGoogle(User user, String subject) {
        if (user.isLinkedToGoogle()) throw new UnauthorizedException("This email is linked to another Google account");
        users.linkGoogleSubject(user.id(), subject);
        refreshTokens.deleteAllForUser(user.id());
        return user;
    }

    private User createGoogleUser(String email, String subject) {
        try {
            User user = users.create(User.withGoogle(email, subject, clock.instant()));
            events.publishEvent(new UserRegistered(user.id(), UserRegistered.Method.GOOGLE));
            return user;
        } catch (EmailTakenException e) {
            // A concurrent sign-in with the same Google account created the user first.
            return users.findByGoogleSubject(subject).orElseThrow(() -> e);
        }
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        String hash = refreshTokenGenerator.hash(refreshToken);
        RefreshToken stored = refreshTokens.findByHash(hash).orElseThrow(AuthService::invalidRefreshToken);
        // Deleting first makes rotation single-use even under concurrent refresh attempts.
        if (!refreshTokens.deleteByHash(hash)) throw invalidRefreshToken();
        if (stored.isExpiredAt(clock.instant())) throw invalidRefreshToken();
        return issueTokens(stored.userId());
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokens.deleteByHash(refreshTokenGenerator.hash(refreshToken));
    }

    @Override
    @Transactional(readOnly = true)
    public User currentUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }

    private TokenPair issueTokens(UUID userId) {
        Instant now = clock.instant();
        // Tokens of sessions that were never refreshed or logged out would otherwise stay in the table forever.
        refreshTokens.deleteExpiredForUser(userId, now);
        String refreshToken = refreshTokenGenerator.generate();
        refreshTokens.save(new RefreshToken(
                UUID.randomUUID(), userId, refreshTokenGenerator.hash(refreshToken), now.plus(settings.refreshTokenTtl())));
        return new TokenPair(accessTokenIssuer.issue(userId), refreshToken, accessTokenIssuer.ttlSeconds());
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Invalid email or password");
    }

    private static UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException("Invalid or expired refresh token");
    }
}
