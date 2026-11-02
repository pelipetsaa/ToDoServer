package com.pelypets.todolist.server.application.port.out;

import com.pelypets.todolist.server.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void save(RefreshToken token);

    Optional<RefreshToken> findByHash(String tokenHash);

    /**
     * Deletes the token and remembers it as used until it would have expired. False when nothing was deleted, e.g. a
     * concurrent refresh used the token first.
     */
    boolean consume(String tokenHash);

    /** The session of a token that was already consumed by a refresh. */
    Optional<UUID> findUsedFamily(String tokenHash);

    void deleteFamily(UUID familyId);

    /** Deletes the token without remembering it, as logout does. */
    boolean deleteByHash(String tokenHash);

    void deleteAllForUser(UUID userId);

    void deleteExpiredForUser(UUID userId, Instant now);

    /** Deletes all but the {@code count} most recently created tokens of the user. */
    void keepNewestForUser(UUID userId, int count);

    /**
     * Deletes expired tokens and expired used-token records; returns how many. Returns 0 without deleting anything when
     * another instance is already purging. Must run inside a transaction.
     */
    int deleteExpired(Instant now);
}
