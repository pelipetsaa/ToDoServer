package com.pelypets.todolist.server.application.service;

import com.pelypets.todolist.server.application.port.in.AuthUseCase;
import com.pelypets.todolist.server.application.port.out.AccessTokenIssuer;
import com.pelypets.todolist.server.application.port.out.GoogleTokenVerifier;
import com.pelypets.todolist.server.application.port.out.PasswordHasher;
import com.pelypets.todolist.server.application.port.out.RefreshTokenGenerator;
import com.pelypets.todolist.server.application.port.out.RefreshTokenRepository;
import com.pelypets.todolist.server.application.port.out.UserRepository;
import com.pelypets.todolist.server.domain.event.RefreshTokenReused;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Transactions are explicit and short: hashing a password (about 250 ms of CPU) and verifying a Google token (possibly
 * an HTTP call) happen before one starts, so a sign-in never holds a database connection while it waits on them.
 */
@Service
public class AuthService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final ApplicationEventPublisher events;
    private final TransactionOperations transactions;
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
            TransactionOperations transactions,
            AuthSettings settings,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.googleTokenVerifier = googleTokenVerifier;
        this.events = events;
        this.transactions = transactions;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public TokenPair register(String email, String password) {
        String normalized = DomainRules.normalizeEmail(email);
        DomainRules.require(DomainRules.emailError(normalized));
        DomainRules.require(DomainRules.passwordError(password));
        if (users.findByEmail(normalized).isPresent()) throw new EmailTakenException();

        String hash = passwordHasher.hash(password);
        return inTransaction(() -> {
            User user = users.create(User.withPassword(normalized, hash, clock.instant()));
            events.publishEvent(new UserRegistered(user.id(), UserRegistered.Method.PASSWORD));
            return issueTokens(user.id(), UUID.randomUUID());
        });
    }

    @Override
    public TokenPair login(String email, String password) {
        Optional<User> user = users.findByEmail(DomainRules.normalizeEmail(email));
        boolean valid = passwordHasher.verify(password, user.flatMap(User::password).orElse(null));
        if (!valid || user.isEmpty()) throw invalidCredentials();
        return inTransaction(() -> issueTokens(user.get().id(), UUID.randomUUID()));
    }

    @Override
    public TokenPair loginWithGoogle(String idToken) {
        GoogleIdentity identity = googleTokenVerifier.verify(idToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid Google token"));
        String email = DomainRules.normalizeEmail(identity.email());

        return inTransaction(() -> {
            User user = users.findByGoogleSubject(identity.subject())
                    .or(() -> users.findByEmail(email).map(existing -> linkGoogle(existing, identity)))
                    .orElseGet(() -> createGoogleUser(email, identity.subject()));
            return issueTokens(user.id(), UUID.randomUUID());
        });
    }

    /**
     * Email sign-up does not verify the address, so whoever set the password may not own the mailbox. When Google manages
     * the mailbox it has proven ownership: the password and every session opened with it are revoked, and only Google
     * signs in from now on. For any other address Google's word is not enough to take the account over.
     */
    private User linkGoogle(User user, GoogleIdentity identity) {
        if (user.isLinkedToGoogle()) throw new UnauthorizedException("This email is linked to another Google account");
        if (!identity.emailAuthoritative()) {
            throw new UnauthorizedException("This email has an account; sign in with its password");
        }
        users.linkGoogleSubject(user.id(), identity.subject());
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

    /** The revocation of a reused token's session must commit, so failures are returned rather than thrown. */
    @Override
    public TokenPair refresh(String refreshToken) {
        String hash = refreshTokenGenerator.hash(refreshToken);
        Optional<TokenPair> pair = inTransaction(() -> rotate(hash));
        return pair.orElseThrow(AuthService::invalidRefreshToken);
    }

    private Optional<TokenPair> rotate(String hash) {
        Optional<RefreshToken> stored = refreshTokens.findByHash(hash);
        if (stored.isEmpty()) {
            refreshTokens.findUsedFamily(hash).ifPresent(this::revokeReusedFamily);
            return Optional.empty();
        }
        RefreshToken token = stored.get();
        // Consuming first makes rotation single-use even under concurrent refresh attempts.
        if (!refreshTokens.consume(hash) || token.isExpiredAt(clock.instant())) return Optional.empty();
        return Optional.of(issueTokens(token.userId(), token.familyId()));
    }

    private void revokeReusedFamily(UUID familyId) {
        log.warn("A rotated refresh token was presented again; revoking its session {}", familyId);
        refreshTokens.deleteFamily(familyId);
        events.publishEvent(new RefreshTokenReused(familyId));
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokens.deleteByHash(refreshTokenGenerator.hash(refreshToken));
    }

    @Override
    public User currentUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }

    @Override
    public void deleteAccount(UUID userId) {
        users.delete(userId);
    }

    private TokenPair issueTokens(UUID userId, UUID familyId) {
        Instant now = clock.instant();
        // Tokens of sessions that were never refreshed or logged out would otherwise stay in the table forever.
        refreshTokens.deleteExpiredForUser(userId, now);
        String refreshToken = refreshTokenGenerator.generate();
        refreshTokens.save(new RefreshToken(UUID.randomUUID(), userId, familyId, refreshTokenGenerator.hash(refreshToken),
                now, now.plus(settings.refreshTokenTtl())));
        // Every sign-in adds a session; the oldest ones beyond the limit are signed out.
        refreshTokens.keepNewestForUser(userId, settings.maxSessionsPerUser());
        return new TokenPair(accessTokenIssuer.issue(userId), refreshToken, accessTokenIssuer.ttlSeconds());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Invalid email or password");
    }

    private static UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException("Invalid or expired refresh token");
    }
}
